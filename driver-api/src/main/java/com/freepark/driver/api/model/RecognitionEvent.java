package com.freepark.driver.api.model;

/**
 * 识别事件：一体机/相机自身识别到车牌后，由平台轮询拉取。
 *
 * @param plateNo   车牌号（已归一化）
 * @param epochMillis 识别时间戳
 * @param confidence 置信度 0~1
 */
public record RecognitionEvent(String plateNo, long epochMillis, double confidence) {

    public static RecognitionEvent of(String plateNo) {
        return new RecognitionEvent(plateNo, System.currentTimeMillis(), 1.0d);
    }
}
