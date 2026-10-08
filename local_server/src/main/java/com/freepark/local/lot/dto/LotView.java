package com.freepark.local.lot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.freepark.local.domain.ParkingLot;
import com.freepark.local.lot.support.LotOpenTimeRules;

public record LotView(
        UUID id,
        String name,
        String code,
        String lotType,
        String address,
        int totalSpaces,
        boolean enabled,
        String mapData,
        List<LotOpenTimeRule> openTimeRules,
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
                lot.getMapData(),
                LotOpenTimeRules.parse(lot.getOpenTimeRules()).stream()
                        .map(window -> new LotOpenTimeRule(
                                window.day(),
                                LotOpenTimeRules.formatTime(window.start()),
                                LotOpenTimeRules.formatTime(window.end())))
                        .toList(),
                lot.getCreatedAt(),
                lot.getUpdatedAt());
    }
}
