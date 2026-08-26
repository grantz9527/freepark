package com.freepark.local.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "parking_booth",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lot_id", "name"}))
public class ParkingBooth extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ParkingLot lot;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 64)
    private String code;

    @Column(length = 255)
    private String location;

    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "parking_booth_lane",
            joinColumns = @JoinColumn(name = "booth_id"),
            inverseJoinColumns = @JoinColumn(name = "lane_id"))
    private List<ParkingLane> lanes = new ArrayList<>();

    protected ParkingBooth() {
    }

    public ParkingBooth(ParkingLot lot, String name, String code, String location, boolean enabled) {
        this.lot = lot;
        this.name = name.trim();
        this.code = code;
        this.location = location;
        this.enabled = enabled;
    }

    public ParkingLot getLot() {
        return lot;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getLocation() {
        return location;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<ParkingLane> getLanes() {
        return lanes;
    }

    public void setLanes(List<ParkingLane> lanes) {
        this.lanes = lanes == null ? new ArrayList<>() : lanes;
    }

    public void updateDetails(String name, String code, String location, boolean enabled) {
        this.name = name.trim();
        this.code = code;
        this.location = location;
        this.enabled = enabled;
    }
}
