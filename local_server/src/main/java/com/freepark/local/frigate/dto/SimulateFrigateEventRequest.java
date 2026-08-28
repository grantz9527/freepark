package com.freepark.local.frigate.dto;

import com.freepark.local.domain.PlateColor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SimulateFrigateEventRequest(
        @NotBlank @Size(max = 32) String plate,
        PlateColor plateColor) {
}
