package com.freepark.local.barrier.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.ParkingBarrier;

public record BarrierView(
        UUID id,
        UUID laneId,
        String laneName,
        String laneCode,
        String name,
        String code,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static BarrierView from(ParkingBarrier barrier) {
        return new BarrierView(
                barrier.getId(),
                barrier.getLane().getId(),
                barrier.getLane().getName(),
                barrier.getLane().getCode(),
                barrier.getName(),
                barrier.getCode(),
                barrier.isEnabled(),
                barrier.getCreatedAt(),
                barrier.getUpdatedAt());
    }
}
