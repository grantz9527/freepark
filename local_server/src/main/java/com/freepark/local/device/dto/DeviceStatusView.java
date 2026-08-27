package com.freepark.local.device.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.ParkingBarrier;

/**
 * 设备状态视图：包含由 lastPollAt 推导出的 online 在线标志。
 */
public record DeviceStatusView(
        UUID id,
        String code,
        String name,
        String brand,
        boolean enabled,
        boolean online,
        Instant lastPollAt,
        long pendingCommands,
        UUID laneId,
        String laneName,
        String laneCode,
        Instant createdAt,
        Instant updatedAt) {

    public static DeviceStatusView from(ParkingBarrier barrier, boolean online, long pendingCommands) {
        return new DeviceStatusView(
                barrier.getId(),
                barrier.getCode(),
                barrier.getName(),
                barrier.getBrand(),
                barrier.isEnabled(),
                online,
                barrier.getLastPollAt(),
                pendingCommands,
                barrier.getLane().getId(),
                barrier.getLane().getName(),
                barrier.getLane().getCode(),
                barrier.getCreatedAt(),
                barrier.getUpdatedAt());
    }
}
