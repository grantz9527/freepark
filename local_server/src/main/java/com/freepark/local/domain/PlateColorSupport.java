package com.freepark.local.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;

public final class PlateColorSupport {

    private PlateColorSupport() {
    }

    public static List<PlateColor> all() {
        return List.of(PlateColor.values());
    }

    public static List<String> allNames() {
        return Arrays.stream(PlateColor.values()).map(PlateColor::name).toList();
    }

    public static List<PlateColor> defaultChinaAllowed() {
        return List.of(
                PlateColor.BLUE,
                PlateColor.YELLOW,
                PlateColor.GREEN,
                PlateColor.YELLOW_GREEN,
                PlateColor.BLACK,
                PlateColor.WHITE);
    }

    public static PlateColor parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PLATE_COLOR);
        }
        try {
            return PlateColor.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_PLATE_COLOR);
        }
    }

    public static List<PlateColor> parseAllowed(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PLATE_COLOR_CONFIG);
        }
        List<PlateColor> parsed = new ArrayList<>();
        for (String value : values) {
            PlateColor color = parse(value);
            if (!parsed.contains(color)) {
                parsed.add(color);
            }
        }
        return parsed;
    }

    public static List<PlateColor> normalizeAllowed(List<PlateColor> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PLATE_COLOR_CONFIG);
        }
        List<PlateColor> parsed = new ArrayList<>();
        for (PlateColor color : values) {
            if (color != null && !parsed.contains(color)) {
                parsed.add(color);
            }
        }
        if (parsed.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PLATE_COLOR_CONFIG);
        }
        return parsed;
    }

    public static void ensureAllowed(PlateColor color, List<PlateColor> allowed) {
        if (color == null || allowed == null || !allowed.contains(color)) {
            throw new BusinessException(ErrorCode.PLATE_COLOR_NOT_ALLOWED);
        }
    }
}
