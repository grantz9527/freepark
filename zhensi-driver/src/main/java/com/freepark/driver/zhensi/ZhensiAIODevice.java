package com.freepark.driver.zhensi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.freepark.driver.api.ParkingAIODevice;
import com.freepark.driver.api.model.AioDriverException;
import com.freepark.driver.api.model.DeviceConfig;
import com.freepark.driver.api.model.DeviceStatus;
import com.freepark.driver.api.model.DisplayCapability;
import com.freepark.driver.api.model.DisplayMessage;
import com.freepark.driver.api.model.GateState;
import com.freepark.driver.api.model.RecognitionEvent;
import com.freepark.driver.api.model.VehicleType;
import com.freepark.driver.api.model.VoiceCapability;
import com.freepark.driver.api.model.VoiceMessage;
import com.freepark.driver.zhensi.transport.ZhensiCommand;
import com.freepark.driver.zhensi.transport.ZhensiCommandTransport;

/**
 * 臻识一体机驱动实例（一台物理设备一个实例）。
 *
 * <p>职责：把平台统一规范方法翻译成臻识命令（见 {@link ZhensiCommand} 动作名），
 * 经 {@link ZhensiCommandTransport} 送到设备，并维护本实例的闸态/识别队列。
 * 实例之间状态互不干扰（同型号多台设备各自 new 一个本实例）。
 */
public final class ZhensiAIODevice implements ParkingAIODevice {

    /** 臻识命令动作名（驱动私有；真实报文结构由 transport/厂商文档决定）。 */
    static final String CMD_OPEN = "OPEN_GATE";
    static final String CMD_CLOSE = "CLOSE_GATE";
    static final String CMD_ALWAYS_OPEN = "ALWAYS_OPEN";
    static final String CMD_ALWAYS_OPEN_OFF = "CANCEL_ALWAYS_OPEN";
    static final String CMD_LED_SHOW = "LED_SHOW";
    static final String CMD_VOICE = "VOICE";

    private final DeviceConfig config;
    private final ZhensiCommandTransport transport;
    private final VoiceCapability voiceCapability;
    private final DisplayCapability displayCapability;
    private final ArrayDeque<RecognitionEvent> pendingRecognition = new ArrayDeque<>();

    private volatile GateState gate = GateState.CLOSED;
    private volatile boolean lastCommandOk = true;

    public ZhensiAIODevice(DeviceConfig config, ZhensiCommandTransport transport) {
        this(config, transport, VoiceCapability.free(), DisplayCapability.fixed(2));
    }

    /**
     * 演示用构造：可注入不同语音能力以示范万能/预置/混合三种播报降级；
     * 真实驱动按产品线固定其能力，不提供该重载。
     */
    ZhensiAIODevice(DeviceConfig config, ZhensiCommandTransport transport, VoiceCapability voiceCapability) {
        this(config, transport, voiceCapability, DisplayCapability.fixed(2));
    }

    /**
     * 演示用构造：可额外注入屏幕规格以示范 2 行 / 4 行 / 滚动条对建议行的取舍；
     * 真实驱动按产品线固定其屏幕，不提供该重载。
     */
    ZhensiAIODevice(DeviceConfig config, ZhensiCommandTransport transport,
                    VoiceCapability voiceCapability, DisplayCapability displayCapability) {
        this.config = config;
        this.transport = transport;
        this.voiceCapability = voiceCapability == null ? VoiceCapability.free() : voiceCapability;
        this.displayCapability = displayCapability == null ? DisplayCapability.fixed(2) : displayCapability;
    }

    @Override
    public String deviceKey() {
        return config.deviceKey();
    }

    // ==================== 道闸控制 ====================

    @Override
    public void openGate() {
        send(CMD_OPEN);
        gate = GateState.OPEN;
    }

    @Override
    public void closeGate() {
        send(CMD_CLOSE);
        gate = GateState.CLOSED;
    }

    @Override
    public void setAlwaysOpen(boolean alwaysOpen) {
        send(alwaysOpen ? CMD_ALWAYS_OPEN : CMD_ALWAYS_OPEN_OFF);
        gate = alwaysOpen ? GateState.ALWAYS_OPEN : GateState.CLOSED;
    }

    @Override
    public GateState gateState() {
        return gate;
    }

    // ==================== LED / LCD 屏显示 ====================

    /**
     * 演示“建议行 → 屏幕排版”的取舍：按本设备屏幕规格决定显示哪些行。
     * 静态多行屏取前 rows 行；可滚动屏（如单行滚动条）把全部行滚动播出。
     */
    @Override
    public void show(DisplayMessage message) {
        String text = resolveDisplayText(message);
        if (text == null) {
            // 无可显示内容：静默跳过
            return;
        }
        send(CMD_LED_SHOW, text, message.durationMs());
    }

