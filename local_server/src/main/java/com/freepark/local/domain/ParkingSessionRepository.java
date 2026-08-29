package com.freepark.local.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ParkingSessionRepository
        extends JpaRepository<ParkingSession, UUID>, JpaSpecificationExecutor<ParkingSession> {

    Optional<ParkingSession> findFirstByLotIdAndPlateNumberIgnoreCaseAndStatusOrderByEntryTimeDesc(
            UUID lotId, String plateNumber, ParkingSessionStatus status);

    boolean existsByLotIdAndPlateNumberIgnoreCaseAndStatus(
            UUID lotId, String plateNumber, ParkingSessionStatus status);

    List<ParkingSession> findAllByOrderByEntryTimeDesc();

    List<ParkingSession> findAllByLotIdOrderByEntryTimeDesc(UUID lotId);
}
