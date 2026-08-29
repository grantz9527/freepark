package com.freepark.local.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 设备侧网关轮询时自动发现的识别设备（未在「识别一体机对接」中登记过）。
 * 设备一旦轮询 /poll 接口即自动登记，供管理人员在对接页面查看、收录或移除。
 */
@Entity
@Table(name = "auto_registered_device")
public class AutoRegisteredDevice extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    /** 设备品牌/协议标识（如 ZHENSHI），轮询暂无法感知，由后续推送补全。 */
    @Column(length = 64)
    private String brand;

    @Column(nullable = false)
    private Instant lastPollAt;

    /** 是否已被管理人员收录（转正式对接列表）。已收录的 code 不再重复自动发现。 */
    @Column(nullable = false)
    private boolean adopted = false;

    protected AutoRegisteredDevice() {
    }

    public AutoRegisteredDevice(String code) {
        this.code = code.trim();
        this.name = this.code;
        this.lastPollAt = Instant.now();
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? code : name.trim();
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public Instant getLastPollAt() {
        return lastPollAt;
    }

    public void markPolled(Instant at) {
        this.lastPollAt = at;
    }

    public boolean isAdopted() {
        return adopted;
    }

    public void markAdopted() {
        this.adopted = true;
    }

    public void markNotAdopted() {
        this.adopted = false;
    }
}
