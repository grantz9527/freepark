package com.freepark.local.domain;

import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "internal_vehicle",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lot_id", "plate_number"}))
public class InternalVehicle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ParkingLot lot;

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "plate_color", nullable = false, length = 16)
    private PlateColor plateColor = PlateColor.BLUE;

    @Column(name = "owner_name", nullable = false, length = 80)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'OTHER'")
    @Column(name = "vehicle_type", nullable = false, length = 16)
    private InternalVehicleType type = InternalVehicleType.OTHER;

    @Column(length = 32)
    private String phone;

    @Column(length = 80)
    private String department;

    @Column(length = 255)
    private String remark;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(nullable = false)
    private boolean enabled = true;

    protected InternalVehicle() {
    }

    public InternalVehicle(
            ParkingLot lot,
            String plateNumber,
            PlateColor plateColor,
            String ownerName,
            InternalVehicleType type,
            String phone,
            String department,
            String remark,
            boolean enabled) {
        this.lot = lot;
        this.plateNumber = plateNumber.trim();
        this.plateColor = plateColor == null ? PlateColor.BLUE : plateColor;
        this.ownerName = ownerName.trim();
        this.type = type == null ? InternalVehicleType.OTHER : type;
        this.phone = phone;
        this.department = department;
        this.remark = remark;
        this.enabled = enabled;
    }

    public ParkingLot getLot() {
        return lot;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public PlateColor getPlateColor() {
        return plateColor;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public InternalVehicleType getType() {
        return type;
    }

    public String getPhone() {
        return phone;
    }

    public String getDepartment() {
        return department;
    }

    public String getRemark() {
        return remark;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void updateDetails(
            String plateNumber,
            PlateColor plateColor,
            String ownerName,
            InternalVehicleType type,
            String phone,
            String department,
            String remark,
            boolean enabled) {
        this.plateNumber = plateNumber.trim();
        this.plateColor = plateColor == null ? PlateColor.BLUE : plateColor;
        this.ownerName = ownerName.trim();
        this.type = type == null ? InternalVehicleType.OTHER : type;
        this.phone = phone;
        this.department = department;
        this.remark = remark;
        this.enabled = enabled;
    }
}
