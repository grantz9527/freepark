package com.freepark.local.space.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAreaRequest(@NotNull UUID locationId, @NotBlank @Size(max = 80) String name) {
}
