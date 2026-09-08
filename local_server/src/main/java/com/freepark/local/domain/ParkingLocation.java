package com.freepark.local.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "parking_location")
public class ParkingLocation extends CloudSyncedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ParkingLot lot;

    @Column(nullable = false, length = 80)
    private String name;

    protected ParkingLocation() {
    }

    public ParkingLocation(ParkingLot lot, String name) {
        this.lot = lot;
        this.name = name.trim();
    }

    public ParkingLot getLot() {
        return lot;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        this.name = name.trim();
    }

    /** 云端同步变更所属车场时使用：将位置挂到新车场下（保留本地 UUID 主键与引用）。 */
    public void attachTo(ParkingLot lot) {
        this.lot = lot;
    }
}
