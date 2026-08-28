package com.freepark.local.softwareplate.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;

public record HyperLpr3Settings(
        @NotNull Boolean enabled,
        @Null @Pattern(regexp = "^https?://[^\\s]+$", message = "{validation.hyperlpr3.baseUrl}") String baseUrl,
        @Null @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double minConfidence,
        @Null @Min(value = 500) @Max(value = 60000) Integer connectTimeoutMs,
        @Null @Min(value = 1000) @Max(value = 300000) Integer readTimeoutMs) {

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8715";
    private static final double DEFAULT_MIN_CONF = 0.6;
    private static final int DEFAULT_CONNECT_MS = 5000;
    private static final int DEFAULT_READ_MS = 60_000;

    public static HyperLpr3Settings defaultsDisabled() {
        return new HyperLpr3Settings(false, DEFAULT_BASE_URL, DEFAULT_MIN_CONF, DEFAULT_CONNECT_MS, DEFAULT_READ_MS);
    }

    public static HyperLpr3Settings applyDefaultWhenDisabledOrNull(HyperLpr3Settings in) {
        if (in == null) return defaultsDisabled();
        if (!Boolean.TRUE.equals(in.enabled())) {
            return new HyperLpr3Settings(false,
                    nonNull(in.baseUrl(), DEFAULT_BASE_URL),
                    nonNull(in.minConfidence(), DEFAULT_MIN_CONF),
                    nonNull(in.connectTimeoutMs(), DEFAULT_CONNECT_MS),
                    nonNull(in.readTimeoutMs(), DEFAULT_READ_MS));
        }
        return new HyperLpr3Settings(true,
                nonNull(in.baseUrl(), DEFAULT_BASE_URL),
                nonNull(in.minConfidence(), DEFAULT_MIN_CONF),
                nonNull(in.connectTimeoutMs(), DEFAULT_CONNECT_MS),
                nonNull(in.readTimeoutMs(), DEFAULT_READ_MS));
    }

    public HyperLpr3Settings withMinConfidenceOverride(Double override) {
        if (override == null) return this;
        double v = Math.min(1.0, Math.max(0.0, override));
        return new HyperLpr3Settings(enabled(), baseUrl(), v, connectTimeoutMs(), readTimeoutMs());
    }

    private static <T> T nonNull(T v, T def) { return v != null ? v : def; }
}
