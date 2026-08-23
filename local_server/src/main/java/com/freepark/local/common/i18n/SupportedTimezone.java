package com.freepark.local.common.i18n;

import java.time.ZoneId;
import java.util.List;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;

public final class SupportedTimezone {

    private static final List<String> ZONES = List.of(
            "UTC",
            "Asia/Shanghai",
            "Asia/Tokyo",
            "Asia/Seoul",
            "Asia/Singapore",
            "Asia/Hong_Kong",
            "Asia/Kolkata",
            "Europe/London",
            "Europe/Paris",
            "Europe/Berlin",
            "America/New_York",
            "America/Chicago",
            "America/Denver",
            "America/Los_Angeles",
            "Australia/Sydney");

    private SupportedTimezone() {
    }

    public static List<String> all() {
        return ZONES;
    }

    public static ZoneId defaultZone() {
        return ZoneId.of("Asia/Shanghai");
    }

    public static ZoneId resolve(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return defaultZone();
        }
        String trimmed = zoneId.trim();
        if (!ZONES.contains(trimmed)) {
            throw new BusinessException(ErrorCode.INVALID_TIMEZONE);
        }
        return ZoneId.of(trimmed);
    }
}
