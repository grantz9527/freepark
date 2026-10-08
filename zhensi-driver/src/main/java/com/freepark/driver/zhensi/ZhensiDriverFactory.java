package com.freepark.driver.zhensi;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** 各具体型号的静态屏行数。名称里的 LINE2 / LINE4 与这里的声明保持一致。 */
    private static final Map<String, DisplayCapability> MODEL_DISPLAYS = Map.of(
            "YELLOW_CARD_LED_LINE4", DisplayCapability.fixed(4),
            "PURPLE_CARD_LCD_LINE4", DisplayCapability.fixed(4),
            "BULE_CARD_LED_LINE2", DisplayCapability.fixed(2));

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
        // 未标明型号（产品线通配）时按 2 行静态屏。具体型号见 displayCapability(String)。
        return DisplayCapability.fixed(2);
    }

    @Override
    public DisplayCapability displayCapability(String model) {
        if (model == null || model.isBlank() || "*".equals(model.trim())) {
            return displayCapability();
        }
        DisplayCapability spec = MODEL_DISPLAYS.get(model.trim().toUpperCase(Locale.ROOT));
        return spec != null ? spec : displayCapability();
    }

    @Override
    public List<String> supportedModels() {
        // 可接管清单供对接页「品牌 → 具体型号/设备类型」选择：
        // - YELLOW_CARD_LED_LINE4 / PURPLE_CARD_LCD_LINE4：识别一体机设备类型；
        // - BULE_CARD_LED_LINE2：臻识蓝卡（RS485 蓝色主板）驱动 2 行 LED 屏的设备类型。
        return List.of("YELLOW_CARD_LED_LINE4", "PURPLE_CARD_LCD_LINE4", "BULE_CARD_LED_LINE2");
    }

    @Override
    public ParkingAIODevice create(DeviceConfig config) {
        // 每台物理设备 = 一个独立实例：自带连接配置 + 该型号的屏显行数
        return new ZhensiAIODevice(
                config,
                new HttpZhensiCommandTransport(config),
                voiceCapability(),
                displayCapability(config.model()));
    }
}
