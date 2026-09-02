package com.freepark.driver.api.model;

/**
 * 一体机对外提供的能力面。
 *
 * <p>由驱动工厂声明自己实现哪些能力；平台在编排前可据此判断
 * 某台设备是否支持某项操作（如纯识别相机不支持屏显）。
 */
public enum Capability {

    /** 车牌识别上报 */
    PLATE_RECOGNITION,

    /** 道闸控制：开闸 / 落闸 / 常开 */
    GATE_CONTROL,

    /** LED / LCD 屏显示 */
    DISPLAY,

    /** 语音播报 */
    VOICE
}
