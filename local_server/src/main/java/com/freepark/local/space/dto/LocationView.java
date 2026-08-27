package com.freepark.local.space.dto;

import java.util.UUID;

public record LocationView(UUID id, String name) {

    public static LocationView from(com.freepark.local.domain.ParkingLocation location) {
        return new LocationView(location.getId(), location.getName());
    }
}
