package com.freepark.local.sitesettings;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    private SystemSettingsView toView(SiteSettings settings) {
        ensurePlateColorDefaults(settings);
        ensureImageStoragePath(settings);
        return new SystemSettingsView(
                settings.getDefaultLocale(),
                settings.getTimezone(),
                settings.getDefaultPlateColor(),
                List.copyOf(settings.getAllowedPlateColors()),
                settings.getImageStoragePath(),
                SupportedLocale.languageTags(),
                SupportedTimezone.all(),
                PlateColorSupport.all(),
                settings.getUpdatedAt());
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
