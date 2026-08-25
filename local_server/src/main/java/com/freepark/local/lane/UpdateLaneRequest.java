package com.freepark.local.lane;

import java.util.UUID;

import com.freepark.local.domain.LaneType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateLaneRequest(
        @NotBlank @Size(max = 120) String name,
        LaneType laneType,
        @NotNull UUID lotId,
        UUID linkedLotId,
        Boolean enabled) {
}
