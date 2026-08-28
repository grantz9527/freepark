package com.freepark.local.sitesettings.dto;

public record Yolo26PlateSettings(
        boolean enabled,
        String baseUrl,
        Double minConfidence,
        Integer connectTimeoutMs,
        Integer readTimeoutMs) {
}
