package com.freepark.local.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.freepark.local.softwareplate.SoftwarePlateProvider;
import com.freepark.local.storage.CloudStorageProvider;

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

    /** null 视为开启，兼容升级前未写入该列的数据。 */
    @Column(name = "image_storage_enabled")
    private Boolean imageStorageEnabled;

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

    @Column(name = "cloud_storage_enabled", nullable = false)
    private boolean cloudStorageEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "cloud_storage_provider", length = 32)
    private CloudStorageProvider cloudStorageProvider = CloudStorageProvider.ALIYUN_OSS;

    @Column(name = "aliyun_oss_endpoint", length = 256)
    private String aliyunOssEndpoint;

    @Column(name = "aliyun_oss_access_key_id", length = 128)
    private String aliyunOssAccessKeyId;

    @Column(name = "aliyun_oss_access_key_secret", length = 256)
    private String aliyunOssAccessKeySecret;

    @Column(name = "aliyun_oss_bucket", length = 128)
    private String aliyunOssBucket;

    @Column(name = "aliyun_oss_path_prefix", length = 256)
    private String aliyunOssPathPrefix;

    @Column(name = "aliyun_oss_custom_domain", length = 256)
    private String aliyunOssCustomDomain;

    @Column(name = "huawei_obs_endpoint", length = 256)
    private String huaweiObsEndpoint;

    @Column(name = "huawei_obs_access_key", length = 128)
    private String huaweiObsAccessKey;

    @Column(name = "huawei_obs_secret_key", length = 256)
    private String huaweiObsSecretKey;

    @Column(name = "huawei_obs_bucket", length = 128)
    private String huaweiObsBucket;

    @Column(name = "huawei_obs_path_prefix", length = 256)
    private String huaweiObsPathPrefix;

    @Column(name = "huawei_obs_custom_domain", length = 256)
    private String huaweiObsCustomDomain;

    @Column(name = "tencent_cos_region", length = 64)
    private String tencentCosRegion;

    @Column(name = "tencent_cos_secret_id", length = 128)
    private String tencentCosSecretId;

    @Column(name = "tencent_cos_secret_key", length = 256)
    private String tencentCosSecretKey;

    @Column(name = "tencent_cos_bucket", length = 128)
    private String tencentCosBucket;

    @Column(name = "tencent_cos_path_prefix", length = 256)
    private String tencentCosPathPrefix;

    @Column(name = "tencent_cos_custom_domain", length = 256)
    private String tencentCosCustomDomain;

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

    public boolean isImageStorageEnabled() {
        return imageStorageEnabled == null || Boolean.TRUE.equals(imageStorageEnabled);
    }

    public void setImageStorageEnabled(boolean imageStorageEnabled) {
        this.imageStorageEnabled = imageStorageEnabled;
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

    public boolean isCloudStorageEnabled() { return cloudStorageEnabled; }

    public void setCloudStorageEnabled(boolean cloudStorageEnabled) { this.cloudStorageEnabled = cloudStorageEnabled; }

    public CloudStorageProvider getCloudStorageProvider() {
        return cloudStorageProvider == null ? CloudStorageProvider.ALIYUN_OSS : cloudStorageProvider;
    }

    public void setCloudStorageProvider(CloudStorageProvider cloudStorageProvider) {
        this.cloudStorageProvider = cloudStorageProvider == null ? CloudStorageProvider.ALIYUN_OSS : cloudStorageProvider;
    }

    public String getAliyunOssEndpoint() { return aliyunOssEndpoint; }
    public void setAliyunOssEndpoint(String aliyunOssEndpoint) { this.aliyunOssEndpoint = aliyunOssEndpoint; }
    public String getAliyunOssAccessKeyId() { return aliyunOssAccessKeyId; }
    public void setAliyunOssAccessKeyId(String aliyunOssAccessKeyId) { this.aliyunOssAccessKeyId = aliyunOssAccessKeyId; }
    public String getAliyunOssAccessKeySecret() { return aliyunOssAccessKeySecret; }
    public void setAliyunOssAccessKeySecret(String aliyunOssAccessKeySecret) { this.aliyunOssAccessKeySecret = aliyunOssAccessKeySecret; }
    public String getAliyunOssBucket() { return aliyunOssBucket; }
    public void setAliyunOssBucket(String aliyunOssBucket) { this.aliyunOssBucket = aliyunOssBucket; }
    public String getAliyunOssPathPrefix() { return aliyunOssPathPrefix; }
    public void setAliyunOssPathPrefix(String aliyunOssPathPrefix) { this.aliyunOssPathPrefix = aliyunOssPathPrefix; }
    public String getAliyunOssCustomDomain() { return aliyunOssCustomDomain; }
    public void setAliyunOssCustomDomain(String aliyunOssCustomDomain) { this.aliyunOssCustomDomain = aliyunOssCustomDomain; }

    public String getHuaweiObsEndpoint() { return huaweiObsEndpoint; }
    public void setHuaweiObsEndpoint(String huaweiObsEndpoint) { this.huaweiObsEndpoint = huaweiObsEndpoint; }
    public String getHuaweiObsAccessKey() { return huaweiObsAccessKey; }
    public void setHuaweiObsAccessKey(String huaweiObsAccessKey) { this.huaweiObsAccessKey = huaweiObsAccessKey; }
    public String getHuaweiObsSecretKey() { return huaweiObsSecretKey; }
    public void setHuaweiObsSecretKey(String huaweiObsSecretKey) { this.huaweiObsSecretKey = huaweiObsSecretKey; }
    public String getHuaweiObsBucket() { return huaweiObsBucket; }
    public void setHuaweiObsBucket(String huaweiObsBucket) { this.huaweiObsBucket = huaweiObsBucket; }
    public String getHuaweiObsPathPrefix() { return huaweiObsPathPrefix; }
    public void setHuaweiObsPathPrefix(String huaweiObsPathPrefix) { this.huaweiObsPathPrefix = huaweiObsPathPrefix; }
    public String getHuaweiObsCustomDomain() { return huaweiObsCustomDomain; }
    public void setHuaweiObsCustomDomain(String huaweiObsCustomDomain) { this.huaweiObsCustomDomain = huaweiObsCustomDomain; }

    public String getTencentCosRegion() { return tencentCosRegion; }
    public void setTencentCosRegion(String tencentCosRegion) { this.tencentCosRegion = tencentCosRegion; }
    public String getTencentCosSecretId() { return tencentCosSecretId; }
    public void setTencentCosSecretId(String tencentCosSecretId) { this.tencentCosSecretId = tencentCosSecretId; }
    public String getTencentCosSecretKey() { return tencentCosSecretKey; }
    public void setTencentCosSecretKey(String tencentCosSecretKey) { this.tencentCosSecretKey = tencentCosSecretKey; }
    public String getTencentCosBucket() { return tencentCosBucket; }
    public void setTencentCosBucket(String tencentCosBucket) { this.tencentCosBucket = tencentCosBucket; }
    public String getTencentCosPathPrefix() { return tencentCosPathPrefix; }
    public void setTencentCosPathPrefix(String tencentCosPathPrefix) { this.tencentCosPathPrefix = tencentCosPathPrefix; }
    public String getTencentCosCustomDomain() { return tencentCosCustomDomain; }
    public void setTencentCosCustomDomain(String tencentCosCustomDomain) { this.tencentCosCustomDomain = tencentCosCustomDomain; }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
