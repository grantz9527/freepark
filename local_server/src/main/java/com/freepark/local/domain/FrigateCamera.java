package com.freepark.local.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "frigate_camera",
        uniqueConstraints = @UniqueConstraint(columnNames = {"camera_name"}))
public class FrigateCamera extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "camera_name", nullable = false, length = 120)
    private String cameraName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_status", nullable = false, length = 16)
    private FrigateLinkStatus linkStatus = FrigateLinkStatus.DISCONNECTED;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "lane_id")
    private UUID laneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bind_direction", length = 16)
    private FrigateBindDirection bindDirection;

    @Column(name = "linkage_enabled", nullable = false)
    private boolean linkageEnabled = true;

    @Column(name = "last_plate", length = 32)
    private String lastPlate;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    protected FrigateCamera() {
    }

    public FrigateCamera(String name, String cameraName, boolean enabled) {
        this.name = name.trim();
        this.cameraName = cameraName.trim();
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name.trim();
    }

    public String getCameraName() {
        return cameraName;
    }

    public void setCameraName(String cameraName) {
        this.cameraName = cameraName.trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FrigateLinkStatus getLinkStatus() {
        return linkStatus;
    }

    public void setLinkStatus(FrigateLinkStatus linkStatus) {
        this.linkStatus = linkStatus;
    }

    public Instant getLastTestAt() {
        return lastTestAt;
    }

    public void setLastTestAt(Instant lastTestAt) {
        this.lastTestAt = lastTestAt;
    }

    public UUID getLaneId() {
        return laneId;
    }

    public void setLaneId(UUID laneId) {
        this.laneId = laneId;
    }

    public FrigateBindDirection getBindDirection() {
        return bindDirection;
    }

    public void setBindDirection(FrigateBindDirection bindDirection) {
        this.bindDirection = bindDirection;
    }

    public boolean isLinkageEnabled() {
        return linkageEnabled;
    }

    public void setLinkageEnabled(boolean linkageEnabled) {
        this.linkageEnabled = linkageEnabled;
    }

    public String getLastPlate() {
        return lastPlate;
    }

    public void setLastPlate(String lastPlate) {
        this.lastPlate = lastPlate;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(Instant lastEventAt) {
        this.lastEventAt = lastEventAt;
    }
}
