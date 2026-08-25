package com.freepark.local.lane;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.ParkingLane;
import com.freepark.local.domain.ParkingLot;

public record LaneView(
        UUID id,
        UUID lotId,
        String lotName,
        String lotCode,
        UUID linkedLotId,
        String linkedLotName,
        String linkedLotCode,
        String name,
        String code,
        String laneType,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static LaneView from(ParkingLane lane) {
        ParkingLot lot = lane.getLot();
        ParkingLot linkedLot = lane.getLinkedLot();
        return new LaneView(
                lane.getId(),
                lot.getId(),
                lot.getName(),
                lot.getCode(),
                linkedLot == null ? null : linkedLot.getId(),
                linkedLot == null ? null : linkedLot.getName(),
                linkedLot == null ? null : linkedLot.getCode(),
                lane.getName(),
                lane.getCode(),
                lane.getLaneType().name(),
                lane.isEnabled(),
                lane.getCreatedAt(),
                lane.getUpdatedAt());
    }
}
