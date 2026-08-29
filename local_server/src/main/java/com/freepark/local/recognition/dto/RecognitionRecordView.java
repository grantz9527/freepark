package com.freepark.local.recognition.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.RecognitionEventType;
import com.freepark.local.domain.RecognitionRecord;

/**
 * 识别记录视图。direction 统一映射为前端约定的 ENTRANCE / EXIT。
 */
public record RecognitionRecordView(
        UUID id,
        UUID lotId,
        String lotName,
        UUID laneId,
        String laneName,
        String plateNumber,
        PlateColor plateColor,
        Instant eventTime,
        String eventImage,
        RecognitionEventType eventType,
        String direction,
        boolean abnormal,
        String abnormalReason,
        boolean voided,
        String sourceSimEventId,
        Instant createdAt,
        Instant updatedAt) {

    public static RecognitionRecordView from(RecognitionRecord record) {
        String direction = normalizeDirection(record.getDirection());
        return new RecognitionRecordView(
                record.getId(),
                record.getLotId(),
                record.getLotName(),
                record.getLaneId(),
                record.getLaneName(),
                record.getPlate() == null ? "" : record.getPlate().trim().toUpperCase(),
                record.getPlateColor(),
                record.getCapturedAt(),
                record.getEventImage(),
                record.getEventType(),
                direction,
                record.isAbnormal(),
                record.getAbnormalReason(),
                record.isVoided(),
                record.getSourceSimEventId(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }

    /**
     * 将设备侧方向值（IN/OUT/1/2 等）统一为前端 ENTRANCE/EXIT。
     */
    public static String normalizeDirection(String direction) {
        if (direction == null) {
            return null;
        }
        String upper = direction.trim().toUpperCase();
        return switch (upper) {
            case "IN", "1", "ENTRANCE" -> "ENTRANCE";
            case "OUT", "2", "EXIT" -> "EXIT";
            default -> null;
        };
    }
}
