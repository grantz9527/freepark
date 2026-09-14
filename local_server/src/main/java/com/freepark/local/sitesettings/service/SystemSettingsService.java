package com.freepark.local.sitesettings.service;

import com.freepark.local.sitesettings.dto.CloudStorageSettings;
import com.freepark.local.sitesettings.dto.SystemSettingsView;
import com.freepark.local.sitesettings.dto.UpdateSystemSettingsRequest;
import com.freepark.local.sitesettings.dto.Yolo26PlateSettings;
import com.freepark.local.softwareplate.SoftwarePlateProvider;
import com.freepark.local.softwareplate.dto.HyperLpr3Settings;
import com.freepark.local.storage.CloudObjectKeys;
import com.freepark.local.storage.CloudStorageProvider;
import com.freepark.local.storage.CloudUploadTarget;
import com.freepark.local.storage.ImageCompressor;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.common.i18n.SupportedLocale;
import com.freepark.local.common.i18n.SupportedTimezone;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.PlateColorSupport;
import com.freepark.local.domain.SiteSettings;
import com.freepark.local.domain.SiteSettingsRepository;
import com.freepark.local.domain.UserRole;

@Service
public class SystemSettingsService {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SiteSettingsRepository settingsRepository;
    private final LocalUserRepository users;

    public SystemSettingsService(SiteSettingsRepository settingsRepository, LocalUserRepository users) {
        this.settingsRepository = settingsRepository;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public SystemSettingsView getSettings() {
        return toView(requireSettings());
    }

    @Transactional
    public SystemSettingsView updateSettings(UUID requesterId, UpdateSystemSettingsRequest request) {
        requireAdmin(requesterId);
        SiteSettings settings = requireSettings();
        settings.setDefaultLocale(SupportedLocale.validateLanguageTag(request.defaultLocale()));
        settings.setTimezone(SupportedTimezone.resolve(request.timezone()).getId());
        List<PlateColor> allowed = PlateColorSupport.normalizeAllowed(request.allowedPlateColors());
        PlateColor defaultPlateColor = request.defaultPlateColor();
        if (!allowed.contains(defaultPlateColor)) {
            throw new BusinessException(ErrorCode.INVALID_PLATE_COLOR_CONFIG);
        }
        settings.setAllowedPlateColors(allowed);
        settings.setDefaultPlateColor(defaultPlateColor);
        settings.setImageStoragePath(normalizeImageStoragePath(request.imageStoragePath()));
        if (request.imageStorageEnabled() != null) {
            settings.setImageStorageEnabled(request.imageStorageEnabled());
        }
        settings.setSoftwarePlateProvider(request.softwarePlateProvider() == null
                ? SoftwarePlateProvider.YOLO26_PLATE : request.softwarePlateProvider());
        applyYolo26(settings, request.yolo26Plate());
        applyHyperLpr3(settings, request.hyperLpr3());
        applyCloudStorage(settings, request.cloudStorage());
        // 保证同一时刻最多只有当前选中的 provider 被启用
        enforceSingleSoftwarePlateEnabled(settings);
        return toView(settingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public Locale getDefaultLocale() {
        return SupportedLocale.resolve(requireSettings().getDefaultLocale());
    }

    @Transactional(readOnly = true)
    public ZoneId getTimezone() {
        return SupportedTimezone.resolve(requireSettings().getTimezone());
    }

    @Transactional(readOnly = true)
    public PlateColor getDefaultPlateColor() {
        return requireSettings().getDefaultPlateColor();
    }

    @Transactional(readOnly = true)
    public List<PlateColor> getAllowedPlateColors() {
        return List.copyOf(requireSettings().getAllowedPlateColors());
    }

    @Transactional(readOnly = true)
    public String getImageStoragePath() {
        return normalizeImageStoragePath(requireSettings().getImageStoragePath());
    }

    @Transactional(readOnly = true)
    public boolean isImageStorageEnabled() {
        return requireSettings().isImageStorageEnabled();
    }

    @Transactional(readOnly = true)
    public boolean isCloudStorageEnabled() {
        return requireSettings().isCloudStorageEnabled();
    }

    /** 当前生效的云上传凭据；未开启或配置不完整时返回 null。 */
    @Transactional(readOnly = true)
    public CloudUploadTarget cloudUploadTarget() {
        SiteSettings settings = requireSettings();
        if (!settings.isCloudStorageEnabled()) {
            return null;
        }
        return switch (settings.getCloudStorageProvider()) {
            case ALIYUN_OSS -> aliyunTarget(settings);
            case HUAWEI_OBS -> huaweiTarget(settings);
            case TENCENT_COS -> tencentTarget(settings);
        };
    }

    @Transactional(readOnly = true)
    public void ensurePlateColorAllowed(PlateColor plateColor) {
        PlateColorSupport.ensureAllowed(plateColor, requireSettings().getAllowedPlateColors());
    }

    @Transactional(readOnly = true)
    public String formatInstant(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DISPLAY_TIME.withZone(getTimezone()).format(instant);
    }

    private SiteSettings requireSettings() {
        SiteSettings settings = settingsRepository.findById(SiteSettings.SINGLETON_ID)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ensurePlateColorDefaults(settings);
        ensureImageStoragePath(settings);
        return settings;
    }

    private void ensurePlateColorDefaults(SiteSettings settings) {
        if (settings.getAllowedPlateColors().isEmpty()) {
            settings.setAllowedPlateColors(PlateColorSupport.defaultChinaAllowed());
        }
        if (settings.getDefaultPlateColor() == null) {
            settings.setDefaultPlateColor(PlateColor.BLUE);
        }
        if (!settings.getAllowedPlateColors().contains(settings.getDefaultPlateColor())) {
            settings.setDefaultPlateColor(settings.getAllowedPlateColors().getFirst());
        }
    }

    private void ensureImageStoragePath(SiteSettings settings) {
        if (settings.getImageStoragePath() == null || settings.getImageStoragePath().isBlank()) {
            settings.setImageStoragePath(SiteSettings.DEFAULT_IMAGE_STORAGE_PATH);
        }
    }

    private String normalizeImageStoragePath(String path) {
        String trimmed = path == null ? "" : path.trim();
        return trimmed.isBlank() ? SiteSettings.DEFAULT_IMAGE_STORAGE_PATH : trimmed;
    }

    // ---------------- Yolo26-Plate ----------------

    public static final String DEFAULT_YOLO26_BASE_URL = "http://127.0.0.1:8780";
    public static final double DEFAULT_YOLO26_MIN_CONF = 0.25;
    public static final int DEFAULT_YOLO26_CONNECT_TIMEOUT_MS = 5_000;
    public static final int DEFAULT_YOLO26_READ_TIMEOUT_MS = 60_000;

    @Transactional(readOnly = true)
    public Yolo26PlateSettings getYolo26PlateSettings() {
        SiteSettings s = requireSettings();
        ensureYolo26Defaults(s);
        return new Yolo26PlateSettings(
                s.isYolo26PlateEnabled(),
                s.getYolo26PlateBaseUrl(),
                s.getYolo26PlateMinConf(),
                s.getYolo26PlateConnectTimeoutMs(),
                s.getYolo26PlateReadTimeoutMs());
    }

    @Transactional(readOnly = true)
    public HyperLpr3Settings getHyperLpr3Settings() {
        SiteSettings s = requireSettings();
        ensureHyperLpr3Defaults(s);
        return new HyperLpr3Settings(
                s.isHyperlpr3Enabled(),
                s.getHyperlpr3BaseUrl(),
                s.getHyperlpr3MinConf(),
                s.getHyperlpr3ConnectTimeoutMs(),
                s.getHyperlpr3ReadTimeoutMs());
    }

    /**
     * 当前选中的 provider 对应的 client 是否启用。
     */
    @Transactional(readOnly = true)
    public boolean isSoftwarePlateEnabledForCurrentProvider() {
        SiteSettings s = requireSettings();
        return switch (s.getSoftwarePlateProvider()) {
            case YOLO26_PLATE -> s.isYolo26PlateEnabled();
            case HYPER_LPR3 -> s.isHyperlpr3Enabled();
        };
    }

    @Transactional(readOnly = true)
    public SoftwarePlateProvider getSoftwarePlateProvider() {
        return requireSettings().getSoftwarePlateProvider();
    }

    private void applyYolo26(SiteSettings settings, UpdateSystemSettingsRequest.Yolo26Update update) {
        boolean enabled = update.enabled();
        String baseUrl = normalizeBaseUrl(update.baseUrl(), enabled, DEFAULT_YOLO26_BASE_URL,
                ErrorCode.INVALID_YOLO26_PLATE_CONFIG);
        if (enabled && (baseUrl == null || baseUrl.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_YOLO26_PLATE_CONFIG, "baseUrl");
        }
        double minConf = clamp(update.minConfidence() == null ? DEFAULT_YOLO26_MIN_CONF : update.minConfidence(), 0.0, 1.0);
        int connect = clampRange(update.connectTimeoutMs(), DEFAULT_YOLO26_CONNECT_TIMEOUT_MS, 500, 600_000);
        int read = clampRange(update.readTimeoutMs(), DEFAULT_YOLO26_READ_TIMEOUT_MS, 1_000, 600_000);
        settings.setYolo26PlateEnabled(enabled);
        settings.setYolo26PlateBaseUrl(baseUrl);
        settings.setYolo26PlateMinConf(minConf);
        settings.setYolo26PlateConnectTimeoutMs(connect);
        settings.setYolo26PlateReadTimeoutMs(read);
    }

    // ---------------- HyperLPR3 ----------------

    public static final String DEFAULT_HYPER_LPR3_BASE_URL = "http://127.0.0.1:8715";
    public static final double DEFAULT_HYPER_LPR3_MIN_CONF = 0.6;
    public static final int DEFAULT_HYPER_LPR3_CONNECT_TIMEOUT_MS = 5_000;
    public static final int DEFAULT_HYPER_LPR3_READ_TIMEOUT_MS = 60_000;

    private void applyHyperLpr3(SiteSettings settings, UpdateSystemSettingsRequest.HyperLpr3Update update) {
        boolean enabled = update.enabled();
        String baseUrl = normalizeBaseUrl(update.baseUrl(), enabled, DEFAULT_HYPER_LPR3_BASE_URL,
                ErrorCode.INVALID_HYPER_LPR3_CONFIG);
        if (enabled && (baseUrl == null || baseUrl.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_HYPER_LPR3_CONFIG, "baseUrl");
        }
        double minConf = clamp(update.minConfidence() == null ? DEFAULT_HYPER_LPR3_MIN_CONF : update.minConfidence(), 0.0, 1.0);
        int connect = clampRange(update.connectTimeoutMs(), DEFAULT_HYPER_LPR3_CONNECT_TIMEOUT_MS, 500, 600_000);
        int read = clampRange(update.readTimeoutMs(), DEFAULT_HYPER_LPR3_READ_TIMEOUT_MS, 1_000, 600_000);
        settings.setHyperlpr3Enabled(enabled);
        settings.setHyperlpr3BaseUrl(baseUrl);
        settings.setHyperlpr3MinConf(minConf);
        settings.setHyperlpr3ConnectTimeoutMs(connect);
        settings.setHyperlpr3ReadTimeoutMs(read);
    }

    private static String normalizeBaseUrl(String raw, boolean enabled, String fallback, ErrorCode schemeErrorCode) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return enabled ? null : fallback;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new BusinessException(schemeErrorCode, "baseUrl.scheme");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessException(schemeErrorCode, "baseUrl.host");
            }
        } catch (IllegalArgumentException iae) {
            throw new BusinessException(schemeErrorCode, "baseUrl.invalid");
        }
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int clampRange(Integer v, int fallback, int lo, int hi) {
        int n = v == null ? fallback : v;
        return (int) Math.max(lo, Math.min(hi, n));
    }

    private void ensureYolo26Defaults(SiteSettings s) {
        if (!s.isYolo26PlateEnabled()) {
            s.setYolo26PlateEnabled(false);
        }
        if (s.getYolo26PlateBaseUrl() == null || s.getYolo26PlateBaseUrl().isBlank()) {
            s.setYolo26PlateBaseUrl(DEFAULT_YOLO26_BASE_URL);
        }
        if (s.getYolo26PlateMinConf() == null || s.getYolo26PlateMinConf() < 0 || s.getYolo26PlateMinConf() > 1) {
            s.setYolo26PlateMinConf(DEFAULT_YOLO26_MIN_CONF);
        }
        if (s.getYolo26PlateConnectTimeoutMs() == null) {
            s.setYolo26PlateConnectTimeoutMs(DEFAULT_YOLO26_CONNECT_TIMEOUT_MS);
        }
        if (s.getYolo26PlateReadTimeoutMs() == null) {
            s.setYolo26PlateReadTimeoutMs(DEFAULT_YOLO26_READ_TIMEOUT_MS);
        }
    }

    private void ensureHyperLpr3Defaults(SiteSettings s) {
        if (s.getHyperlpr3BaseUrl() == null || s.getHyperlpr3BaseUrl().isBlank()) {
            s.setHyperlpr3BaseUrl(DEFAULT_HYPER_LPR3_BASE_URL);
        }
        if (s.getHyperlpr3MinConf() == null || s.getHyperlpr3MinConf() < 0 || s.getHyperlpr3MinConf() > 1) {
            s.setHyperlpr3MinConf(DEFAULT_HYPER_LPR3_MIN_CONF);
        }
        if (s.getHyperlpr3ConnectTimeoutMs() == null) {
            s.setHyperlpr3ConnectTimeoutMs(DEFAULT_HYPER_LPR3_CONNECT_TIMEOUT_MS);
        }
        if (s.getHyperlpr3ReadTimeoutMs() == null) {
            s.setHyperlpr3ReadTimeoutMs(DEFAULT_HYPER_LPR3_READ_TIMEOUT_MS);
        }
    }

    private SystemSettingsView toView(SiteSettings settings) {
        ensurePlateColorDefaults(settings);
        ensureImageStoragePath(settings);
        ensureYolo26Defaults(settings);
        ensureHyperLpr3Defaults(settings);
        if (settings.getSoftwarePlateProvider() == null) {
            settings.setSoftwarePlateProvider(SoftwarePlateProvider.YOLO26_PLATE);
        }
        return new SystemSettingsView(
                settings.getDefaultLocale(),
                settings.getTimezone(),
                settings.getDefaultPlateColor(),
                List.copyOf(settings.getAllowedPlateColors()),
                settings.getImageStoragePath(),
                settings.isImageStorageEnabled(),
                settings.getSoftwarePlateProvider(),
                new Yolo26PlateSettings(
                        settings.isYolo26PlateEnabled(),
                        settings.getYolo26PlateBaseUrl(),
                        settings.getYolo26PlateMinConf(),
                        settings.getYolo26PlateConnectTimeoutMs(),
                        settings.getYolo26PlateReadTimeoutMs()),
                new HyperLpr3Settings(
                        settings.isHyperlpr3Enabled(),
                        settings.getHyperlpr3BaseUrl(),
                        settings.getHyperlpr3MinConf(),
                        settings.getHyperlpr3ConnectTimeoutMs(),
                        settings.getHyperlpr3ReadTimeoutMs()),
                toCloudView(settings),
                SupportedLocale.languageTags(),
                SupportedTimezone.all(),
                PlateColorSupport.all(),
                Arrays.asList(SoftwarePlateProvider.values()),
                settings.getUpdatedAt());
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 强制只允许当前 softwarePlateProvider 对应的启用开关为 true，
     * 其它 provider 一律强制关闭，避免同一时刻两个引擎都处于启用态。
     */
    private void enforceSingleSoftwarePlateEnabled(SiteSettings settings) {
        SoftwarePlateProvider current = settings.getSoftwarePlateProvider() == null
                ? SoftwarePlateProvider.YOLO26_PLATE : settings.getSoftwarePlateProvider();
        if (current != SoftwarePlateProvider.YOLO26_PLATE) {
            settings.setYolo26PlateEnabled(false);
        }
        if (current != SoftwarePlateProvider.HYPER_LPR3) {
            settings.setHyperlpr3Enabled(false);
        }
    }

    private void applyCloudStorage(SiteSettings settings, UpdateSystemSettingsRequest.CloudStorageUpdate update) {
        if (update == null) {
            return;
        }
        CloudStorageProvider provider = update.provider() == null
                ? CloudStorageProvider.ALIYUN_OSS
                : update.provider();
        settings.setCloudStorageProvider(provider);
        applyAliyun(settings, update.aliyun());
        applyHuawei(settings, update.huawei());
        applyTencent(settings, update.tencent());
        settings.setCloudStorageMaxImageKb(normalizeMaxImageKb(update.maxImageKb()));
        if (update.enabled()) {
            requireCloudProviderReady(settings, provider);
            settings.setCloudStorageEnabled(true);
        } else {
            settings.setCloudStorageEnabled(false);
        }
    }

    private void applyAliyun(SiteSettings settings, UpdateSystemSettingsRequest.AliyunOssUpdate update) {
        if (update == null) {
            return;
        }
        settings.setAliyunOssEndpoint(trimToNull(update.endpoint()));
        settings.setAliyunOssAccessKeyId(trimToNull(update.accessKeyId()));
        settings.setAliyunOssAccessKeySecret(
                keepOrUpdateSecret(update.accessKeySecret(), settings.getAliyunOssAccessKeySecret()));
        settings.setAliyunOssBucket(trimToNull(update.bucket()));
        settings.setAliyunOssPathPrefix(trimToEmpty(update.pathPrefix()));
        settings.setAliyunOssCustomDomain(trimToEmpty(update.customDomain()));
    }

    private void applyHuawei(SiteSettings settings, UpdateSystemSettingsRequest.HuaweiObsUpdate update) {
        if (update == null) {
            return;
        }
        settings.setHuaweiObsEndpoint(trimToNull(update.endpoint()));
        settings.setHuaweiObsAccessKey(trimToNull(update.accessKey()));
        settings.setHuaweiObsSecretKey(keepOrUpdateSecret(update.secretKey(), settings.getHuaweiObsSecretKey()));
        settings.setHuaweiObsBucket(trimToNull(update.bucket()));
        settings.setHuaweiObsPathPrefix(trimToEmpty(update.pathPrefix()));
        settings.setHuaweiObsCustomDomain(trimToEmpty(update.customDomain()));
    }

    private void applyTencent(SiteSettings settings, UpdateSystemSettingsRequest.TencentCosUpdate update) {
        if (update == null) {
            return;
        }
        settings.setTencentCosRegion(trimToNull(update.region()));
        settings.setTencentCosSecretId(trimToNull(update.secretId()));
        settings.setTencentCosSecretKey(keepOrUpdateSecret(update.secretKey(), settings.getTencentCosSecretKey()));
        settings.setTencentCosBucket(trimToNull(update.bucket()));
        settings.setTencentCosPathPrefix(trimToEmpty(update.pathPrefix()));
        settings.setTencentCosCustomDomain(trimToEmpty(update.customDomain()));
    }

    private static CloudUploadTarget aliyunTarget(SiteSettings settings) {
        if (isBlank(settings.getAliyunOssEndpoint())
                || isBlank(settings.getAliyunOssAccessKeyId())
                || isBlank(settings.getAliyunOssAccessKeySecret())
                || isBlank(settings.getAliyunOssBucket())) {
            return null;
        }
        String host = CloudObjectKeys.hostWithoutScheme(settings.getAliyunOssEndpoint());
        return new CloudUploadTarget(
                CloudStorageProvider.ALIYUN_OSS,
                host,
                CloudObjectKeys.regionFromOssEndpoint(host),
                settings.getAliyunOssAccessKeyId(),
                settings.getAliyunOssAccessKeySecret(),
                settings.getAliyunOssBucket().trim(),
                nullToEmpty(settings.getAliyunOssPathPrefix()),
                nullToEmpty(settings.getAliyunOssCustomDomain()),
                settings.getCloudStorageMaxImageKb() * 1024);
    }

    private static CloudUploadTarget huaweiTarget(SiteSettings settings) {
        if (isBlank(settings.getHuaweiObsEndpoint())
                || isBlank(settings.getHuaweiObsAccessKey())
                || isBlank(settings.getHuaweiObsSecretKey())
                || isBlank(settings.getHuaweiObsBucket())) {
            return null;
        }
        String host = CloudObjectKeys.hostWithoutScheme(settings.getHuaweiObsEndpoint());
        return new CloudUploadTarget(
                CloudStorageProvider.HUAWEI_OBS,
                host,
                CloudObjectKeys.regionFromHuaweiEndpoint(host),
                settings.getHuaweiObsAccessKey(),
                settings.getHuaweiObsSecretKey(),
                settings.getHuaweiObsBucket().trim(),
                nullToEmpty(settings.getHuaweiObsPathPrefix()),
                nullToEmpty(settings.getHuaweiObsCustomDomain()),
                settings.getCloudStorageMaxImageKb() * 1024);
    }

    private static CloudUploadTarget tencentTarget(SiteSettings settings) {
        if (isBlank(settings.getTencentCosRegion())
                || isBlank(settings.getTencentCosSecretId())
                || isBlank(settings.getTencentCosSecretKey())
                || isBlank(settings.getTencentCosBucket())) {
            return null;
        }
        String region = settings.getTencentCosRegion().trim();
        return new CloudUploadTarget(
                CloudStorageProvider.TENCENT_COS,
                "cos." + region + ".myqcloud.com",
                region,
                settings.getTencentCosSecretId(),
                settings.getTencentCosSecretKey(),
                settings.getTencentCosBucket().trim(),
                nullToEmpty(settings.getTencentCosPathPrefix()),
                nullToEmpty(settings.getTencentCosCustomDomain()),
                settings.getCloudStorageMaxImageKb() * 1024);
    }

    private void requireCloudProviderReady(SiteSettings settings, CloudStorageProvider provider) {
        switch (provider) {
            case ALIYUN_OSS -> {
                requireCloudField(settings.getAliyunOssEndpoint(), "aliyun.endpoint");
                requireCloudField(settings.getAliyunOssAccessKeyId(), "aliyun.accessKeyId");
                requireCloudField(settings.getAliyunOssAccessKeySecret(), "aliyun.accessKeySecret");
                requireCloudField(settings.getAliyunOssBucket(), "aliyun.bucket");
            }
            case HUAWEI_OBS -> {
                requireCloudField(settings.getHuaweiObsEndpoint(), "huawei.endpoint");
                requireCloudField(settings.getHuaweiObsAccessKey(), "huawei.accessKey");
                requireCloudField(settings.getHuaweiObsSecretKey(), "huawei.secretKey");
                requireCloudField(settings.getHuaweiObsBucket(), "huawei.bucket");
            }
            case TENCENT_COS -> {
                requireCloudField(settings.getTencentCosRegion(), "tencent.region");
                requireCloudField(settings.getTencentCosSecretId(), "tencent.secretId");
                requireCloudField(settings.getTencentCosSecretKey(), "tencent.secretKey");
                requireCloudField(settings.getTencentCosBucket(), "tencent.bucket");
            }
        }
    }

    private static int normalizeMaxImageKb(Integer kb) {
        if (kb == null) {
            return ImageCompressor.DEFAULT_MAX_KB;
        }
        if (kb < ImageCompressor.MIN_KB || kb > ImageCompressor.MAX_KB) {
            throw new BusinessException(ErrorCode.INVALID_CLOUD_STORAGE_CONFIG, "maxImageKb");
        }
        return kb;
    }

    private static void requireCloudField(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_CLOUD_STORAGE_CONFIG, field);
        }
    }

