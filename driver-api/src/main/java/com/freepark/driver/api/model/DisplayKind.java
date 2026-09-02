package com.freepark.driver.api.model;

/**
 * 屏显内容类型。
 *
 * <p>平台只下发语义内容（类型 + 文本），如何排版、走何种协议
 * （LED 屏 / LCD 屏、网络 / 串口）由各厂商驱动自行实现。
 */
public enum DisplayKind {
    /** 欢迎语（如"欢迎光临"） */
    WELCOME,
    /** 车牌显示 */
    PLATE,
    /** 剩余车位 */
    SLOT,
    /** 自由文本 */
    FREE_TEXT,
    /** 缴费二维码/缴费提示 */
    PAYMENT
}
