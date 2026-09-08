package com.freepark.local.domain;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.freepark.driver.api.model.VehicleType;

/**
 * 白名单车辆（停车卡）记录：同一车场下同一车牌允许多条记录，
 * 每条对应一张带时间区间（startTime~endTime）的停车卡，通过 startTime/endTime 决定当前是否生效。
 * 注意：实体不再声明 (lot_id, plate_number) 唯一约束，历史库中由旧版本建立的唯一索引由
 * WhitelistVehiclePlateUniqueConstraintCleanupRunner 在启动时幂等清理。
 */
@Entity
@Table(name = "whitelist_vehicle")
public class WhitelistVehicle extends CloudSyncedEntity {

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
    private VehicleType type = VehicleType.OTHER;

    @Column(length = 32)
    private String phone;

    @Column(length = 80)
    private String department;

    @Column(length = 255)
    private String remark;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(nullable = false)
    private boolean enabled = true;

    protected WhitelistVehicle() {
    }

    public WhitelistVehicle(
            ParkingLot lot,
            String plateNumber,
            PlateColor plateColor,
            String ownerName,
            VehicleType type,
            String phone,
            String department,
            String remark,
            Instant startTime,
            Instant endTime,
            boolean enabled) {
        this.lot = lot;
        this.plateNumber = plateNumber.trim();
        this.plateColor = plateColor == null ? PlateColor.BLUE : plateColor;
        this.ownerName = ownerName.trim();
        this.type = type == null ? VehicleType.OTHER : type;
        this.phone = phone;
        this.department = department;
        this.remark = remark;
        this.startTime = startTime;
        this.endTime = endTime;
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

    public VehicleType getType() {
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

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void updateDetails(
            String plateNumber,
            PlateColor plateColor,
            String ownerName,
            VehicleType type,
            String phone,
            String department,
            String remark,
            Instant startTime,
            Instant endTime,
            boolean enabled) {
        this.plateNumber = plateNumber.trim();
        this.plateColor = plateColor == null ? PlateColor.BLUE : plateColor;
        this.ownerName = ownerName.trim();
        this.type = type == null ? VehicleType.OTHER : type;
        this.phone = phone;
        this.department = department;
        this.remark = remark;
        this.startTime = startTime;
        this.endTime = endTime;
        this.enabled = enabled;
    }
}
