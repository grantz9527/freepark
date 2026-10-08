package com.freepark.local.whitelist.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.driver.api.model.VehicleType;
import com.freepark.local.domain.CameraWhitelistSyncStatus;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.WhitelistVehicle;

public record WhitelistVehicleView(
        UUID id,
        UUID lotId,
        String plateNumber,
        PlateColor plateColor,
        String ownerName,
        VehicleType type,
        String phone,
        String department,
        String remark,
        Instant startTime,
        Instant endTime,
        boolean enabled,
        /** 臻识机内白名单同步状态（本地，只读）。 */
        CameraWhitelistSyncStatus cameraWhitelistSyncStatus,
        Instant createdAt,
        Instant updatedAt) {

    public static WhitelistVehicleView from(WhitelistVehicle vehicle) {
        return new WhitelistVehicleView(
                vehicle.getId(),
                vehicle.getLot().getId(),
                vehicle.getPlateNumber(),
                vehicle.getPlateColor(),
                vehicle.getOwnerName(),
                vehicle.getType(),
                vehicle.getPhone(),
                vehicle.getDepartment(),
                vehicle.getRemark(),
                vehicle.getStartTime(),
                vehicle.getEndTime(),
                vehicle.isEnabled(),
                vehicle.getCameraWhitelistSyncStatus(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }
}
