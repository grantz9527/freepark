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

    /**
     * 本地臻识机内白名单同步状态（<strong>不参与云端协同</strong>）。
     * 由本地保存、云端下发应用、定时对账按时间窗与下发结果刷新。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "camera_whitelist_sync_status", nullable = false, length = 32)
    @ColumnDefault("'NOT_EFFECTIVE'")
    private CameraWhitelistSyncStatus cameraWhitelistSyncStatus = CameraWhitelistSyncStatus.NOT_EFFECTIVE;

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
        refreshCameraWhitelistSyncStatus(Instant.now());
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

    public CameraWhitelistSyncStatus getCameraWhitelistSyncStatus() {
        return cameraWhitelistSyncStatus == null
                ? CameraWhitelistSyncStatus.NOT_EFFECTIVE
                : cameraWhitelistSyncStatus;
    }

    /**
     * 按时间窗/启用/类型推进状态机（保留「已下发 / 过期已移除」等交付结果）；返回是否变化。
     */
    public boolean refreshCameraWhitelistSyncStatus(Instant now) {
        CameraWhitelistSyncStatus next = computeNextSyncStatus(now);
        if (next == getCameraWhitelistSyncStatus()) {
            return false;
        }
        this.cameraWhitelistSyncStatus = next;
        return true;
    }

    /** 仅更新内存中的机内状态（CAS 落库成功后回写实体）。 */
    public void applyCameraWhitelistSyncStatus(CameraWhitelistSyncStatus status) {
        this.cameraWhitelistSyncStatus = status == null
                ? CameraWhitelistSyncStatus.NOT_EFFECTIVE
                : status;
    }

    /** 下发成功后标记为已下发（仅对待下发）。 */
    public boolean markCameraDelivered() {
        if (getCameraWhitelistSyncStatus() != CameraWhitelistSyncStatus.PENDING_DELIVER) {
            return false;
        }
        this.cameraWhitelistSyncStatus = CameraWhitelistSyncStatus.DELIVERED;
        return true;
    }

    /** 过期删除成功后标记为过期已移除（仅对过期待移除）。 */
    public boolean markCameraExpiredRemoved() {
        if (getCameraWhitelistSyncStatus() != CameraWhitelistSyncStatus.EXPIRED_PENDING_REMOVE) {
            return false;
        }
        this.cameraWhitelistSyncStatus = CameraWhitelistSyncStatus.EXPIRED_REMOVED;
        return true;
    }

    public CameraWhitelistSyncStatus computeNextSyncStatus(Instant now) {
        Instant at = now == null ? Instant.now() : now;
        CameraWhitelistSyncStatus current = getCameraWhitelistSyncStatus();
        TimePhase phase = resolveTimePhase(at);
        return switch (phase) {
            case INELIGIBLE, NOT_STARTED -> CameraWhitelistSyncStatus.NOT_EFFECTIVE;
            case ACTIVE -> current == CameraWhitelistSyncStatus.DELIVERED
                    ? CameraWhitelistSyncStatus.DELIVERED
                    : CameraWhitelistSyncStatus.PENDING_DELIVER;
            case EXPIRED -> current == CameraWhitelistSyncStatus.EXPIRED_REMOVED
                    ? CameraWhitelistSyncStatus.EXPIRED_REMOVED
                    : CameraWhitelistSyncStatus.EXPIRED_PENDING_REMOVE;
        };
    }

    private TimePhase resolveTimePhase(Instant at) {
        if (!enabled || (type != VehicleType.OWNER && type != VehicleType.MONTHLY)) {
            return TimePhase.INELIGIBLE;
        }
        if (startTime != null && at.isBefore(startTime)) {
            return TimePhase.NOT_STARTED;
        }
        if (endTime != null && at.isAfter(endTime)) {
            return TimePhase.EXPIRED;
        }
        return TimePhase.ACTIVE;
    }

    private enum TimePhase {
        INELIGIBLE,
        NOT_STARTED,
        ACTIVE,
        EXPIRED
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
        refreshCameraWhitelistSyncStatus(Instant.now());
    }
}
