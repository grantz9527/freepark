package com.freepark.local.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 识别记录：车牌 + 车牌颜色 + 抓拍图像引用 + 方向 + 抓拍时间。
 *
 * <p>来源有两种，只会填其中一种关联：
 * <ul>
 *   <li>道闸/识别一体机直连上报：关联 {@link #device}；</li>
 *   <li>Frigate MQTT 事件 / 模拟识别：关联 {@link #frigateCamera}，并在绑定时同步写入 {@link #laneId}。</li>
 * </ul>
 */
@Entity
@Table(name = "recognition_record")
public class RecognitionRecord extends BaseEntity {

    /** 道闸/识别一体机来源的关联设备；Frigate 来源时为 null。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "device_id", foreignKey = @ForeignKey(name = "fk_recognition_record_device"))
    private ParkingBarrier device;

    /** Frigate / 模拟识别来源的关联相机；道闸直连来源时为 null。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "frigate_camera_id", foreignKey = @ForeignKey(name = "fk_recognition_record_frigate_camera"))
    private FrigateCamera frigateCamera;

    /** 识别发生的通道（冗余快照，便于来源无关的查询）。 */
    @Column(name = "lane_id")
    private UUID laneId;

    @Column(nullable = false, length = 32)
    private String plate;

    /** 车牌颜色。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "plate_color", length = 32)
    private PlateColor plateColor;

    /** 抓拍图像引用（存储路径或资源 id，避免直接存大字段）。 */
    @Column(length = 512)
    private String imageRef;

    /** 方向：IN/OUT 等，由设备协议决定。 */
    @Column(length = 32)
    private String direction;

    @Column(nullable = false)
    private Instant capturedAt;

    protected RecognitionRecord() {
    }

    /** 道闸/识别一体机直连来源的构造方法。 */
    public RecognitionRecord(
            ParkingBarrier device,
            String plate,
            PlateColor plateColor,
            String imageRef,
            String direction,
            Instant capturedAt) {
        this.device = device;
        this.laneId = device == null || device.getLane() == null ? null : device.getLane().getId();
        this.plate = plate == null ? "" : plate.trim();
        this.plateColor = plateColor;
        this.imageRef = imageRef;
        this.direction = direction;
        this.capturedAt = capturedAt;
    }

    /** Frigate 相机 / 模拟识别来源的构造方法。 */
    public RecognitionRecord(
            FrigateCamera frigateCamera,
            String plate,
            PlateColor plateColor,
            String imageRef,
            String direction,
            Instant capturedAt) {
        this.frigateCamera = frigateCamera;
        this.laneId = frigateCamera == null ? null : frigateCamera.getLaneId();
        this.plate = plate == null ? "" : plate.trim();
        this.plateColor = plateColor;
        this.imageRef = imageRef;
        this.direction = direction;
        this.capturedAt = capturedAt;
    }

    public ParkingBarrier getDevice() {
        return device;
    }

    public FrigateCamera getFrigateCamera() {
        return frigateCamera;
    }

    public UUID getLaneId() {
        return laneId;
    }

    public void setLaneId(UUID laneId) {
        this.laneId = laneId;
    }

    public String getPlate() {
        return plate;
    }

    public PlateColor getPlateColor() {
        return plateColor;
    }

    public void setPlateColor(PlateColor plateColor) {
        this.plateColor = plateColor;
    }

    public String getImageRef() {
        return imageRef;
    }

    public String getDirection() {
        return direction;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}
