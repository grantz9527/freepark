package com.freepark.driver.zhensi;

import java.util.List;
import java.util.Set;

import com.freepark.driver.api.AIODriverFactory;
import com.freepark.driver.api.ParkingAIODevice;
import com.freepark.driver.api.model.Capability;
import com.freepark.driver.api.model.DeviceConfig;
import com.freepark.driver.api.model.DisplayCapability;
import com.freepark.driver.api.model.VoiceCapability;
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
    public VoiceCapability voiceCapability() {
        // DEMO 采用万能语音：任意文本可播。真实厂商若只有预置语音，应返回
        // VoiceCapability.presetOnly(List.of("欢迎光临", "一路平安", ...)) 并在驱动内
        // 按 (VoiceKind, VehicleType) 把平台播报意图映射到设备端固定话术。
        return VoiceCapability.free();
    }

    @Override
    public DisplayCapability displayCapability() {
        // DEMO 一体机为 2 行静态屏：平台下发的有序建议行只取前 2 行显示。
        // 真实厂商按产品线声明，如 4 行屏 DisplayCapability.fixed(4)，
        // 单行 LED 滚动条 DisplayCapability.scrolling()。
        return DisplayCapability.fixed(2);
    }

    @Override
    public List<String> supportedModels() {
        // 可接管清单供对接页「品牌 → 具体型号/设备类型」选择：
        // - HTZ-S02 / V500：识别一体机型号；
        // - BULE_CARD_LED_LINE2：臻识蓝卡（RS485 蓝色主板）驱动 2 行 LED 屏的设备类型。
        return List.of("HTZ-S02", "V500", "BULE_CARD_LED_LINE2");
    }

    @Override
    public ParkingAIODevice create(DeviceConfig config) {
        // 每台物理设备 = 一个独立实例：自带连接配置 + 独立本地状态
        return new ZhensiAIODevice(config, new HttpZhensiCommandTransport(config));
    }
}
