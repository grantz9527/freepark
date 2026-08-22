package com.freepark.local.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "parking_area")
public class ParkingArea extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private ParkingLocation location;

    @Column(nullable = false, length = 80)
    private String name;

    protected ParkingArea() {
    }

    public ParkingArea(ParkingLocation location, String name) {
        this.location = location;
        this.name = name.trim();
    }

    public ParkingLocation getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        this.name = name.trim();
    }
}
