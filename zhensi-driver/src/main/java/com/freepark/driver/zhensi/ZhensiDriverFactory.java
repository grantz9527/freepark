package com.freepark.driver.zhensi;

import java.util.List;
import java.util.Set;

import com.freepark.driver.api.AIODriverFactory;
import com.freepark.driver.api.ParkingAIODevice;
import com.freepark.driver.api.model.Capability;
import com.freepark.driver.api.model.DeviceConfig;
import com.freepark.driver.zhensi.transport.HttpZhensiCommandTransport;

/**
 * 臻识（Zhenshi DEMO）识别一体机驱动工厂。
 *
 * <p>示范：一个厂商驱动工程 = 一个工厂 + 一个 {@link ParkingAIODevice} 实现。
 * 平台把该工程作为依赖引入后，注册表即可按 ZHENSHI 品牌装配所有臻识设备。
 */
public final class ZhensiDriverFactory implements AIODriverFactory {

    public static final String BRAND = "ZHENSHI";

    @Override
    public String brand() {
        return BRAND;
    }

    @Override
    public String model() {
        return "*"; // 示范：接管臻识整条产品线；实际可按型号细分工厂
    }

    @Override
    public String displayName() {
        return "FreePark（Zhenshi DEMO）一体机驱动";
    }

    @Override
    public Set<Capability> capabilities() {
        // 臻识一体机：识别 + 道闸 + LED + 语音
        return Set.of(Capability.PLATE_RECOGNITION, Capability.GATE_CONTROL,
                Capability.DISPLAY, Capability.VOICE);
    }

    @Override
    public List<String> supportedModels() {
        // DEMO：型号不做细分，整条臻识产品线通配（model="*"）。
        // 真实厂商驱动在此返回可接管的设备型号清单，如 List.of("HTZ-S02", "V500")，
        // 对接页的驱动卡片会逐一列出。
        return List.of();
    }

    @Override
    public ParkingAIODevice create(DeviceConfig config) {
        // 每台物理设备 = 一个独立实例：自带连接配置 + 独立本地状态
        return new ZhensiAIODevice(config, new HttpZhensiCommandTransport(config));
    }
}
