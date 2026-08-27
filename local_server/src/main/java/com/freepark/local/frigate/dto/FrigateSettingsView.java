package com.freepark.local.frigate.dto;

import java.time.Instant;

import com.freepark.local.domain.FrigateLinkStatus;

public record FrigateSettingsView(
        String apiHost,
        int apiPort,
        String mqttHost,
        int mqttPort,
        String topicPrefix,
        String mqttUsername,
        boolean mqttPasswordSet,
        boolean enabled,
        FrigateLinkStatus linkStatus,
        Instant lastTestAt,
        Instant updatedAt) {
}
