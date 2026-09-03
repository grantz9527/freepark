package com.freepark.driver.api.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.freepark.driver.api.AIODriverFactory;
import com.freepark.driver.api.ParkingAIODevice;
import com.freepark.driver.api.model.DeviceConfig;
import com.freepark.driver.api.model.GateState;

/**
 * 验证契约核心语义：
 * 1) 一个系统内可同时装配多家厂商驱动；
 * 2) 同品牌同型号的多台物理设备 = 多个互相隔离的绑定实例；
 * 3) brand+model 精确路由，model="*" 通配整条产品线。
 */
class AioDriverRegistryTest {

    /** 内存模拟设备：每次动作都记到自身 actionLog（验证实例隔离）。 */
    static final class SimDevice implements ParkingAIODevice {
        final String key;
        final List<String> actionLog = new ArrayList<>();
        GateState gate = GateState.CLOSED;

        SimDevice(String key) {
            this.key = key;
        }

        @Override public String deviceKey() { return key; }

        @Override public void openGate() { gate = GateState.OPEN; actionLog.add("open"); }

        @Override public void closeGate() { gate = GateState.CLOSED; actionLog.add("close"); }

        @Override public void setAlwaysOpen(boolean on) { actionLog.add("alwaysOpen=" + on); }

        @Override public GateState gateState() { return gate; }

        @Override public void show(com.freepark.driver.api.model.DisplayMessage m) { actionLog.add("show:" + String.join("/", m.lines())); }

        @Override public void speak(com.freepark.driver.api.model.VoiceMessage v) { actionLog.add("speak:" + v.text()); }

        @Override public List<com.freepark.driver.api.model.RecognitionEvent> pullRecognition() { return List.of(); }

        @Override public com.freepark.driver.api.model.DeviceStatus status() {
            return com.freepark.driver.api.model.DeviceStatus.online(gate, "ok");
        }
    }

    /** 厂商 A：臻识整条产品线（model="*"）。 */
    static final class ZhenshiFactory implements AIODriverFactory {
        @Override public String brand() { return "ZHENSHI"; }
        @Override public String model() { return "*"; }
        @Override public ParkingAIODevice create(DeviceConfig c) { return new SimDevice(c.deviceKey()); }
    }

    /** 厂商 B：某控制板厂商，只支持固定型号 BG-200。 */
    static final class BaiguFactory implements AIODriverFactory {
        @Override public String brand() { return "BAIGU"; }
        @Override public String model() { return "BG-200"; }
        @Override public ParkingAIODevice create(DeviceConfig c) { return new SimDevice(c.deviceKey()); }
    }

    @Test
    void 多家厂商可同时装配() {
        AioDriverRegistry registry = new AioDriverRegistry();
        registry.registerAll(List.of(new ZhenshiFactory(), new BaiguFactory()));
        assertEquals(2, registry.factories().size());

        // 同一系统的两条不同车道，一台臻识、一台别家，各自能取到实例
        ParkingAIODevice zhenshi =
                registry.deviceFor(DeviceConfig.of("GATE-001", "ZHENSHI", "HTZ-S02", "192.168.1.10", 8080));
        ParkingAIODevice baigu =
                registry.deviceFor(DeviceConfig.of("GATE-002", "BAIGU", "BG-200", "192.168.1.20", 80));
        assertNotNull(zhenshi);
        assertNotNull(baigu);
    }

    @Test
    void 同型号多台设备实例互相隔离() {
        AioDriverRegistry registry = new AioDriverRegistry();
        registry.register(new ZhenshiFactory());

        ParkingAIODevice a =
                registry.deviceFor(DeviceConfig.of("GATE-001", "zhenshi", "HTZ-S02", "192.168.1.10", 8080));
        ParkingAIODevice b =
                registry.deviceFor(DeviceConfig.of("GATE-002", "ZHENSHI", "HTZ-S02", "192.168.1.11", 8080));

        // 类型写一份，实例是两台
        assertTrue(a != b);

        a.openGate();
        assertEquals(GateState.OPEN, a.gateState());
        assertEquals(GateState.CLOSED, b.gateState());

        // 同 deviceKey 幂等：平台反复取用同一实例
        assertSame(a, registry.deviceFor(
                DeviceConfig.of("GATE-001", "ZHENSHI", "HTZ-S02", "192.168.1.10", 8080)));
    }

    @Test
    void 品牌大小写不敏感且型号通配() {
        AioDriverRegistry registry = new AioDriverRegistry();
        registry.register(new ZhenshiFactory()); // model="*"

        // 任何臻识型号都被接管
        assertTrue(registry.supports("ZHENSHI", "HTZ-S02"));
        assertTrue(registry.supports("zhenshi", "ANY-MODEL"));
    }

    @Test
    void 未注册厂商报错并指明缺口() {
        AioDriverRegistry registry = new AioDriverRegistry();
        registry.register(new BaiguFactory());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> registry.deviceFor(DeviceConfig.of("GATE-9", "OTHER", "X1", "0.0.0.0", 80)));
        assertTrue(e.getMessage().contains("OTHER"));
    }
}
