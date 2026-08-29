package com.freepark.local.device.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingLane;

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
        ParkingLane lane = barrier.getLane();
        return new DeviceStatusView(
                barrier.getId(),
                barrier.getCode(),
                barrier.getName(),
                barrier.getBrand(),
                barrier.isEnabled(),
                online,
                barrier.getLastPollAt(),
                pendingCommands,
                lane == null ? null : lane.getId(),
                lane == null ? null : lane.getName(),
                lane == null ? null : lane.getCode(),
                barrier.getCreatedAt(),
                barrier.getUpdatedAt());
    }
}
