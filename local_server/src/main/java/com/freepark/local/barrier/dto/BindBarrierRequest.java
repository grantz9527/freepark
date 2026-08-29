package com.freepark.local.barrier.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record BindBarrierRequest(@NotNull UUID laneId) {
}
