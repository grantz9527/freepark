package com.freepark.driver.zhensi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.freepark.driver.api.ParkingAIODevice;
import com.freepark.driver.api.model.DeviceConfig;
import com.freepark.driver.api.model.DisplayCapability;
import com.freepark.driver.api.model.DisplayKind;
import com.freepark.driver.api.model.DisplayMessage;
import com.freepark.driver.api.model.GateState;
import com.freepark.driver.api.model.RecognitionEvent;
import com.freepark.driver.api.model.VehicleType;
import com.freepark.driver.api.model.VoiceCapability;
import com.freepark.driver.api.model.VoiceKind;
import com.freepark.driver.api.model.VoiceMessage;
import com.freepark.driver.api.registry.AioDriverRegistry;
import com.freepark.driver.zhensi.transport.ZhensiCommand;
import com.freepark.driver.zhensi.transport.ZhensiCommandTransport;

/** 记录型命令通道：单测不触碰网络。 */
final class RecordingTransport implements ZhensiCommandTransport {
    final List<ZhensiCommand> sent = new ArrayList<>();
    boolean online = true;

    @Override public void send(ZhensiCommand command) { sent.add(command); }
    @Override public boolean ping() { return online; }
}

class ZhensiAIODeviceTest {

    private static DeviceConfig cfg(String key) {
        return DeviceConfig.of(key, "ZHENSHI", "YELLOW_CARD_LED_LINE4", "192.168.1.10", 8080);
    }

    @Test
    void 统一规范命令被翻译为臻识命令() {
        RecordingTransport t = new RecordingTransport();
        ZhensiAIODevice device = new ZhensiAIODevice(cfg("GATE-001"), t);

        device.openGate();
        assertEquals(GateState.OPEN, device.gateState());
        device.setAlwaysOpen(true);
        assertEquals(GateState.ALWAYS_OPEN, device.gateState());
        device.show(DisplayMessage.of(DisplayKind.PLATE, "苏A12345"));
        device.speak(VoiceMessage.of(VoiceKind.WELCOME_IN, "欢迎光临"));
        device.closeGate();

        List<String> actions = t.sent.stream().map(ZhensiCommand::action).toList();
        assertEquals(List.of("OPEN_GATE", "ALWAYS_OPEN", "LED_SHOW", "VOICE", "CLOSE_GATE"), actions);
        // 屏显/语音把语义文本原样带下去（协议编码留给 transport 层）
        assertEquals("苏A12345", t.sent.get(2).text());
        assertEquals("欢迎光临", t.sent.get(3).text());
    }

    @Test
    void 识别事件入站后可被平台轮询取走() {
        RecordingTransport t = new RecordingTransport();
        ZhensiAIODevice device = new ZhensiAIODevice(cfg("GATE-001"), t);

        device.ingest(RecognitionEvent.of("苏A12345"));
        device.ingest(RecognitionEvent.of("京B88888"));

        List<RecognitionEvent> pulled = device.pullRecognition();
        assertEquals(2, pulled.size());
        assertEquals("苏A12345", pulled.get(0).plateNo());
        // 拉取即消费
        assertTrue(device.pullRecognition().isEmpty());
    }

    @Test
    void 多台设备实例互相隔离() {
        RecordingTransport ta = new RecordingTransport();
        RecordingTransport tb = new RecordingTransport();
        ZhensiAIODevice a = new ZhensiAIODevice(cfg("GATE-001"), ta);
        ZhensiAIODevice b = new ZhensiAIODevice(cfg("GATE-002"), tb);

        a.openGate();
        a.ingest(RecognitionEvent.of("苏A12345"));

        // B 台不受 A 台命令影响，队列互不共享
        assertEquals(GateState.CLOSED, b.gateState());
        assertTrue(b.pullRecognition().isEmpty());
        assertEquals(1, a.pullRecognition().size());
    }

    @Test
    void 万能语音直接播平台建议文本() {
        RecordingTransport t = new RecordingTransport();
        // 默认（FREE_TEXT）与显式 FREE_TEXT 等价
        ZhensiAIODevice device = new ZhensiAIODevice(cfg("GATE-001"), t, VoiceCapability.free());

        device.speak(VoiceMessage.of(VoiceKind.WELCOME_IN, VehicleType.MONTHLY, "月租车，欢迎光临 苏A12345"));

        assertEquals(1, t.sent.size());
        assertEquals("VOICE", t.sent.get(0).action());
        assertEquals("月租车，欢迎光临 苏A12345", t.sent.get(0).text());
    }

