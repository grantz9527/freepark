package com.freepark.local.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    long countByLotIdAndStatus(UUID lotId, ParkingSessionStatus status);

    /** 按车场汇总在场流水数，供满位拦截缓存对账。返回 [lotId, count]。 */
    @Query("select s.lotId, count(s.id) from ParkingSession s where s.status = :status group by s.lotId")
    List<Object[]> countGroupedByLotIdAndStatus(@Param("status") ParkingSessionStatus status);

    List<ParkingSession> findTop50ByStatusOrderByEntryTimeDesc(ParkingSessionStatus status);

    Optional<ParkingSession> findByCloudId(Long cloudId);
}