    /** 按本设备屏幕规格把有序建议行排版为实际播出文本；无内容时返回 null。 */
    String resolveDisplayText(DisplayMessage message) {
        List<String> lines = message.lines();
        if (lines.isEmpty()) {
            return null;
        }
        List<String> visible = displayCapability.scrollable()
                ? lines
                : lines.subList(0, Math.min(displayCapability.rows(), lines.size()));
        // DEMO 以 " / " 分隔行模拟逐行语义；真实协议按屏体行字段/坐标下发
        return String.join(" / ", visible);
    }

    // ==================== 语音播报 ====================

    /**
     * 演示“播报意图 → 设备话术”的翻译与降级：
     * 万能语音播建议文本；仅预置语音按 (kind, vehicleType) 挑选设备端固定话术，
     * 无合适话术则静默放弃（不抛错、不阻断业务）。
     */
    @Override
    public void speak(VoiceMessage voice) {
        String text = resolveVoiceText(voice);
        if (text == null) {
            // 预置设备无可播固定句（如含车牌变量的句子）：静默降级
            return;
        }
        send(CMD_VOICE, text, 0);
    }

    /** 按本设备语音能力把“意图 + 建议文本”翻译成实际播出内容；无可播内容时返回 null。 */
    String resolveVoiceText(VoiceMessage voice) {
        return switch (voiceCapability.mode()) {
            // 万能语音：直接播平台按话术模板生成的建议文本
            case FREE_TEXT -> voice.text();
            // 混合语音：建议文本恰好是预置句时照播，否则回退设备固定话术
            case HYBRID -> voiceCapability.presets().contains(voice.text()) ? voice.text() : presetFor(voice);
            // 仅预置语音：只能播设备端固定话术，建议文本仅供参考
            case PRESET_ONLY -> presetFor(voice);
        };
    }

    /**
     * 演示用“设备端预置话术表”：键为 kind 或 kind:vehicleType。
     * 真实设备/厂商协议各异（编号、索引或固定句），此处仅示范翻译规则。
     */
    private static final Map<String, String> DEMO_PRESETS = Map.ofEntries(
            Map.entry("WELCOME_IN", "欢迎光临"),
            Map.entry("WELCOME_IN:MONTHLY", "月租车，欢迎光临"),
            Map.entry("WELCOME_IN:OWNER", "业主车辆，欢迎回家"),
            Map.entry("WELCOME_IN:VIP", "贵宾车辆，欢迎光临"),
            Map.entry("WELCOME_IN:TEMPORARY", "临时车辆，请扫码入场"),
            Map.entry("WELCOME_IN:RESERVED", "预约车辆，欢迎入场"),
            Map.entry("WELCOME_OUT", "一路平安"),
            Map.entry("WELCOME_OUT:MONTHLY", "月租车，一路平安"),
            Map.entry("WELCOME_OUT:OWNER", "业主车辆，一路平安"),
            Map.entry("WELCOME_OUT:VIP", "贵宾车辆，一路平安"),
            Map.entry("WELCOME_OUT:TEMPORARY", "请缴费后离场"),
            Map.entry("WELCOME_OUT:RESERVED", "预约车辆，一路平安"),
            Map.entry("CHARGE", "请扫描付款码缴费"),
            Map.entry("FULL", "车位已满，请稍候"));

    /** 按 (kind, vehicleType) 挑预置句：先精确匹配，再回退 kind 通用句；仍无则放弃。 */
    private String presetFor(VoiceMessage voice) {
        if (voice.vehicleType() != VehicleType.OTHER) {
            String byType = DEMO_PRESETS.get(voice.kind().name() + ":" + voice.vehicleType().name());
            if (byType != null) {
                return byType;
            }
        }
        return DEMO_PRESETS.get(voice.kind().name());
    }

    // ==================== 识别上报 ====================

    @Override
    public List<RecognitionEvent> pullRecognition() {
        // 平台轮询：取走并清空本实例识别队列（幂等消费）
        synchronized (pendingRecognition) {
            if (pendingRecognition.isEmpty()) {
                return List.of();
            }
            List<RecognitionEvent> events = new ArrayList<>(pendingRecognition);
            pendingRecognition.clear();
            return events;
        }
    }

    /**
     * 入站识别事件入口（平台 HTTP 网关收到臻识推送后调用）。
     *
     * <p>对应既有平台的臻识推送模型：相机识别到车牌 POST 给平台，
     * 平台按 serialno 匹配 deviceKey 后把事件喂给对应实例。
     */
    public void ingest(RecognitionEvent event) {
        synchronized (pendingRecognition) {
            pendingRecognition.addLast(event);
        }
    }

    // ==================== 状态 ====================

    @Override
    public DeviceStatus status() {
        boolean online = lastCommandOk && transport.ping();
        return online
                ? DeviceStatus.online(gate, "臻识一体机在线")
                : DeviceStatus.offline("命令通道异常");
    }

    private void send(String action) {
        send(action, null, 0);
    }

    private void send(String action, String text, long durationMs) {
        try {
            transport.send(ZhensiCommand.of(action, text, durationMs));
            lastCommandOk = true;
        } catch (AioDriverException e) {
            lastCommandOk = false;
            throw e;
        }
    }
}
