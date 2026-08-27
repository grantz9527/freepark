package com.freepark.local.frigate.dto;

import java.util.UUID;

import com.freepark.local.domain.FrigateBindDirection;

import jakarta.validation.constraints.NotNull;

public record BindFrigateCameraRequest(
        @NotNull UUID laneId,
        FrigateBindDirection bindDirection,
        @NotNull Boolean linkageEnabled) {
}
