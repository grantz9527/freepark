package com.freepark.local.sitesettings;

import java.time.Instant;
import java.util.List;

import com.freepark.local.domain.PlateColor;

public record SystemSettingsView(
        String defaultLocale,
        String timezone,
        PlateColor defaultPlateColor,
        List<PlateColor> allowedPlateColors,
        List<String> supportedLocales,
        List<String> supportedTimezones,
        List<PlateColor> supportedPlateColors,
        Instant updatedAt) {
}
