package com.freepark.local.recognition.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.RecognitionEventType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 手工/模拟录入识别记录的请求。direction 使用前端约定的 ENTRANCE / EXIT。
 */
public record CreateRecognitionRecordRequest(
        UUID lotId,
        @Size(max = 120) String lotName,
        UUID laneId,
        @Size(max = 120) String laneName,
        @NotBlank @Size(max = 32) String plateNumber,
        @NotNull PlateColor plateColor,
        Instant eventTime,
        String eventImage,
        RecognitionEventType eventType,
        String direction,
        Boolean abnormal,
        String abnormalReason,
        String sourceSimEventId) {
}
