package com.freepark.local.aiodriver;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.freepark.driver.api.model.DeviceConfig;
import com.freepark.driver.api.registry.AioDriverRegistry;

/**
 * 平台侧驱动装配冒烟测试：验证依赖式接入闭环
 * （pom 引驱动 jar → ServiceLoader 自动发现工厂 → 注册表按 deviceKey 实例化）。
 */
@SpringBootTest
class AioDriverAssemblyTest {

    @Autowired
    private AioDriverRegistry registry;

    @Test
    void zhensiFactoryAutoDiscoveredFromClasspath() {
        assertTrue(registry.supports("ZHENSHI", "*"),
                "classpath 上的臻识驱动应被 ServiceLoader 自动发现并注册");
        assertTrue(registry.factories().stream()
                .anyMatch(f -> "ZHENSHI".equalsIgnoreCase(f.brand())));
    }

    @Test
    void deviceInstancesAreIsolatedAndCachedPerKey() {
        String suffix = Long.toString(System.nanoTime());
        DeviceConfig first = DeviceConfig.of("EQ-A-" + suffix, "ZHENSHI", "*", "127.0.0.1", 80);
        DeviceConfig second = DeviceConfig.of("EQ-B-" + suffix, "ZHENSHI", "*", "127.0.0.1", 80);

        assertSame(registry.deviceFor(first), registry.deviceFor(first),
                "同一 deviceKey 幂等返回同一实例");
        assertNotSame(registry.deviceFor(first), registry.deviceFor(second),
                "不同 deviceKey 实例互相隔离");

        assertTrue(registry.devices().stream()
                .anyMatch(d -> d.deviceKey().equals(first.deviceKey())));
    }
}
