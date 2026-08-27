package com.freepark.local.barrier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBarrierRequest(@NotBlank @Size(max = 120) String name, Boolean enabled) {
}
