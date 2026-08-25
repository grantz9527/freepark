package com.freepark.local.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.freepark.local.common.i18n.SupportedLocale;
import com.freepark.local.common.i18n.SupportedTimezone;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.PlateColorSupport;
import com.freepark.local.domain.SiteSettings;
import com.freepark.local.domain.SiteSettingsRepository;

@Component
public class SiteSettingsInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SiteSettingsInitializer.class);

    private final SiteSettingsRepository settingsRepository;

    public SiteSettingsInitializer(SiteSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!settingsRepository.existsById(SiteSettings.SINGLETON_ID)) {
            SiteSettings settings = new SiteSettings(
                    SupportedLocale.ZH_CN.locale().toLanguageTag(),
                    SupportedTimezone.defaultZone().getId());
            settingsRepository.save(settings);
            log.info(
                    "Initialized default site settings (locale={}, timezone={}, defaultPlateColor={})",
                    settings.getDefaultLocale(),
                    settings.getTimezone(),
                    settings.getDefaultPlateColor());
            return;
        }

        SiteSettings settings = settingsRepository.findById(SiteSettings.SINGLETON_ID).orElseThrow();
        boolean changed = false;
        if (settings.getAllowedPlateColors().isEmpty()) {
            settings.setAllowedPlateColors(PlateColorSupport.defaultChinaAllowed());
            changed = true;
        }
        if (settings.getDefaultPlateColor() == null) {
            settings.setDefaultPlateColor(PlateColor.BLUE);
            changed = true;
        }
        if (!settings.getAllowedPlateColors().contains(settings.getDefaultPlateColor())) {
            settings.setDefaultPlateColor(settings.getAllowedPlateColors().getFirst());
            changed = true;
        }
        if (settings.getImageStoragePath() == null || settings.getImageStoragePath().isBlank()) {
            settings.setImageStoragePath(SiteSettings.DEFAULT_IMAGE_STORAGE_PATH);
            changed = true;
        }
        if (changed) {
            settingsRepository.save(settings);
            log.info("Patched site settings plate color defaults");
        }
    }
}
