package com.freepark.driver.api.model;

/**
 * 语音播报消息（结构化播报意图 + 建议文本）。
 *
 * <p>{@code kind} 表达“要播什么场景”，{@code vehicleType} 表达“面向哪类车辆”，
 * 两者组合覆盖 入场/出场 × 临时/预约/贵宾/业主/月租 等全部话术场景；
 * {@code text} 是平台按话术模板生成的建议文本：
 * <ul>
 *   <li>万能语音设备（FREE_TEXT）：直接播出 {@code text}；</li>
 *   <li>仅预置语音设备（PRESET_ONLY）：由驱动按 (kind, vehicleType) 挑选固定话术，
 *       {@code text} 仅供参考（含变量的话术播不完整，须降级为固定句）；</li>
 *   <li>播报失败/无合适话术时驱动应静默降级，不得影响主业务。</li>
 * </ul>
 */
public record VoiceMessage(VoiceKind kind, VehicleType vehicleType, String text) {

    public VoiceMessage {
        kind = kind == null ? VoiceKind.FREE_TEXT : kind;
        vehicleType = vehicleType == null ? VehicleType.OTHER : vehicleType;
    }

    public static VoiceMessage of(VoiceKind kind, VehicleType vehicleType, String text) {
        return new VoiceMessage(kind, vehicleType, text);
    }

    /** 便捷构造：不区分车辆类型（通用话术）。 */
    public static VoiceMessage of(VoiceKind kind, String text) {
        return new VoiceMessage(kind, VehicleType.OTHER, text);
    }
}
