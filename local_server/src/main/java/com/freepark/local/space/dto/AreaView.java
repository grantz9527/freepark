package com.freepark.local.space.dto;

import java.util.UUID;

public record AreaView(UUID id, UUID locationId, String name) {

    public static AreaView from(com.freepark.local.domain.ParkingArea area) {
        return new AreaView(area.getId(), area.getLocation().getId(), area.getName());
    }
}
