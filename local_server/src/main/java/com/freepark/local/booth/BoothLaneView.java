package com.freepark.local.booth;

import java.util.UUID;

import com.freepark.local.domain.ParkingLane;

public record BoothLaneView(UUID id, String name, String code, String laneType) {

    public static BoothLaneView from(ParkingLane lane) {
        return new BoothLaneView(
                lane.getId(),
                lane.getName(),
                lane.getCode(),
                lane.getLaneType().name());
    }
}
