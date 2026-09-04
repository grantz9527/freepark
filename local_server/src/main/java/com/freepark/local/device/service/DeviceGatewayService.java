package com.freepark.local.device.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.freepark.local.accessdecision.dto.AccessDecisionRequest;
import com.freepark.local.accessdecision.dto.AccessDecisionView;
import com.freepark.local.accessdecision.dto.AccessDirection;
import com.freepark.local.accessdecision.service.AccessDecisionService;
import com.freepark.local.aiodriver.service.AioDriverService;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.device.protocol.CameraProtocol;
import com.freepark.local.device.protocol.ZhenshiProtocol;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.LaneType;
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

    /** 轮询设备档案缓存 TTL：心跳轮询非常频繁，1 分钟内复用档案、避免每次查库。 */
    private static final long POLL_CACHE_TTL_MILLIS = TimeUnit.SECONDS.toMillis(60);

    /** 通行判定「黑名单拦截」remark（AccessDecisionService 命中黑名单规则时返回）。 */
    private static final String REMARK_BLACKLISTED_VEHICLE = "blacklisted_vehicle";
    /**
     * 黑名单拦截固定文案：语音文本按控制板语音库词组（词间 ASCII 逗号分隔）播报，
     * LED 文字分行显示（每行须在控制板单行 32 字节内）。词库已确认：此车为黑名单车辆(#202)、
     * 禁止入场(#242)、无权出场(#152)。
     */
    private static final String BLACKLIST_VOICE_ENTRANCE = "此车为黑名单车辆,禁止入场";
    private static final String BLACKLIST_VOICE_EXIT = "此车为黑名单车辆,无权出场";
    private static final String BLACKLIST_LED_ENTRANCE = "此车为黑名单车辆\n禁止入场";
    private static final String BLACKLIST_LED_EXIT = "此车为黑名单车辆\n无权出场";

    /** 通行判定「内部车场：非内部车入场拦截」remark（AccessDecisionService 命中 INTERNAL 车场入场校验时返回）。 */
    private static final String REMARK_NOT_INTERNAL_VEHICLE = "not_internal_vehicle";

    private final ParkingBarrierRepository barriers;
    private final RecognitionRecordService recognitionRecordService;
    private final DeviceCommandService commandService;
    private final AutoRegisteredDeviceService autoDeviceService;
    private final AccessDecisionService accessDecisions;
    private final AioDriverService aioDrivers;
    private final ParkingSessionService parkingSessions;
    private final ZhenshiProtocol defaultProtocol;
    private final Map<String, CameraProtocol> protocolsByBrand;

    /** 轮询设备缓存（key 为小写 code）。 */
    private final ConcurrentHashMap<String, PollCacheEntry> pollCache = new ConcurrentHashMap<>();

    /** 轮询缓存条目；device 为 null 表示该 code 当前未登记（同样短时缓存，防未登记设备高频打库）。 */
    private static final class PollCacheEntry {
        private final ParkingBarrier device;
        private final long loadedAtMillis;

        PollCacheEntry(ParkingBarrier device) {
            this.device = device;
            this.loadedAtMillis = System.currentTimeMillis();
        }

        boolean expired() {
            return System.currentTimeMillis() - loadedAtMillis >= POLL_CACHE_TTL_MILLIS;
        }
    }

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
    public JsonNode handlePoll(String code) {
        ParkingBarrier device = findCachedDeviceForPoll(code);
        if (device == null) {
            // 未登记的设备：自动注册到「识别一体机对接」，返回心跳确认，等待管理员收录绑定车道
            autoDeviceService.upsertOnPoll(code);
            return heartbeatAck();
        }
        if (!device.isEnabled()) {
            throw new BusinessException(ErrorCode.DEVICE_DISABLED);
        }
        // 缓存复用 detached 实体，心跳用批量 UPDATE 落库（避免 merge 产生额外 SELECT）
        barriers.touchLastPollAt(device.getId(), Instant.now());
        DeviceCommand cmd = commandService.dequeueForDevice(device.getId()).orElse(null);
        CameraProtocol protocol = resolveProtocol(device);
        // 指令（手动）开闸也按车道方向播报欢迎/欢送语：入口“欢迎光临”、出口“一路顺风”
        JsonNode response = protocol.buildPollResponse(cmd, pollVoiceText(device, cmd));
        if (cmd != null && cmd.getAction() == DeviceCommand.Action.SYNC_TIME) {
            // 时间同步不参与开/关闸：按心跳应答后，在响应中附带 0x05 主板时间同步帧
            response = protocol.appendTimeSync(response);
        }
        return response;
    }

    /** 心跳/注册确认应答：臻识官方 demo 对设备注册的标准回复，相机收到即继续下一轮询，不触发任何动作。 */
    private static JsonNode heartbeatAck() {
        ObjectNode ok = JsonNodeFactory.instance.objectNode();
        ok.put("status", "ok");
        return ok;
    }

    /**
     * 轮询设备定位（1 分钟缓存）：命中且未过期直接复用，避免高频心跳反复查库。
     * 缓存失效后回源刷新；未登记设备以空条目短时缓存，同样避免反复查询。
     */
    private ParkingBarrier findCachedDeviceForPoll(String code) {
        String key = code.toLowerCase();
        PollCacheEntry entry = pollCache.get(key);
        if (entry != null && !entry.expired()) {
            return entry.device;
        }
        ParkingBarrier device = barriers.findByCodeIgnoreCase(code).orElse(null);
        pollCache.put(key, new PollCacheEntry(device));
        return device;
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
            DeviceCommand cmd = pending.get();
            if (cmd.getAction() == DeviceCommand.Action.SYNC_TIME) {
                // 时间同步为“附加”指令，不参与识别放行决策：消费后继续常规通行判定，最终响应附带 485 同步时间帧
                log.info("识别 device={} plate={}：消费预排指令 SYNC_TIME，继续常规通行判定并附带时间同步",
                        device.getCode(), record.getPlate());
                return protocol.appendTimeSync(respondOpenOnAllowed(device, record, protocol));
            }
            boolean open = cmd.getAction() == DeviceCommand.Action.OPEN;
            log.info("识别 device={} plate={}：命中预排命令 {}，开闸={}", device.getCode(), record.getPlate(),
                    cmd.getAction(), open);
            // 预排开闸按行进方向附欢迎/欢送语音；CLOSE 由协议层转译为 ivs_ioctrl（落闸）等设备控制报文
            return protocol.buildPushResponse(cmd, welcomeVoice(toAccessDirection(record.getDirection())));
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
            return interceptResponse(protocol, direction, decision.remark(), plate);
        }

        // 放行：优先平台主动 HTTP 下发开闸指令
        String farewell = welcomeVoice(direction);
        // 识别放行语音统一拼车牌后播报（“车牌,欢迎光临”/“车牌,一路顺风”）：控制板语音按词组匹配，
        // 车牌为变量信息可自动识别播报（显示屏通信协议 2.5 播报车牌匹配格式 P，词间 ASCII 逗号分隔）。
        // 入场/离场均下发 LED 两行“车牌 / 欢迎光临”（或“车牌 / 一路顺风”，0x6E 文字+语音一体帧）。
        String normalizedPlate = plate.trim();
        String voiceText = normalizedPlate + "," + farewell;
        String ledText = normalizedPlate + "\n" + farewell;
        if (aioDrivers.openGateSystem(device, "recognition:" + device.getCode())) {
            log.info("识别放行 device={} plate={} direction={}：推送了开闸指令（平台主动下发）",
                    device.getCode(), plate, direction);
            // 已主动下发开闸，响应不再重复指示设备开闸（语音与 LED 提示仍随响应带回）
            return protocol.buildPushResponse(false, voiceText, ledText);
        }
        // 档案无驱动或连接地址不可用：回退响应带回，由设备按响应自行开闸
        log.info("识别放行 device={} plate={} direction={}：推送了开闸指令（响应带回）",
                device.getCode(), plate, direction);
        return protocol.buildPushResponse(true, voiceText, ledText);
    }

    /**
     * 拦截提示响应：不开闸的同时经相机串口向控制板下发「LED 文字 + 语音」一体提示：
     * <ul>
     *   <li>黑名单：固定文案（入口“此车为黑名单车辆,禁止入场”、出口“此车为黑名单车辆,无权出场”，
     *       词条均匹配板卡语音库）；</li>
     *   <li>内部车场非内部车入场：语音“车牌,无权入场”、LED 两行“车牌 / 无权入场”
     *       （“无权入场”为板卡语音库 #153，车牌为变量信息自动识别播报）；</li>
     * </ul>
     * 其它拦截仅返回不开闸。
     */
    private JsonNode interceptResponse(CameraProtocol protocol, AccessDirection direction, String remark, String plate) {
        if (REMARK_BLACKLISTED_VEHICLE.equals(remark)) {
            if (direction == AccessDirection.ENTRANCE) {
                return protocol.buildPushResponse(false, BLACKLIST_VOICE_ENTRANCE, BLACKLIST_LED_ENTRANCE);
            }
            if (direction == AccessDirection.EXIT) {
                return protocol.buildPushResponse(false, BLACKLIST_VOICE_EXIT, BLACKLIST_LED_EXIT);
            }
            return protocol.buildPushResponse(false);
        }
        if (REMARK_NOT_INTERNAL_VEHICLE.equals(remark) && direction == AccessDirection.ENTRANCE) {
            String normalizedPlate = plate.trim();
            return protocol.buildPushResponse(false,
                    normalizedPlate + ",无权入场",
                    normalizedPlate + "\n无权入场");
        }
        return protocol.buildPushResponse(false);
    }

    /** 放行播报语：入场播“欢迎光临”，出场播“一路顺风”；方向不可判定不播。 */
    private String welcomeVoice(AccessDirection direction) {
        if (direction == AccessDirection.ENTRANCE) {
            return "欢迎光临";
        }
        if (direction == AccessDirection.EXIT) {
            return "一路顺风";
        }
        return null;
    }

    /**
     * 指令（手动）开闸的播报语：按设备绑定车道方向推断（入口“欢迎光临”、出口“一路顺风”）。
     * 仅 OPEN 指令播报；双向车道/未绑定车道/其它动作方向不明，不播。
     */
    private String pollVoiceText(ParkingBarrier device, DeviceCommand cmd) {
        if (cmd == null || cmd.getAction() != DeviceCommand.Action.OPEN) {
            return null;
        }
        LaneType laneType = barriers.findBoundLaneType(device.getId()).orElse(null);
        if (laneType == LaneType.ENTRANCE) {
            return "欢迎光临";
        }
        if (laneType == LaneType.EXIT) {
            return "一路顺风";
        }
        return null;
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
