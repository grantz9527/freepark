package com.freepark.local.device.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.AutoRegisteredDevice;

/** 自动发现设备视图：设备轮询网关时自动登记，供对接页面查看与收录。 */
public record AutoRegisteredDeviceView(
        UUID id,
        String code,
        String name,
        String brand,
        Instant lastPollAt,
        Instant createdAt) {

    public static AutoRegisteredDeviceView from(AutoRegisteredDevice device) {
        return new AutoRegisteredDeviceView(
                device.getId(),
                device.getCode(),
                device.getName(),
                device.getBrand(),
                device.getLastPollAt(),
                device.getCreatedAt());
    }
}
