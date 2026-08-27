package com.freepark.local.space.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSpaceRequest(
        @NotNull UUID areaId,
        @NotBlank @Size(max = 64) String code,
        Boolean enabled) {
}
