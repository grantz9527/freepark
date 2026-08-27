package com.freepark.local.frigate.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.FrigateBindDirection;
import com.freepark.local.domain.FrigateLinkStatus;

public record FrigateCameraView(
        UUID id,
        String name,
        String cameraName,
        boolean enabled,
        FrigateLinkStatus linkStatus,
        Instant lastTestAt,
        UUID laneId,
        FrigateBindDirection bindDirection,
        boolean linkageEnabled,
        String lastPlate,
        Instant lastEventAt,
        Instant createdAt,
        Instant updatedAt) {
}
