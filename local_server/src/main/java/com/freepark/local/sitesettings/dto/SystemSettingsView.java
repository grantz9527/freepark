package com.freepark.local.sitesettings.dto;

import java.time.Instant;
import java.util.List;

import com.freepark.local.domain.PlateColor;
import com.freepark.local.softwareplate.SoftwarePlateProvider;
import com.freepark.local.softwareplate.dto.HyperLpr3Settings;

public record SystemSettingsView(
        String defaultLocale,
        String timezone,
        PlateColor defaultPlateColor,
        List<PlateColor> allowedPlateColors,
        String imageStoragePath,
        SoftwarePlateProvider softwarePlateProvider,
        Yolo26PlateSettings yolo26Plate,
        HyperLpr3Settings hyperLpr3,
        List<String> supportedLocales,
        List<String> supportedTimezones,
        List<PlateColor> supportedPlateColors,
        List<SoftwarePlateProvider> supportedSoftwarePlateProviders,
        Instant updatedAt) {
}
