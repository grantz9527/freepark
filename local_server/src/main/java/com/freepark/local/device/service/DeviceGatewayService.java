package com.freepark.local.device.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import com.freepark.local.accessdecision.dto.AccessDecisionRequest;
import com.freepark.local.accessdecision.dto.AccessDecisionView;
import com.freepark.local.accessdecision.dto.AccessDirection;
import com.freepark.local.accessdecision.service.AccessDecisionService;
import com.freepark.local.aiodriver.service.AioDriverService;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.device.dto.DevicePollResponse;
import com.freepark.local.device.protocol.CameraProtocol;
import com.freepark.local.device.protocol.ZhenshiProtocol;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.parkingflow.service.ParkingSessionService;
import com.freepark.local.recognition.service.RecognitionRecordService;

/**
 * 识别设备接入网关核心编排：
 * - poll：设备按 code 轮询 → 更新心跳 → 出队待执行指令 → 按品牌协议返回。
 * - push：设备识别后推送 → 从报文提取 serialno → 定位设备 → 更新心跳 → 解析入库 → 出队 OPEN 指令 → 按品牌协议返回开闸响应。
 * 所有交互方向均为 设备 → 服务器。
 */
@Service
public class DeviceGatewayService {

    private static final Logger log = LoggerFactory.getLogger(DeviceGatewayService.class);

    private final ParkingBarrierRepository barriers;
    private final RecognitionRecordService recognitionRecordService;
    private final DeviceCommandService commandService;
    private final AutoRegisteredDeviceService autoDeviceService;
    private final AccessDecisionService accessDecisions;
    private final AioDriverService aioDrivers;
    private final ParkingSessionService parkingSessions;
    private final ZhenshiProtocol defaultProtocol;
    private final Map<String, CameraProtocol> protocolsByBrand;

    public DeviceGatewayService(
            ParkingBarrierRepository barriers,
            RecognitionRecordService recognitionRecordService,
            DeviceCommandService commandService,
            AutoRegisteredDeviceService autoDeviceService,
            AccessDecisionService accessDecisions,
            AioDriverService aioDrivers,
            ParkingSessionService parkingSessions,
            ZhenshiProtocol defaultProtocol,
            List<CameraProtocol> protocols) {
        this.barriers = barriers;
        this.recognitionRecordService = recognitionRecordService;
        this.commandService = commandService;
        this.autoDeviceService = autoDeviceService;
        this.accessDecisions = accessDecisions;
        this.aioDrivers = aioDrivers;
        this.parkingSessions = parkingSessions;
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
     * 4. 命中预排命令时按其开/关闸意图响应；否则按车场通行判定规则判定放行
     * 5. 放行 → 平台主动 HTTP 下发开闸指令（无可驱动/连接时回退响应带回）
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

        // 1) 先消费预排命令（管理员/规则显式开/关闸优先，沿用原队列语义）
        Optional<DeviceCommand> pending = commandService.dequeueForDevice(device.getId());
        if (pending.isPresent()) {
            boolean open = pending.get().getAction() == DeviceCommand.Action.OPEN;
            log.info("识别 device={} plate={}：命中预排命令 {}，推送了开闸指令={}",
                    device.getCode(), record.getPlate(), pending.get().getAction(), open);
            return protocol.buildPushResponse(open);
        }

        // 2) 无预排命令：按通行判定规则决定放行，放行才向识别一体机下发开闸指令
        return respondOpenOnAllowed(device, record, protocol);
    }

    /**
     * 识别放行判定并开闸：
     * <ol>
     *   <li>设备需绑定通道且通道归属车场、行进方向可判定，否则只记录不开闸；</li>
     *   <li>按车场通行判定规则（白名单/黑名单/模式白名单/内部车辆等）判定放行；</li>
     *   <li>放行：优先平台主动 HTTP 下发开闸指令；档案无驱动或连接地址时
     *       回退为响应带回（本次推送响应 info=ok，由设备自行执行）；</li>
     *   <li>拦截：不开闸，响应 info=no。</li>
     * </ol>
     */
    private JsonNode respondOpenOnAllowed(ParkingBarrier device, RecognitionRecord record, CameraProtocol protocol) {
        String plate = record.getPlate();
        if (plate == null || plate.isBlank()) {
            log.info("识别空车牌不开闸：device={}", device.getCode());
            return protocol.buildPushResponse(false);
        }
        if (device.getLane() == null || device.getLane().getLot() == null) {
            log.info("识别仅记录不开闸：device={} plate={} 未绑定通道/车场", device.getCode(), plate);
            return protocol.buildPushResponse(false);
        }
        AccessDirection direction = toAccessDirection(record.getDirection());
        if (direction == null) {
            log.info("识别仅记录不开闸：device={} plate={} 行进方向不可判定", device.getCode(), plate);
            return protocol.buildPushResponse(false);
        }

        UUID lotId = device.getLane().getLot().getId();
        AccessDecisionView decision = accessDecisions.decide(lotId, new AccessDecisionRequest(
                device.getLane().getId(),
                plate,
                record.getPlateColor(),
                direction,
                null, // 通道未配置拦截色，由调用方具备时提供
                direction == AccessDirection.EXIT ? parkingSessions.hasOpenSession(lotId, plate) : null));
        if (decision.result() == AccessDecisionView.Result.INTERCEPTED) {
            log.info("识别拦截不开闸：device={} plate={} direction={} remark={}",
                    device.getCode(), plate, direction, decision.remark());
            return protocol.buildPushResponse(false);
        }

        // 放行：优先平台主动 HTTP 下发开闸指令
        if (aioDrivers.openGateSystem(device, "recognition:" + device.getCode())) {
            log.info("识别放行 device={} plate={} direction={}：推送了开闸指令（平台主动下发）",
                    device.getCode(), plate, direction);
            // 已主动下发开闸，响应不再重复指示设备开闸
            return protocol.buildPushResponse(false);
        }
        // 档案无驱动或连接地址不可用：回退响应带回，由设备按响应自行开闸
        log.info("识别放行 device={} plate={} direction={}：推送了开闸指令（响应带回）",
                device.getCode(), plate, direction);
        return protocol.buildPushResponse(true);
    }

    /** 对齐流水层的方向解释：IN/ENTRANCE/1 → 入场，OUT/EXIT/2 → 出场，其余不可判定。 */
    private AccessDirection toAccessDirection(String direction) {
        if (direction == null) {
            return null;
        }
        String upper = direction.trim().toUpperCase();
        if ("IN".equals(upper) || "ENTRANCE".equals(upper) || "1".equals(upper)) {
            return AccessDirection.ENTRANCE;
        }
        if ("OUT".equals(upper) || "EXIT".equals(upper) || "2".equals(upper)) {
            return AccessDirection.EXIT;
        }
        return null;
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
