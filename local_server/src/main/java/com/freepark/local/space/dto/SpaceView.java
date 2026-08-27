package com.freepark.local.space.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.ParkingSpace;

public record SpaceView(
        UUID id,
        UUID lotId,
        UUID areaId,
        String areaName,
        String locationName,
        String code,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static SpaceView from(ParkingSpace space) {
        return new SpaceView(
                space.getId(),
                space.getLot().getId(),
                space.getArea().getId(),
                space.getArea().getName(),
                space.getArea().getLocation().getName(),
                space.getCode(),
                space.isEnabled(),
                space.getCreatedAt(),
                space.getUpdatedAt());
    }
}
