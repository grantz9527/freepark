package com.freepark.local.sitesettings;

import java.util.List;

import com.freepark.local.domain.PlateColor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record UpdateSystemSettingsRequest(
        @NotBlank String defaultLocale,
        @NotBlank String timezone,
        @NotNull PlateColor defaultPlateColor,
        @NotEmpty List<PlateColor> allowedPlateColors) {
}
