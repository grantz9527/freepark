package com.freepark.driver.api.model;

/** 语音播报内容类型。 */
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
