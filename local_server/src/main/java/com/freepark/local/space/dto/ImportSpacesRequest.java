package com.freepark.local.space.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ImportSpacesRequest(@NotNull UUID areaId) {
}
