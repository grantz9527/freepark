package com.freepark.local.booth.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBoothRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 64) String code,
        @Size(max = 255) String location,
        Boolean enabled,
        List<UUID> laneIds) {
}
