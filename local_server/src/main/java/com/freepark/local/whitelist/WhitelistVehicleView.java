package com.freepark.local.whitelist;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.WhitelistVehicle;

public record WhitelistVehicleView(
        UUID id,
        UUID lotId,
        String plateNumber,
        PlateColor plateColor,
        String ownerName,
        String phone,
        String department,
        String remark,
        Instant startTime,
        Instant endTime,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static WhitelistVehicleView from(WhitelistVehicle vehicle) {
        return new WhitelistVehicleView(
                vehicle.getId(),
                vehicle.getLot().getId(),
                vehicle.getPlateNumber(),
                vehicle.getPlateColor(),
                vehicle.getOwnerName(),
                vehicle.getPhone(),
                vehicle.getDepartment(),
                vehicle.getRemark(),
                vehicle.getStartTime(),
                vehicle.getEndTime(),
                vehicle.isEnabled(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }
}