    private CloudStorageSettings toCloudView(SiteSettings settings) {
        return new CloudStorageSettings(
                settings.isCloudStorageEnabled(),
                settings.getCloudStorageProvider(),
                settings.getCloudStorageMaxImageKb(),
                new CloudStorageSettings.AliyunOssSettings(
                        nullToEmpty(settings.getAliyunOssEndpoint()),
                        nullToEmpty(settings.getAliyunOssAccessKeyId()),
                        !isBlank(settings.getAliyunOssAccessKeySecret()),
                        nullToEmpty(settings.getAliyunOssBucket()),
                        nullToEmpty(settings.getAliyunOssPathPrefix()),
                        nullToEmpty(settings.getAliyunOssCustomDomain())),
                new CloudStorageSettings.HuaweiObsSettings(
                        nullToEmpty(settings.getHuaweiObsEndpoint()),
                        nullToEmpty(settings.getHuaweiObsAccessKey()),
                        !isBlank(settings.getHuaweiObsSecretKey()),
                        nullToEmpty(settings.getHuaweiObsBucket()),
                        nullToEmpty(settings.getHuaweiObsPathPrefix()),
                        nullToEmpty(settings.getHuaweiObsCustomDomain())),
                new CloudStorageSettings.TencentCosSettings(
                        nullToEmpty(settings.getTencentCosRegion()),
                        nullToEmpty(settings.getTencentCosSecretId()),
                        !isBlank(settings.getTencentCosSecretKey()),
                        nullToEmpty(settings.getTencentCosBucket()),
                        nullToEmpty(settings.getTencentCosPathPrefix()),
                        nullToEmpty(settings.getTencentCosCustomDomain())));
    }

    private static String keepOrUpdateSecret(String incoming, String existing) {
        String trimmed = incoming == null ? "" : incoming.trim();
        return trimmed.isEmpty() ? existing : trimmed;
    }

    private static String trimToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
