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

    Optional<ParkingSession> findFirstByLotIdAndPlateNumberIgnoreCaseAndStatusNotOrderByEntryTimeDesc(
            UUID lotId, String plateNumber, ParkingSessionStatus excludedStatus);

    boolean existsByLotIdAndPlateNumberIgnoreCaseAndStatus(
            UUID lotId, String plateNumber, ParkingSessionStatus status);

    List<ParkingSession> findAllByLotIdAndPlateNumberIgnoreCaseAndStatus(
            UUID lotId, String plateNumber, ParkingSessionStatus status);

    List<ParkingSession> findAllByOrderByEntryTimeDesc();

    List<ParkingSession> findAllByLotIdOrderByEntryTimeDesc(UUID lotId);

    /** 待同步云端的流水：按入场时间升序取一批，避免单轮推送过多阻塞 */
    List<ParkingSession> findTop200BySyncPendingTrueOrderByEntryTimeAsc();

    Optional<ParkingSession> findByCloudId(Long cloudId);
}
