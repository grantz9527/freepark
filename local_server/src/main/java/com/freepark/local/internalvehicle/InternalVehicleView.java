package com.freepark.local.internalvehicle;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.InternalVehicle;
import com.freepark.local.domain.InternalVehicleType;
import com.freepark.local.domain.PlateColor;

public record InternalVehicleView(
        UUID id,
        UUID lotId,
        String plateNumber,
        PlateColor plateColor,
        String ownerName,
        InternalVehicleType type,
        String phone,
        String department,
        String remark,
        UUID batchId,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static InternalVehicleView from(InternalVehicle vehicle) {
        return new InternalVehicleView(
                vehicle.getId(),
                vehicle.getLot().getId(),
                vehicle.getPlateNumber(),
                vehicle.getPlateColor(),
                vehicle.getOwnerName(),
                vehicle.getType(),
                vehicle.getPhone(),
                vehicle.getDepartment(),
                vehicle.getRemark(),
                vehicle.getBatchId(),
                vehicle.isEnabled(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }
}
