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
        name = "parking_space",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lot_id", "code"}))
public class ParkingSpace extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ParkingLot lot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private ParkingArea area;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private boolean enabled = true;

    protected ParkingSpace() {
    }

    public ParkingSpace(ParkingLot lot, ParkingArea area, String code, boolean enabled) {
        this.lot = lot;
        this.area = area;
        this.code = code.trim();
        this.enabled = enabled;
    }

    public ParkingLot getLot() {
        return lot;
    }

    public ParkingArea getArea() {
        return area;
    }

    public String getCode() {
        return code;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void updateDetails(ParkingArea area, String code, boolean enabled) {
        this.area = area;
        this.code = code.trim();
        this.enabled = enabled;
    }
}
