package com.freepark.local.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 下发到识别设备的排队指令（开闸/关闸等）。
 * 设备轮询时按 FIFO 出队，出队后标记为 DELIVERED。
 */
@Entity
@Table(name = "device_command")
public class DeviceCommand extends BaseEntity {

    public enum Action {
        OPEN,
        CLOSE,
        QUERY
    }

    public enum Status {
        PENDING,
        DELIVERED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private ParkingBarrier device;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_code", nullable = false, length = 32)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PENDING;

    /** 来源（admin 手动 / 规则触发），便于追溯。 */
    @Column(length = 64)
    private String source;

    @Column
    private Instant deliveredAt;

    protected DeviceCommand() {
    }

    public DeviceCommand(ParkingBarrier device, Action action, String source) {
        this.device = device;
        this.action = action;
        this.source = source;
    }

    public ParkingBarrier getDevice() {
        return device;
    }

    public Action getAction() {
        return action;
    }

    public Status getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void markDelivered(Instant at) {
        this.status = Status.DELIVERED;
        this.deliveredAt = at;
    }

    public UUID getDeviceId() {
        return device.getId();
    }
}
