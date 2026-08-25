package com.freepark.local.domain;

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

    public void updateDetails(String name, boolean enabled) {
        this.name = name.trim();
        this.enabled = enabled;
    }
}
