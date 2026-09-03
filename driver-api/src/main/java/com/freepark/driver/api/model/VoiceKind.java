package com.freepark.driver.api.model;

/**
 * 语音播报场景（意图的“动作”维度）。
 *
 * <p>与 {@link VehicleType}（面向哪类车辆）组合成完整播报意图，例如：
 * WELCOME_IN + MONTHLY = “入场月租车欢迎”，WELCOME_OUT + TEMPORARY = “出场临停车请缴费”。
 * 万能语音设备由平台按话术模板生成文本，仅预置语音设备由驱动按 (kind, vehicleType)
 * 挑选设备端固定话术。
 */
public enum VoiceKind {
    /** 欢迎入场 */
    WELCOME_IN,
    /** 欢送离场 */
    WELCOME_OUT,
    /** 车牌播报 */
    PLATE,
    /** 收费提示 */
    CHARGE,
    /** 车位已满 */
    FULL,
    /** 自由文本 */
    FREE_TEXT
}
