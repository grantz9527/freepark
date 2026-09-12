package com.freepark.local.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 停车流水：入场识别自动生成在场流水，出场识别匹配并关闭流水。
 * 关联的识别记录通过 entryRecognitionId / exitRecognitionId 回指。
 */
@Entity
@Table(name = "parking_session")
public class ParkingSession extends BaseEntity {

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "lot_name", length = 120)
    private String lotName;

    @Column(nullable = false, length = 32)
    private String plateNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "plate_color", length = 32)
    private PlateColor plateColor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParkingSessionStatus status = ParkingSessionStatus.OPEN;

    @Column(nullable = false)
    private Instant entryTime;

    @Column(name = "entry_lane_id")
    private UUID entryLaneId;

    @Column(name = "entry_lane_name", length = 120)
    private String entryLaneName;

    @Column(name = "entry_recognition_id")
    private UUID entryRecognitionId;

    @Column(name = "entry_image", columnDefinition = "TEXT")
    private String entryImage;

    @Column(name = "exit_time")
    private Instant exitTime;

    @Column(name = "exit_lane_id")
    private UUID exitLaneId;

    @Column(name = "exit_lane_name", length = 120)
    private String exitLaneName;

    @Column(name = "exit_recognition_id")
    private UUID exitRecognitionId;

    @Column(name = "exit_image", columnDefinition = "TEXT")
    private String exitImage;

    /**
     * 是否待同步云端：入场创建/出场关闭/作废等任何状态变化都会置 true；
     * 上报成功后由上报器按“快照未变”比对后清 false。存量行可能为 NULL，
     * 一律按 false 处理（功能上线前的历史流水不补推），查询只取 true 的行。
     */
    @Column(name = "sync_pending")
    private Boolean syncPending = Boolean.TRUE;

    /** 云端流水主键：云端下发或边缘上报回填后有值。 */
    @Column(name = "cloud_id", unique = true)
    private Long cloudId;

    /** 最近一次已应用的云端写修订号，上报时回传以免过期快照覆盖云端改动。 */
    @Column(name = "cloud_revision")
    private Long cloudRevision;

    protected ParkingSession() {
    }

    public ParkingSession(
            UUID lotId,
            String lotName,
            String plateNumber,
            PlateColor plateColor,
            Instant entryTime,
            UUID entryLaneId,
            String entryLaneName,
            UUID entryRecognitionId,
            String entryImage) {
        this.lotId = lotId;
        this.lotName = lotName;
        this.plateNumber = plateNumber == null ? "" : plateNumber.trim().toUpperCase();
        this.plateColor = plateColor;
        this.entryTime = entryTime;
        this.entryLaneId = entryLaneId;
        this.entryLaneName = entryLaneName;
        this.entryRecognitionId = entryRecognitionId;
        this.entryImage = entryImage;
        this.syncPending = Boolean.TRUE;
    }

    public UUID getLotId() {
        return lotId;
    }

    public String getLotName() {
        return lotName;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public PlateColor getPlateColor() {
        return plateColor;
    }

    public ParkingSessionStatus getStatus() {
        return status;
    }

    public Instant getEntryTime() {
        return entryTime;
    }

    public UUID getEntryLaneId() {
        return entryLaneId;
    }

    public String getEntryLaneName() {
        return entryLaneName;
    }

    public UUID getEntryRecognitionId() {
        return entryRecognitionId;
    }

    public String getEntryImage() {
        return entryImage;
    }

    public Instant getExitTime() {
        return exitTime;
    }

    public UUID getExitLaneId() {
        return exitLaneId;
    }

    public String getExitLaneName() {
        return exitLaneName;
    }

    public UUID getExitRecognitionId() {
        return exitRecognitionId;
    }

    public String getExitImage() {
        return exitImage;
    }

    public Boolean getSyncPending() {
        return syncPending;
    }

    public void setSyncPending(Boolean syncPending) {
        this.syncPending = syncPending;
    }

    public Long getCloudId() {
        return cloudId;
    }

    public void setCloudId(Long cloudId) {
        this.cloudId = cloudId;
    }

    public Long getCloudRevision() {
        return cloudRevision;
    }

    public void setCloudRevision(Long cloudRevision) {
        this.cloudRevision = cloudRevision;
    }

    public void setLotId(UUID lotId) {
        this.lotId = lotId;
    }

    public void setLotName(String lotName) {
        this.lotName = lotName;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber == null ? "" : plateNumber.trim().toUpperCase();
    }

    public void setPlateColor(PlateColor plateColor) {
        this.plateColor = plateColor;
    }

    public void setStatus(ParkingSessionStatus status) {
        this.status = status;
    }

    public void setEntryTime(Instant entryTime) {
        this.entryTime = entryTime;
    }

    public void setEntryLaneName(String entryLaneName) {
        this.entryLaneName = entryLaneName;
    }

    public void setExitTime(Instant exitTime) {
        this.exitTime = exitTime;
    }

    public void setExitLaneName(String exitLaneName) {
        this.exitLaneName = exitLaneName;
    }

    /** 出场匹配成功：关闭流水。 */
    public void closeWithExit(
            Instant exitTime,
            UUID exitLaneId,
            String exitLaneName,
            UUID exitRecognitionId,
            String exitImage) {
        this.status = ParkingSessionStatus.CLOSED;
        this.exitTime = exitTime;
        this.exitLaneId = exitLaneId;
        this.exitLaneName = exitLaneName;
        this.exitRecognitionId = exitRecognitionId;
        this.exitImage = exitImage;
        this.syncPending = Boolean.TRUE;
    }

    /** 作废流水（在场或已出场均可作废）。 */
    public void markVoided() {
        this.status = ParkingSessionStatus.VOIDED;
        this.syncPending = Boolean.TRUE;
    }
}
