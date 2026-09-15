package com.freepark.local.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "parking_barrier",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lane_id", "code"}))
public class ParkingBarrier extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "lane_id", nullable = true)
    private ParkingLane lane;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 设备品牌/协议标识，如 ZHENSHI，用于网关按协议适配上报与指令。 */
    @Column(length = 64)
    private String brand;

    /** 品牌下的具体设备型号（如 YELLOW_CARD_LED_LINE4），用于驱动按型号路由；空表示整条产品线通配。 */
    @Column(length = 64)
    private String model;

    /** 设备 IP，平台主动下发开闸等 HTTP 命令时使用（默认 80 端口）。 */
    @Column(length = 64)
    private String host;

    /** 已废弃：命令端口不再从档案读取，HTTP 默认 80。保留列以免旧库升级失败。 */
    @Column
    private Integer port;

    /** 完整视频流地址（RTSP/HTTP 等），各厂商路径不同，由现场直接填写。 */
    @Column(name = "stream_url", length = 512)
    private String streamUrl;

    /** 最近一次轮询时间戳，用于推导设备在线状态。 */
    @Column
    private Instant lastPollAt;

    protected ParkingBarrier() {
    }

    public ParkingBarrier(ParkingLane lane, String name, String code, boolean enabled) {
        this.lane = lane;
        this.name = name.trim();
        this.code = code.trim();
        this.enabled = enabled;
    }

    public ParkingLane getLane() {
        return lane;
    }

    public void setLane(ParkingLane lane) {
        this.lane = lane;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    /** 更新一体机命令通道连接参数（可为空，表示未配置驱动通道）。 */
    public void setConnection(String host, Integer port) {
        this.host = host;
        this.port = port;
    }

    public void setStreamUrl(String streamUrl) {
        String trimmed = streamUrl == null ? "" : streamUrl.trim();
        this.streamUrl = trimmed.isEmpty() ? null : trimmed;
    }

    public void setConnection(String host, Integer port, String brand) {
        this.host = host;
        this.port = port;
        this.brand = brand;
    }

    public Instant getLastPollAt() {
        return lastPollAt;
    }

    public void markPolled(Instant at) {
        this.lastPollAt = at;
    }

    public void updateDetails(String name, boolean enabled) {
        this.name = name.trim();
        this.enabled = enabled;
    }
}
