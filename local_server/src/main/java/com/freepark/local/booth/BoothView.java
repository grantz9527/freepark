package com.freepark.local.booth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.freepark.local.domain.ParkingBooth;

public record BoothView(
        UUID id,
        UUID lotId,
        String lotName,
        String name,
        String code,
        String location,
        boolean enabled,
        List<BoothLaneView> lanes,
        Instant createdAt,
        Instant updatedAt) {

    public static BoothView from(ParkingBooth booth) {
        return new BoothView(
                booth.getId(),
                booth.getLot().getId(),
                booth.getLot().getName(),
                booth.getName(),
                booth.getCode(),
                booth.getLocation(),
                booth.isEnabled(),
                booth.getLanes().stream().map(BoothLaneView::from).toList(),
                booth.getCreatedAt(),
                booth.getUpdatedAt());
    }
}
