package com.freepark.local.device.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;

/**
 * 识别设备上报请求：识别到车牌后由设备 POST 到服务器。
 * imageBase64 为抓拍图；capturedAt 为设备抓拍时间。
 */
public record DeviceRecognizeRequest(
        @NotBlank String plate,
        String imageBase64,
        String direction,
        Instant capturedAt) {
}
