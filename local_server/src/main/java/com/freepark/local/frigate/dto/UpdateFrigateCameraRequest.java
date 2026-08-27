package com.freepark.local.frigate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateFrigateCameraRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String cameraName,
        @NotNull Boolean enabled) {
}
