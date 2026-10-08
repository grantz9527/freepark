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

    List<ParkingBarrier> findAllByLaneLotIdAndEnabledTrue(UUID lotId);

    /** 已启用且绑定车道/车场的设备（含 lane、lot，避免 open-in-view=false 懒加载失败）。 */
    @Query("""
            select distinct b from ParkingBarrier b
            join fetch b.lane l
            join fetch l.lot
            where b.enabled = true
            """)
    List<ParkingBarrier> findAllEnabledBoundWithLot();

    @Query("""
            select b from ParkingBarrier b
            left join fetch b.lane l
            left join fetch l.lot
            where b.id = :id
            """)
    Optional<ParkingBarrier> findByIdWithLaneLot(@Param("id") UUID id);

    Optional<ParkingBarrier> findByCodeIgnoreCase(String code);

    /** 仅更新心跳时间，避免对 detached 实体的 merge 产生额外 SELECT。 */
    @Modifying
    @Query("update ParkingBarrier b set b.lastPollAt = :at where b.id = :id")
    int touchLastPollAt(@Param("id") UUID id, @Param("at") Instant at);

    /** 查设备绑定车道的类型（入口/出口/双向），用于指令开闸时推断欢迎/欢送播报语；未绑车道返回 empty。 */
    @Query("select b.lane.laneType from ParkingBarrier b where b.id = :id")
    Optional<LaneType> findBoundLaneType(@Param("id") UUID id);
}
