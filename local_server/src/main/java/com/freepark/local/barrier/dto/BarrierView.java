package com.freepark.local.barrier.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingLane;

public record BarrierView(
        UUID id,
        UUID laneId,
        String laneName,
        String laneCode,
        String name,
        String code,
        String brand,
        String model,
        String host,
        Integer port,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static BarrierView from(ParkingBarrier barrier) {
        ParkingLane lane = barrier.getLane();
        return new BarrierView(
                barrier.getId(),
                lane == null ? null : lane.getId(),
                lane == null ? null : lane.getName(),
                lane == null ? null : lane.getCode(),
                barrier.getName(),
                barrier.getCode(),
                barrier.getBrand(),
                barrier.getModel(),
                barrier.getHost(),
                barrier.getPort(),
                barrier.isEnabled(),
                barrier.getCreatedAt(),
                barrier.getUpdatedAt());
    }
}
