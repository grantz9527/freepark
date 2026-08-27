package com.freepark.local.barrier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBarrierRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = 2, max = 64) String code,
        Boolean enabled) {
}
