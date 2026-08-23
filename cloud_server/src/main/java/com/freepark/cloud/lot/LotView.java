package com.freepark.cloud.lot;

import java.time.Instant;
import java.util.UUID;

import com.freepark.cloud.domain.ParkingLot;

public record LotView(
        UUID id,
        String name,
        String code,
        String lotType,
        String address,
        int totalSpaces,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static LotView from(ParkingLot lot) {
        return new LotView(
                lot.getId(),
                lot.getName(),
                lot.getCode(),
                lot.getLotType().name(),
                lot.getAddress(),
                lot.getTotalSpaces(),
                lot.isEnabled(),
                lot.getCreatedAt(),
                lot.getUpdatedAt());
    }
}
