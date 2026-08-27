package com.freepark.local.nodeconfig.dto;

import com.freepark.local.domain.NodeMode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateNodeSettingsRequest(
        @NotNull NodeMode mode,
        @Size(max = 255) String mqttHost,
        @Min(1) @Max(65535) Integer mqttPort,
        @Size(max = 128) String mqttClientId,
        @Size(max = 128) String mqttUsername,
        @Size(max = 255) String mqttPassword,
        @Size(max = 255) String mqttTopicPrefix) {
}
