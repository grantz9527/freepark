package com.freepark.local.device.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.DeviceCommand;

public record DeviceCommandView(
        UUID id,
        UUID deviceId,
        String action,
        String status,
        String source,
        Instant createdAt,
        Instant deliveredAt) {

    public static DeviceCommandView from(DeviceCommand cmd) {
        return new DeviceCommandView(
                cmd.getId(),
                cmd.getDeviceId(),
                cmd.getAction().name(),
                cmd.getStatus().name(),
                cmd.getSource(),
                cmd.getCreatedAt(),
                cmd.getDeliveredAt());
    }
}
