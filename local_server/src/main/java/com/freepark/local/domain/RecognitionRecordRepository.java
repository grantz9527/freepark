package com.freepark.local.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RecognitionRecordRepository
        extends JpaRepository<RecognitionRecord, UUID>, JpaSpecificationExecutor<RecognitionRecord> {

    List<RecognitionRecord> findByDevice_IdOrderByCapturedAtDesc(UUID deviceId, Pageable pageable);

    List<RecognitionRecord> findByPlateOrderByCapturedAtDesc(String plate, Pageable pageable);

    List<RecognitionRecord> findByLaneIdOrderByCapturedAtDesc(UUID laneId, Pageable pageable);

    List<RecognitionRecord> findByPlateIgnoreCaseAndAbnormalReasonAndCapturedAtGreaterThanEqualOrderByCapturedAtDesc(
            String plate, String abnormalReason, Instant capturedAt, Pageable pageable);
}
