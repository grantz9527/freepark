package com.freepark.local.device.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.device.dto.DevicePollResponse;
import com.freepark.local.device.dto.DeviceRecognizeRequest;
import com.freepark.local.device.protocol.CameraProtocol;
import com.freepark.local.device.protocol.ZhenshiProtocol;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.domain.RecognitionRecordRepository;

/**
 * 识别设备接入网关核心编排：
 * - poll：设备按 code 轮询 → 更新心跳 → 出队待执行指令 → 按品牌协议返回。
 * - recognize：设备识别后上报 → 按品牌协议解析 → 入库。
 * 所有交互方向均为 设备 → 服务器。
 */
@Service
public class DeviceGatewayService {

    private final ParkingBarrierRepository barriers;
    private final RecognitionRecordRepository records;
    private final DeviceCommandService commandService;
    private final ZhenshiProtocol defaultProtocol;
    private final Map<String, CameraProtocol> protocolsByBrand;

    public DeviceGatewayService(
            ParkingBarrierRepository barriers,
            RecognitionRecordRepository records,
            DeviceCommandService commandService,
            ZhenshiProtocol defaultProtocol,
            List<CameraProtocol> protocols) {
        this.barriers = barriers;
        this.records = records;
        this.commandService = commandService;
        this.defaultProtocol = defaultProtocol;
        this.protocolsByBrand = protocols.stream()
                .collect(Collectors.toMap(CameraProtocol::brand, Function.identity(), (a, b) -> a));
    }

    @Transactional
    public DevicePollResponse handlePoll(String code) {
        ParkingBarrier device = requireDevice(code);
        device.markPolled(Instant.now());
        DeviceCommand cmd = commandService.dequeueForDevice(device.getId()).orElse(null);
        return resolveProtocol(device).buildPollResponse(cmd);
    }

    @Transactional
    public UUID handleRecognize(String code, DeviceRecognizeRequest request) {
        ParkingBarrier device = requireDevice(code);
        CameraProtocol protocol = resolveProtocol(device);
        RecognitionRecord record = protocol.parseRecognize(device, request);
        return records.save(record).getId();
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
}
