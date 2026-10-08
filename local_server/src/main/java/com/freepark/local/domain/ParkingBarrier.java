package com.freepark.local.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** 显示屏安装方向。行数由驱动按型号上报，不在档案里改。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "screen_orientation", length = 16)
    private ScreenOrientation screenOrientation;

    /** 空闲时显示屏第一行。过车提示会临时盖住，不会改掉这里。 */
    @Column(name = "screen_line1", length = 32)
    private String screenLine1;

    /** 空闲时显示屏第二行。 */
    @Column(name = "screen_line2", length = 32)
    private String screenLine2;

    /** 默认屏显每轮停留秒数，对应控制板 0x6E 的 DT（0–255）。 */
    @Column(name = "screen_stay_seconds")
    private Integer screenStaySeconds;

    /** 第一行播放方式，对应 0x6E 的 DM。空表示立即显示。 */
    @Column(name = "screen_play_mode1")
    private Integer screenPlayMode1;

    /** 第二行播放方式，对应 0x6E 的 DM。空表示立即显示。 */
    @Column(name = "screen_play_mode2")
    private Integer screenPlayMode2;

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

    public ScreenOrientation getScreenOrientation() {
        return screenOrientation;
    }

    public void setScreenOrientation(ScreenOrientation screenOrientation) {
        this.screenOrientation = screenOrientation;
    }

    public String getScreenLine1() {
        return screenLine1;
    }

    public void setScreenLine1(String screenLine1) {
        this.screenLine1 = screenLine1;
    }

    public String getScreenLine2() {
        return screenLine2;
    }

    public void setScreenLine2(String screenLine2) {
        this.screenLine2 = screenLine2;
    }

    public Integer getScreenStaySeconds() {
        return screenStaySeconds;
    }

    public void setScreenStaySeconds(Integer screenStaySeconds) {
        this.screenStaySeconds = screenStaySeconds;
    }

    public Integer getScreenPlayMode1() {
        return screenPlayMode1;
    }

    public void setScreenPlayMode1(Integer screenPlayMode1) {
        this.screenPlayMode1 = screenPlayMode1;
    }

    public Integer getScreenPlayMode2() {
        return screenPlayMode2;
    }

    public void setScreenPlayMode2(Integer screenPlayMode2) {
        this.screenPlayMode2 = screenPlayMode2;
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
