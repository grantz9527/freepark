package com.freepark.local.barrier.dto;

import java.time.Instant;
import java.util.UUID;

import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingLane;

public record BarrierView(
        UUID id,
        UUID laneId,
        String laneName,
        String laneCode,
        String name,
        String code,
        String brand,
        String model,
        String host,
        Integer port,
        String streamUrl,
        boolean enabled,
        Instant lastPollAt,
        boolean online,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 档案视图（不计算在线状态）：仅作即时写操作的返回，lastPollAt 取实体落库值。
     */
    public static BarrierView from(ParkingBarrier barrier) {
        return from(barrier, barrier.getLastPollAt(), false);
    }

    /**
     * 实时视图：lastPollAt 由心跳登记中心给出（内存优先），online 由心跳新鲜度推导。
     */
    public static BarrierView from(ParkingBarrier barrier, Instant lastPollAt, boolean online) {
        ParkingLane lane = barrier.getLane();
        return new BarrierView(
                barrier.getId(),
                lane == null ? null : lane.getId(),
                lane == null ? null : lane.getName(),
                lane == null ? null : lane.getCode(),
                barrier.getName(),
                barrier.getCode(),
                barrier.getBrand(),
                barrier.getModel(),
                barrier.getHost(),
                barrier.getPort(),
                barrier.getStreamUrl(),
                barrier.isEnabled(),
                lastPollAt,
                online,
                barrier.getCreatedAt(),
                barrier.getUpdatedAt());
    }
}
