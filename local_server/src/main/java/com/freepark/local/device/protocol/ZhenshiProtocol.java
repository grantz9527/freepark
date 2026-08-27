package com.freepark.local.device.protocol;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.freepark.local.device.dto.DevicePollResponse;
import com.freepark.local.device.dto.DeviceRecognizeRequest;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.RecognitionRecord;

/**
 * 臻识（ZHENSHI）识别一体机协议适配。
 * 臻识设备通过 HTTP 轮询服务器取指令，识别后上报车牌+图像。
 */
@Component
public class ZhenshiProtocol implements CameraProtocol {

    public static final String BRAND = "ZHENSHI";

    @Override
    public String brand() {
        return BRAND;
    }

    @Override
    public RecognitionRecord parseRecognize(ParkingBarrier device, DeviceRecognizeRequest request) {
        Instant capturedAt = request.capturedAt() != null ? request.capturedAt() : Instant.now();
        String imageRef = request.imageBase64();
        // TODO: 图像落盘后改为存储路径引用，避免在 DB 存大字段。
        return new RecognitionRecord(device, request.plate(), imageRef, request.direction(), capturedAt);
    }

    @Override
    public DevicePollResponse buildPollResponse(DeviceCommand command) {
        if (command == null) {
            return DevicePollResponse.empty();
        }
        return DevicePollResponse.of(command.getAction().name().toLowerCase(), command.getId(), command.getCreatedAt());
    }
}
