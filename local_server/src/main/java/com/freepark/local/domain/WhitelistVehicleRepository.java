package com.freepark.local.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhitelistVehicleRepository
        extends JpaRepository<WhitelistVehicle, UUID>, JpaSpecificationExecutor<WhitelistVehicle> {

    /**
     * 车场下是否存在当前时间正处于有效时间区间（且启用）的白名单记录。
     * 同一车牌可有多张停车卡（多条记录），只有区间覆盖当前时刻的才视为有效白名单。
     */
    @Query("""
            select count(w) > 0 from WhitelistVehicle w
            where w.lot.id = :lotId
              and lower(w.plateNumber) = lower(:plateNumber)
              and w.enabled = true
              and (w.startTime is null or w.startTime <= :now)
              and (w.endTime is null or w.endTime >= :now)
            """)
    boolean existsActiveAt(@Param("lotId") UUID lotId, @Param("plateNumber") String plateNumber,
            @Param("now") Instant now);

    /**
     * 取当前时间有效的白名单记录（用于播报车辆类型与剩余天数），
     * 多张有效卡并存时优先临近到期的一张；长期有效（endTime 为空）排最后。
     */
    @Query("""
            select w from WhitelistVehicle w
            where w.lot.id = :lotId
              and lower(w.plateNumber) = lower(:plateNumber)
              and w.enabled = true
              and (w.startTime is null or w.startTime <= :now)
              and (w.endTime is null or w.endTime >= :now)
            order by w.endTime asc nulls last
            """)
    List<WhitelistVehicle> findActiveAt(@Param("lotId") UUID lotId, @Param("plateNumber") String plateNumber,
            @Param("now") Instant now);

    /**
     * 车牌在车场内的全部启用停车卡（不限时间区间，含已过期/未生效）。
     * 返回顺序不保证，调用方（连续有效段拼接）会自行按开始时间排序。
     */
    @Query("""
            select w from WhitelistVehicle w
            where w.lot.id = :lotId
              and lower(w.plateNumber) = lower(:plateNumber)
              and w.enabled = true
            """)
    List<WhitelistVehicle> findAllEnabledByLotAndPlate(@Param("lotId") UUID lotId,
            @Param("plateNumber") String plateNumber);
}
