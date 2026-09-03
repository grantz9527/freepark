package com.freepark.driver.api.model;

/**
 * 语音播报面向的车辆对象类型。
 *
 * <p>配合 {@link VoiceKind} 表达完整播报意图（如“入场 + 月租车 → 月租车欢迎光临”）。
 * 万能语音设备据此由平台话术模板生成对应句子；仅预置语音设备由驱动据此挑选
 * 最贴近的固定话术。
 */
public enum VehicleType {

    /** 临时车（临停/需缴费出场） */
    TEMPORARY,

    /** 预约车 */
    RESERVED,

    /** 贵宾车 */
    VIP,

    /** 业主车 */
    OWNER,

    /** 月租车 */
    MONTHLY,

    /** 其他 / 未细分（通用话术） */
    OTHER
}
