package com.freepark.local.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.freepark.local.softwareplate.SoftwarePlateProvider;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "site_settings")
@EntityListeners(AuditingEntityListener.class)
public class SiteSettings {

    public static final String SINGLETON_ID = "default";
    public static final String DEFAULT_IMAGE_STORAGE_PATH = "./data/images";

    @Id
    @Column(length = 32, nullable = false, updatable = false)
    private String id = SINGLETON_ID;

    @Column(nullable = false, length = 16)
    private String defaultLocale;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_plate_color", nullable = false, length = 32)
    private PlateColor defaultPlateColor = PlateColor.BLUE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "site_settings_allowed_plate_color",
            joinColumns = @JoinColumn(name = "settings_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "plate_color", nullable = false, length = 32)
    @OrderColumn(name = "sort_order")
    private List<PlateColor> allowedPlateColors = new ArrayList<>();

    @Column(name = "image_storage_path", length = 512)
    private String imageStoragePath = DEFAULT_IMAGE_STORAGE_PATH;

    // ======== 软件车牌识别（全局开关 + provider + 各家独立参数） ========
    @Enumerated(EnumType.STRING)
    @Column(name = "software_plate_provider", length = 32)
    private SoftwarePlateProvider softwarePlateProvider = SoftwarePlateProvider.YOLO26_PLATE;

    // ======== YOLO26-Plate 软件识别（可选） ========
    @Column(name = "yolo26_plate_enabled", nullable = false)
    private boolean yolo26PlateEnabled = false;

    @Column(name = "yolo26_plate_base_url", length = 512)
    private String yolo26PlateBaseUrl = "http://127.0.0.1:8780";

    @Column(name = "yolo26_plate_min_conf")
    private Double yolo26PlateMinConf = 0.25;

    @Column(name = "yolo26_plate_connect_timeout_ms")
    private Integer yolo26PlateConnectTimeoutMs = 5_000;

    @Column(name = "yolo26_plate_read_timeout_ms")
    private Integer yolo26PlateReadTimeoutMs = 60_000;

    // ======== HyperLPR3 软件识别（可选） ========
    @Column(name = "hyperlpr3_enabled", nullable = false)
    private boolean hyperlpr3Enabled = false;

    @Column(name = "hyperlpr3_base_url", length = 512)
    private String hyperlpr3BaseUrl = "http://127.0.0.1:8715";

    @Column(name = "hyperlpr3_min_conf")
    private Double hyperlpr3MinConf = 0.6;

    @Column(name = "hyperlpr3_connect_timeout_ms")
    private Integer hyperlpr3ConnectTimeoutMs = 5_000;

    @Column(name = "hyperlpr3_read_timeout_ms")
    private Integer hyperlpr3ReadTimeoutMs = 60_000;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected SiteSettings() {
    }

    public SiteSettings(String defaultLocale, String timezone) {
        this.defaultLocale = defaultLocale;
        this.timezone = timezone;
        this.defaultPlateColor = PlateColor.BLUE;
        this.allowedPlateColors = new ArrayList<>(PlateColorSupport.defaultChinaAllowed());
        this.imageStoragePath = DEFAULT_IMAGE_STORAGE_PATH;
    }

    public String getId() {
        return id;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public PlateColor getDefaultPlateColor() {
        return defaultPlateColor;
    }

    public void setDefaultPlateColor(PlateColor defaultPlateColor) {
        this.defaultPlateColor = defaultPlateColor;
    }

    public List<PlateColor> getAllowedPlateColors() {
        return allowedPlateColors;
    }

    public void setAllowedPlateColors(List<PlateColor> allowedPlateColors) {
        this.allowedPlateColors = new ArrayList<>(allowedPlateColors);
    }

    public String getImageStoragePath() {
        return imageStoragePath;
    }

    public void setImageStoragePath(String imageStoragePath) {
        this.imageStoragePath = imageStoragePath;
    }

    public boolean isYolo26PlateEnabled() { return yolo26PlateEnabled; }

    public void setYolo26PlateEnabled(boolean yolo26PlateEnabled) { this.yolo26PlateEnabled = yolo26PlateEnabled; }

    public String getYolo26PlateBaseUrl() { return yolo26PlateBaseUrl; }

    public void setYolo26PlateBaseUrl(String yolo26PlateBaseUrl) { this.yolo26PlateBaseUrl = yolo26PlateBaseUrl; }

    public Double getYolo26PlateMinConf() { return yolo26PlateMinConf; }

    public void setYolo26PlateMinConf(Double yolo26PlateMinConf) { this.yolo26PlateMinConf = yolo26PlateMinConf; }

    public Integer getYolo26PlateConnectTimeoutMs() { return yolo26PlateConnectTimeoutMs; }

    public void setYolo26PlateConnectTimeoutMs(Integer ms) { this.yolo26PlateConnectTimeoutMs = ms; }

    public Integer getYolo26PlateReadTimeoutMs() { return yolo26PlateReadTimeoutMs; }

    public void setYolo26PlateReadTimeoutMs(Integer ms) { this.yolo26PlateReadTimeoutMs = ms; }

    public SoftwarePlateProvider getSoftwarePlateProvider() {
        return softwarePlateProvider == null ? SoftwarePlateProvider.YOLO26_PLATE : softwarePlateProvider;
    }

    public void setSoftwarePlateProvider(SoftwarePlateProvider softwarePlateProvider) {
        this.softwarePlateProvider = softwarePlateProvider == null ? SoftwarePlateProvider.YOLO26_PLATE : softwarePlateProvider;
    }

    public boolean isHyperlpr3Enabled() { return hyperlpr3Enabled; }

    public void setHyperlpr3Enabled(boolean hyperlpr3Enabled) { this.hyperlpr3Enabled = hyperlpr3Enabled; }

    public String getHyperlpr3BaseUrl() { return hyperlpr3BaseUrl; }

    public void setHyperlpr3BaseUrl(String hyperlpr3BaseUrl) { this.hyperlpr3BaseUrl = hyperlpr3BaseUrl; }

    public Double getHyperlpr3MinConf() { return hyperlpr3MinConf; }

    public void setHyperlpr3MinConf(Double hyperlpr3MinConf) { this.hyperlpr3MinConf = hyperlpr3MinConf; }

    public Integer getHyperlpr3ConnectTimeoutMs() { return hyperlpr3ConnectTimeoutMs; }

    public void setHyperlpr3ConnectTimeoutMs(Integer ms) { this.hyperlpr3ConnectTimeoutMs = ms; }

    public Integer getHyperlpr3ReadTimeoutMs() { return hyperlpr3ReadTimeoutMs; }

    public void setHyperlpr3ReadTimeoutMs(Integer ms) { this.hyperlpr3ReadTimeoutMs = ms; }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
