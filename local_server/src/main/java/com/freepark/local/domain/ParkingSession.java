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
    }

    /** 作废流水（在场或已出场均可作废）。 */
    public void markVoided() {
        this.status = ParkingSessionStatus.VOIDED;
    }
}
