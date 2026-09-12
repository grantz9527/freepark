package com.freepark.local.nodeconfig.dto;

import java.math.BigDecimal;

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
        @Size(max = 255) String mqttTopicPrefix,
        @Size(max = 255) String configSyncTopicPrefix,
        @Size(max = 255) String reportTopicPrefix,
        @Size(max = 255) String commandTopicPrefix,
        @Size(max = 64) String nodeCode,
        @Size(max = 255) String feeApiUrl,
        Boolean feeMockEnabled,
        BigDecimal feeMockAmount) {
}
