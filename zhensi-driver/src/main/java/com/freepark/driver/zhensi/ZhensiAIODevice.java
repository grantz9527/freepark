package com.freepark.driver.zhensi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import com.freepark.driver.api.ParkingAIODevice;
import com.freepark.driver.api.model.AioDriverException;
import com.freepark.driver.api.model.DeviceConfig;
import com.freepark.driver.api.model.DeviceStatus;
import com.freepark.driver.api.model.DisplayMessage;
import com.freepark.driver.api.model.GateState;
import com.freepark.driver.api.model.RecognitionEvent;
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
    private final ArrayDeque<RecognitionEvent> pendingRecognition = new ArrayDeque<>();

    private volatile GateState gate = GateState.CLOSED;
    private volatile boolean lastCommandOk = true;

    public ZhensiAIODevice(DeviceConfig config, ZhensiCommandTransport transport) {
        this.config = config;
        this.transport = transport;
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

    @Override
    public void show(DisplayMessage message) {
        send(CMD_LED_SHOW, message.text(), message.durationMs());
    }

    // ==================== 语音播报 ====================

    @Override
    public void speak(VoiceMessage voice) {
        send(CMD_VOICE, voice.text(), 0);
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