    @Test
    void 仅预置语音按意图挑固定话术() {
        RecordingTransport t = new RecordingTransport();
        ZhensiAIODevice device = new ZhensiAIODevice(cfg("GATE-001"), t,
                VoiceCapability.presetOnly(List.of("月租车，欢迎光临", "贵宾车辆，欢迎光临")));

        // 平台给的建议文本仅供参考，实际播设备端固定话术
        device.speak(VoiceMessage.of(VoiceKind.WELCOME_IN, VehicleType.MONTHLY, "月租车月租车月租车"));
        device.speak(VoiceMessage.of(VoiceKind.WELCOME_IN, VehicleType.VIP, "任意文本"));
        // 无具体类型的车型：回退 kind 通用句
        device.speak(VoiceMessage.of(VoiceKind.WELCOME_OUT, VehicleType.OTHER, "建议文本"));

        List<String> texts = t.sent.stream().map(ZhensiCommand::text).toList();
        assertEquals(List.of("月租车，欢迎光临", "贵宾车辆，欢迎光临", "一路平安"), texts);
    }

    @Test
    void 仅预置语音无可播固定句时静默降级() {
        RecordingTransport t = new RecordingTransport();
        ZhensiAIODevice device = new ZhensiAIODevice(cfg("GATE-001"), t, VoiceCapability.presetOnly(List.of()));

        // 车牌号属于变量话术，预置表无对应固定句：不发命令、不抛错
        device.speak(VoiceMessage.of(VoiceKind.PLATE, VehicleType.OWNER, "苏A12345 请入场"));

        assertTrue(t.sent.isEmpty());
    }

    @Test
    void 混合语音命中预置则照播否则回退固定话术() {
        RecordingTransport t = new RecordingTransport();
        ZhensiAIODevice device = new ZhensiAIODevice(cfg("GATE-001"), t,
                VoiceCapability.hybrid(List.of("欢迎光临")));

        // 建议文本恰是预置句：照播
        device.speak(VoiceMessage.of(VoiceKind.WELCOME_IN, VehicleType.OTHER, "欢迎光临"));
        // 建议文本不在预置集合：回退 (kind,type)→kind 固定话术
        device.speak(VoiceMessage.of(VoiceKind.WELCOME_IN, VehicleType.TEMPORARY, "临时车辆欢迎光临"));

        List<String> texts = t.sent.stream().map(ZhensiCommand::text).toList();
        assertEquals(List.of("欢迎光临", "临时车辆，请扫码入场"), texts);
    }

    @Test
    void 两行屏仅显示前两行建议() {
        RecordingTransport t = new RecordingTransport();
        // 工厂默认 2 行静态屏（与 displayCapability() 声明一致）
        ZhensiAIODevice device = new ZhensiAIODevice(cfg("GATE-001"), t);

        device.show(DisplayMessage.of(DisplayKind.PLATE,
                List.of("苏A12345", "欢迎光临", "临时车辆", "请扫码入场"), 0));

        assertEquals(1, t.sent.size());
        assertEquals("LED_SHOW", t.sent.get(0).action());
        // 只取前 2 行，行序不变
        assertEquals("苏A12345 / 欢迎光临", t.sent.get(0).text());
    }

    @Test
    void 四行屏完整显示全部建议行() {
        RecordingTransport t = new RecordingTransport();
        ZhensiAIODevice device = new ZhensiAIODevice(cfg("GATE-001"), t,
                VoiceCapability.free(), DisplayCapability.fixed(4));

        device.show(DisplayMessage.of(DisplayKind.PLATE,
                List.of("苏A12345", "欢迎光临", "临时车辆", "请扫码入场"), 0));

        assertEquals("苏A12345 / 欢迎光临 / 临时车辆 / 请扫码入场", t.sent.get(0).text());
    }

    @Test
    void 单行滚动屏拼接全部建议行滚动播出() {
        RecordingTransport t = new RecordingTransport();
        ZhensiAIODevice device = new ZhensiAIODevice(cfg("GATE-001"), t,
                VoiceCapability.free(), DisplayCapability.scrolling());

        device.show(DisplayMessage.of(DisplayKind.PAYMENT,
                List.of("请扫码缴费", "支持微信支付宝"), 5000));

        // 可滚动屏不受行数限制：全部行滚动播出，停留时长透传
        assertEquals("请扫码缴费 / 支持微信支付宝", t.sent.get(0).text());
        assertEquals(5000L, t.sent.get(0).durationMs());
    }

    @Test
    void 空建议行静默跳过不发命令() {
        RecordingTransport t = new RecordingTransport();
        ZhensiAIODevice device = new ZhensiAIODevice(cfg("GATE-001"), t);

        device.show(DisplayMessage.of(DisplayKind.WELCOME, List.of(), 0));

        assertTrue(t.sent.isEmpty());
    }

    @Test
    void 工厂经注册表按品牌装配() {
        AioDriverRegistry registry = new AioDriverRegistry();
        registry.register(new ZhensiDriverFactory());

        assertTrue(registry.supports("ZHENSHI", "YELLOW_CARD_LED_LINE4"));
        assertTrue(registry.supports("zhenshi", "ANY"));

        // 同一工厂可创建多台设备实例；这里仅验证装配不触碰网络
        ParkingAIODevice d1 = registry.deviceFor(cfg("GATE-001"));
        ParkingAIODevice d2 = registry.deviceFor(cfg("GATE-002"));
        assertTrue(d1 != d2);
        assertEquals("GATE-001", d1.deviceKey());
    }
}
