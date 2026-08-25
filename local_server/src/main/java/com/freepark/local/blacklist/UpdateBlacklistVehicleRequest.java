package com.freepark.local.blacklist;

import java.time.Instant;

import com.freepark.local.domain.PlateColor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateBlacklistVehicleRequest(
        @NotBlank @Size(max = 20) String plateNumber,
        @NotNull PlateColor plateColor,
        @NotBlank @Size(max = 80) String ownerName,
        @Size(max = 32) String phone,
        @Size(max = 80) String department,
        @Size(max = 255) String remark,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        Boolean enabled) {
}
