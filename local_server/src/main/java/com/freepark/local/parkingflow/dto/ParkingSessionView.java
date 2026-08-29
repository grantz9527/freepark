package com.freepark.local.parkingflow.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.ParkingSession;
import com.freepark.local.domain.ParkingSessionStatus;
import com.freepark.local.domain.PlateColor;

public record ParkingSessionView(
        UUID id,
        UUID lotId,
        String lotName,
        String plateNumber,
        PlateColor plateColor,
        ParkingSessionStatus status,
        Instant entryTime,
        UUID entryLaneId,
        String entryLaneName,
        UUID entryRecognitionId,
        String entryImage,
        Instant exitTime,
        UUID exitLaneId,
        String exitLaneName,
        UUID exitRecognitionId,
        String exitImage,
        Instant createdAt,
        Instant updatedAt) {

    public static ParkingSessionView from(ParkingSession session) {
        return new ParkingSessionView(
                session.getId(),
                session.getLotId(),
                session.getLotName(),
                session.getPlateNumber(),
                session.getPlateColor(),
                session.getStatus(),
                session.getEntryTime(),
                session.getEntryLaneId(),
                session.getEntryLaneName(),
                session.getEntryRecognitionId(),
                session.getEntryImage(),
                session.getExitTime(),
                session.getExitLaneId(),
                session.getExitLaneName(),
                session.getExitRecognitionId(),
                session.getExitImage(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }
}
