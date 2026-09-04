package com.freepark.local.nodeconfig.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.freepark.local.domain.NodeMode;

public record NodeSettingsView(
        NodeMode mode,
        String mqttHost,
        Integer mqttPort,
        String mqttClientId,
        String mqttUsername,
        boolean mqttPasswordSet,
        String mqttTopicPrefix,
        String feeApiUrl,
        boolean feeMockEnabled,
        BigDecimal feeMockAmount,
        Instant updatedAt) {
}
