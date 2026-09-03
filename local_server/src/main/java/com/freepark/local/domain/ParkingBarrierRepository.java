package com.freepark.local.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingBarrierRepository extends JpaRepository<ParkingBarrier, UUID> {

    boolean existsByLaneIdAndCodeIgnoreCase(UUID laneId, String code);

    List<ParkingBarrier> findAllByLaneIdOrderByCreatedAtDesc(UUID laneId);

    Optional<ParkingBarrier> findByCodeIgnoreCase(String code);

    /** 仅更新心跳时间，避免对 detached 实体的 merge 产生额外 SELECT。 */
    @Modifying
    @Query("update ParkingBarrier b set b.lastPollAt = :at where b.id = :id")
    int touchLastPollAt(@Param("id") UUID id, @Param("at") Instant at);
}
