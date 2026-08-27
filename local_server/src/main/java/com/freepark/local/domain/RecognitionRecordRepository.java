package com.freepark.local.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecognitionRecordRepository extends JpaRepository<RecognitionRecord, UUID> {

    List<RecognitionRecord> findByDeviceIdOrderByCapturedAtDesc(UUID deviceId, Pageable pageable);

    List<RecognitionRecord> findByPlateOrderByCapturedAtDesc(String plate, Pageable pageable);
}
