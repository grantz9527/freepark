package com.freepark.driver.api.model;

import java.util.List;

/**
 * 语音播报能力（能力面 {@link Capability#VOICE} 的细化描述）。
 *
 * <p>驱动在工厂上声明自己如何播报：万能设备为 {@link VoiceMode#FREE_TEXT}，
 * 预置设备为 {@link VoiceMode#PRESET_ONLY} 并在 {@link #presets()} 中列出设备端
 * 可播的固定话术（供界面展示与测试播报使用）。
 *
 * @param mode    播报模式
 * @param presets 设备端预置的可播固定话术（PRESET_ONLY/HYBRID 时提供，可空）
 */
public record VoiceCapability(VoiceMode mode, List<String> presets) {

    public VoiceCapability {
        presets = presets == null ? List.of() : List.copyOf(presets);
    }

    /** 万能语音：任意文本可播，无预置话术。 */
    public static VoiceCapability free() {
        return new VoiceCapability(VoiceMode.FREE_TEXT, List.of());
    }

    /** 仅预置语音：只可播给定固定话术。 */
    public static VoiceCapability presetOnly(List<String> presets) {
        return new VoiceCapability(VoiceMode.PRESET_ONLY, presets);
    }

    /** 混合语音：自由文本优先，回退到给定预置话术。 */
    public static VoiceCapability hybrid(List<String> presets) {
        return new VoiceCapability(VoiceMode.HYBRID, presets);
    }
}
