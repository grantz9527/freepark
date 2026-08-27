package com.freepark.local.frigate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SimulateFrigateEventRequest(@NotBlank @Size(max = 32) String plate) {
}
