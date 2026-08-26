package com.freepark.local.internalvehicle;

import com.freepark.local.domain.InternalVehicleType;
import com.freepark.local.domain.PlateColor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateInternalVehicleRequest(
        @NotBlank @Size(max = 20) String plateNumber,
        @NotNull PlateColor plateColor,
        @NotBlank @Size(max = 80) String ownerName,
        InternalVehicleType type,
        @Size(max = 32) String phone,
        @Size(max = 80) String department,
        @Size(max = 255) String remark,
        Boolean enabled) {
}
