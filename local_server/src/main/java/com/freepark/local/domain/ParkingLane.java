package com.freepark.local.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "parking_lane")
public class ParkingLane extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ParkingLot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_lot_id")
    private ParkingLot linkedLot;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "lane_type", nullable = false, length = 32)
    private LaneType laneType = LaneType.ENTRANCE;

    @Column(nullable = false)
    private boolean enabled = true;

    protected ParkingLane() {
    }

    public ParkingLane(
            ParkingLot lot, ParkingLot linkedLot, String name, String code, LaneType laneType, boolean enabled) {
        this.lot = lot;
        this.linkedLot = linkedLot;
        this.name = name.trim();
        this.code = code.trim();
        this.laneType = laneType == null ? LaneType.ENTRANCE : laneType;
        this.enabled = enabled;
    }

    public ParkingLot getLot() {
        return lot;
    }

    public ParkingLot getLinkedLot() {
        return linkedLot;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public LaneType getLaneType() {
        return laneType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void updateDetails(
            ParkingLot lot, ParkingLot linkedLot, String name, LaneType laneType, boolean enabled) {
        this.lot = lot;
        this.linkedLot = linkedLot;
        this.name = name.trim();
        this.laneType = laneType == null ? this.laneType : laneType;
        this.enabled = enabled;
    }
}
