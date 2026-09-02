package com.freepark.driver.zhensi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.freepark.driver.api.ParkingAIODevice;
import com.freepark.driver.api.model.DeviceConfig;
import com.freepark.driver.api.model.DisplayKind;
import com.freepark.driver.api.model.DisplayMessage;
import com.freepark.driver.api.model.GateState;
import com.freepark.driver.api.model.RecognitionEvent;
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
        return DeviceConfig.of(key, "ZHENSHI", "HTZ-S02", "192.168.1.10", 8080);
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
    void 工厂经注册表按品牌装配() {
        AioDriverRegistry registry = new AioDriverRegistry();
        registry.register(new ZhensiDriverFactory());

        assertTrue(registry.supports("ZHENSHI", "HTZ-S02"));
        assertTrue(registry.supports("zhenshi", "ANY"));

        // 同一工厂可创建多台设备实例；这里仅验证装配不触碰网络
        ParkingAIODevice d1 = registry.deviceFor(cfg("GATE-001"));
        ParkingAIODevice d2 = registry.deviceFor(cfg("GATE-002"));
        assertTrue(d1 != d2);
        assertEquals("GATE-001", d1.deviceKey());
    }
}
