package com.freepark.local.lot.dto;

import com.freepark.local.domain.LotType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLotRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = 2, max = 64) String code,
        @NotNull LotType lotType,
        @Size(max = 255) String address,
        @Min(0) Integer totalSpaces,
        Boolean enabled) {
}
