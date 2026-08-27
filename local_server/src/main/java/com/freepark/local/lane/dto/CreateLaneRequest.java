package com.freepark.local.lane.dto;

import java.util.UUID;

import com.freepark.local.domain.LaneType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLaneRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = 2, max = 64) String code,
        @NotNull LaneType laneType,
        @NotNull UUID lotId,
        UUID linkedLotId,
        Boolean enabled) {
}
