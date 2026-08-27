package com.freepark.local.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 识别设备上报的识别记录：车牌 + 抓拍图像引用 + 方向 + 抓拍时间。
 * 由设备识别后通过上报接口写入。
 */
@Entity
@Table(name = "recognition_record")
public class RecognitionRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private ParkingBarrier device;

    @Column(nullable = false, length = 32)
    private String plate;

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

    public RecognitionRecord(ParkingBarrier device, String plate, String imageRef, String direction, Instant capturedAt) {
        this.device = device;
        this.plate = plate == null ? "" : plate.trim();
        this.imageRef = imageRef;
        this.direction = direction;
        this.capturedAt = capturedAt;
    }

    public ParkingBarrier getDevice() {
        return device;
    }

    public String getPlate() {
        return plate;
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
