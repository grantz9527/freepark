package com.freepark.local.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "parking_barrier",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lane_id", "code"}))
public class ParkingBarrier extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lane_id", nullable = false)
    private ParkingLane lane;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 设备品牌/协议标识，如 ZHENSHI，用于网关按协议适配上报与指令。 */
    @Column(length = 64)
    private String brand;

    /** 最近一次轮询时间戳，用于推导设备在线状态。 */
    @Column
    private Instant lastPollAt;

    protected ParkingBarrier() {
    }

    public ParkingBarrier(ParkingLane lane, String name, String code, boolean enabled) {
        this.lane = lane;
        this.name = name.trim();
        this.code = code.trim();
        this.enabled = enabled;
    }

    public ParkingLane getLane() {
        return lane;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public Instant getLastPollAt() {
        return lastPollAt;
    }

    public void markPolled(Instant at) {
        this.lastPollAt = at;
    }

    public void updateDetails(String name, boolean enabled) {
        this.name = name.trim();
        this.enabled = enabled;
    }
}
