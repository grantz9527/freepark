package com.freepark.local.device.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.device.dto.DevicePollResponse;
import com.freepark.local.device.protocol.CameraProtocol;
import com.freepark.local.device.protocol.ZhenshiProtocol;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.recognition.service.RecognitionRecordService;

/**
 * 识别设备接入网关核心编排：
 * - poll：设备按 code 轮询 → 更新心跳 → 出队待执行指令 → 按品牌协议返回。
 * - push：设备识别后推送 → 从报文提取 serialno → 定位设备 → 更新心跳 → 解析入库 → 出队 OPEN 指令 → 按品牌协议返回开闸响应。
 * 所有交互方向均为 设备 → 服务器。
 */
@Service
public class DeviceGatewayService {

    private final ParkingBarrierRepository barriers;
    private final RecognitionRecordService recognitionRecordService;
    private final DeviceCommandService commandService;
    private final AutoRegisteredDeviceService autoDeviceService;
    private final ZhenshiProtocol defaultProtocol;
    private final Map<String, CameraProtocol> protocolsByBrand;

    public DeviceGatewayService(
            ParkingBarrierRepository barriers,
            RecognitionRecordService recognitionRecordService,
            DeviceCommandService commandService,
            AutoRegisteredDeviceService autoDeviceService,
            ZhenshiProtocol defaultProtocol,
            List<CameraProtocol> protocols) {
        this.barriers = barriers;
        this.recognitionRecordService = recognitionRecordService;
        this.commandService = commandService;
        this.autoDeviceService = autoDeviceService;
        this.defaultProtocol = defaultProtocol;
        this.protocolsByBrand = protocols.stream()
                .collect(Collectors.toMap(CameraProtocol::brand, Function.identity(), (a, b) -> a));
    }

    @Transactional
    public DevicePollResponse handlePoll(String code) {
        ParkingBarrier device = barriers.findByCodeIgnoreCase(code).orElse(null);
        if (device == null) {
            // 未登记的设备：自动注册到「识别一体机对接」，并返回空轮询响应
            autoDeviceService.upsertOnPoll(code);
            return DevicePollResponse.empty();
        }
        if (!device.isEnabled()) {
            throw new BusinessException(ErrorCode.DEVICE_DISABLED);
        }
        device.markPolled(Instant.now());
        DeviceCommand cmd = commandService.dequeueForDevice(device.getId()).orElse(null);
        return resolveProtocol(device).buildPollResponse(cmd);
    }

    /**
     * 处理设备识别结果推送（如臻识500的 AlarmInfoPlate）。
     * 1. 按品牌路由协议
     * 2. 从推送报文提取设备序列号并定位设备
     * 3. 刷新心跳、解析识别结果并入库
     * 4. 出队待执行 OPEN 指令，决定是否开闸
     * 5. 返回品牌协议定义的开闸/不开闸响应
     */
    @Transactional
    public JsonNode handlePush(String brand, JsonNode pushData) {
        CameraProtocol protocol = resolveProtocolByBrand(brand);
        String deviceCode = protocol.extractDeviceId(pushData);
        ParkingBarrier device = barriers.findByCodeIgnoreCase(deviceCode).orElse(null);
        if (device == null) {
            // 未登记的设备：自动注册到「识别一体机对接」，返回不开闸，等待管理员收录并绑定车道
            autoDeviceService.upsertOnPoll(deviceCode);
            return protocol.buildPushResponse(false);
        }
        if (!device.isEnabled()) {
            throw new BusinessException(ErrorCode.DEVICE_DISABLED);
        }
        device.markPolled(Instant.now());

        RecognitionRecord record = protocol.parsePush(device, pushData);
        recognitionRecordService.saveDeviceRecord(record);

        Optional<DeviceCommand> pending = commandService.dequeueForDevice(device.getId());
        boolean openGate = pending.map(cmd -> cmd.getAction() == DeviceCommand.Action.OPEN).orElse(false);
        return protocol.buildPushResponse(openGate);
    }

    private ParkingBarrier requireDevice(String code) {
        ParkingBarrier device = barriers.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
        if (!device.isEnabled()) {
            throw new BusinessException(ErrorCode.DEVICE_DISABLED);
        }
        return device;
    }

    private CameraProtocol resolveProtocol(ParkingBarrier device) {
        String brand = device.getBrand();
        if (brand == null || brand.isBlank()) {
            return defaultProtocol;
        }
        return protocolsByBrand.getOrDefault(brand, defaultProtocol);
    }

    private CameraProtocol resolveProtocolByBrand(String brand) {
        if (brand == null || brand.isBlank()) {
            return defaultProtocol;
        }
        return protocolsByBrand.getOrDefault(brand.toUpperCase(), defaultProtocol);
    }
}
