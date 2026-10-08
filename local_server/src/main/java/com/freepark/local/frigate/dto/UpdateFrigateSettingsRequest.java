package com.freepark.local.frigate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateFrigateSettingsRequest(
        @Size(max = 255) String apiHost,
        @NotNull @Min(1) @Max(65535) Integer apiPort,
        @Size(max = 255) String mqttHost,
        @NotNull @Min(1) @Max(65535) Integer mqttPort,
        @Size(max = 255) String topicPrefix,
        @Size(max = 128) String mqttUsername,
        @Size(max = 255) String mqttPassword,
        @NotNull Boolean enabled) {
}
