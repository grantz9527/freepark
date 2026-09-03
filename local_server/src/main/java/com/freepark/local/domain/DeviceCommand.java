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
        /** 开闸：触发一次放行，闸杆抬起。 */
        OPEN,
        /** 落闸：解除常开并落下闸杆。 */
        CLOSE,
        /** 常开：保持闸杆抬起、车辆持续放行（如高峰期），由设备 IO 持续输出实现。 */
        HOLD_OPEN,
        /** 同步主板时间：设备下次轮询/推送时，服务器在响应中带回当前时间供出入口控制主板（显示屏控制板）校准 RTC。 */
        SYNC_TIME,
        QUERY
    }

    public enum Status {
        PENDING,
        DELIVERED,
        /** 入队后超过有效时限仍未被设备取走，出队时作废。 */
        EXPIRED
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

    /** 指令附加参数（JSON 字符串，可选）：落闸/常开等场景指定协议细节，如 {"io":0,"value":2,"delay":500}。 */
    @Column(length = 1024)
    private String payload;

    @Column
    private Instant deliveredAt;

    protected DeviceCommand() {
    }

    public DeviceCommand(ParkingBarrier device, Action action, String source) {
        this(device, action, source, null);
    }

    public DeviceCommand(ParkingBarrier device, Action action, String source, String payload) {
        this.device = device;
        this.action = action;
        this.source = source;
        this.payload = payload;
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

    public String getPayload() {
        return payload;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void markDelivered(Instant at) {
        this.status = Status.DELIVERED;
        this.deliveredAt = at;
    }

    /** 出队时发现已超过有效时限：作废（保留记录便于追溯，不再下发）。 */
    public void markExpired() {
        this.status = Status.EXPIRED;
    }

    public UUID getDeviceId() {
        return device.getId();
    }
}
