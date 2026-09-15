package com.freepark.driver.api.model;

import java.util.Map;

/**
 * 一台物理设备的连接/身份配置。
 *
 * <p>驱动包一律不访问数据库：设备档案（序列号、品牌型号、连接参数）
 * 由平台侧从设备管理读取后，以本配置注入驱动实例。
 * {@code port} 为视频流 RTSP 端口；HTTP 命令通道由驱动自行默认（通常 80）。
 */
public record DeviceConfig(
        String deviceKey,
        String brand,
        String model,
        String host,
        Integer port,
        String username,
        String password,
        Map<String, String> params) {

    public DeviceConfig {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /** 便捷构造：多数设备只需地址与凭据。 */
    public static DeviceConfig of(String deviceKey, String brand, String model,
                                  String host, Integer port) {
        return new DeviceConfig(deviceKey, brand, model, host, port, null, null, Map.of());
    }
}
