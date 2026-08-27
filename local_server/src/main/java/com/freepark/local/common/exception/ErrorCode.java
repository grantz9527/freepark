package com.freepark.local.common.exception;

import java.util.Locale;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED("error.validation", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("error.auth.invalid-credentials", HttpStatus.UNAUTHORIZED),
    WRONG_PASSWORD("error.auth.wrong-password", HttpStatus.BAD_REQUEST),
    ACCOUNT_DISABLED("error.auth.disabled", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("error.unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("error.forbidden", HttpStatus.FORBIDDEN),
    USERNAME_EXISTS("error.user.username-exists", HttpStatus.BAD_REQUEST),
    LOT_CODE_EXISTS("error.lot.code-exists", HttpStatus.BAD_REQUEST),
    LANE_CODE_EXISTS("error.lane.code-exists", HttpStatus.BAD_REQUEST),
    LANE_LOTS_DUPLICATE("error.lane.lots-duplicate", HttpStatus.BAD_REQUEST),
    BARRIER_CODE_EXISTS("error.barrier.code-exists", HttpStatus.BAD_REQUEST),
    DEVICE_NOT_FOUND("error.device.not-found", HttpStatus.NOT_FOUND),
    DEVICE_DISABLED("error.device.disabled", HttpStatus.FORBIDDEN),
    BOOTH_NAME_EXISTS("error.booth.name-exists", HttpStatus.BAD_REQUEST),
    BOOTH_CODE_EXISTS("error.booth.code-exists", HttpStatus.BAD_REQUEST),
    SPACE_CODE_EXISTS("error.space.code-exists", HttpStatus.BAD_REQUEST),
    LOCATION_NAME_EXISTS("error.location.name-exists", HttpStatus.BAD_REQUEST),
    AREA_NAME_EXISTS("error.area.name-exists", HttpStatus.BAD_REQUEST),
    INTERNAL_VEHICLE_PLATE_EXISTS("error.internal-vehicle.plate-exists", HttpStatus.BAD_REQUEST),
    WHITELIST_VEHICLE_PLATE_EXISTS("error.whitelist-vehicle.plate-exists", HttpStatus.BAD_REQUEST),
    WHITELIST_VEHICLE_INVALID_TIME_RANGE("error.whitelist-vehicle.invalid-time-range", HttpStatus.BAD_REQUEST),
    BLACKLIST_VEHICLE_PLATE_EXISTS("error.blacklist-vehicle.plate-exists", HttpStatus.BAD_REQUEST),
    BLACKLIST_VEHICLE_INVALID_TIME_RANGE("error.blacklist-vehicle.invalid-time-range", HttpStatus.BAD_REQUEST),
    PATTERN_ALLOWLIST_NAME_EXISTS("error.pattern-allowlist.name-exists", HttpStatus.BAD_REQUEST),
    PATTERN_ALLOWLIST_PATTERN_EXISTS("error.pattern-allowlist.pattern-exists", HttpStatus.BAD_REQUEST),
    PATTERN_ALLOWLIST_INVALID_PATTERN("error.pattern-allowlist.invalid-pattern", HttpStatus.BAD_REQUEST),
    ACCESS_JUDGMENT_INVALID_ORDER("error.access-judgment.invalid-order", HttpStatus.BAD_REQUEST),
    INVALID_LOCALE("error.settings.invalid-locale", HttpStatus.BAD_REQUEST),
    INVALID_TIMEZONE("error.settings.invalid-timezone", HttpStatus.BAD_REQUEST),
    INVALID_PLATE_COLOR("error.settings.invalid-plate-color", HttpStatus.BAD_REQUEST),
    INVALID_PLATE_COLOR_CONFIG("error.settings.invalid-plate-color-config", HttpStatus.BAD_REQUEST),
    PLATE_COLOR_NOT_ALLOWED("error.plate-color.not-allowed", HttpStatus.BAD_REQUEST),
    INVALID_NODE_CONFIG("error.node.invalid-config", HttpStatus.BAD_REQUEST),
    INVALID_FRIGATE_CONFIG("error.frigate.invalid-config", HttpStatus.BAD_REQUEST),
    FRIGATE_CAMERA_EXISTS("error.frigate.camera-exists", HttpStatus.BAD_REQUEST),
    FRIGATE_MQTT_CONNECT_FAILED("error.frigate.mqtt-connect-failed", HttpStatus.BAD_REQUEST),
    NOT_FOUND("error.not-found", HttpStatus.NOT_FOUND),
    INTERNAL_ERROR("error.internal", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String messageKey;
    private final HttpStatus status;

    ErrorCode(String messageKey, HttpStatus status) {
        this.messageKey = messageKey;
        this.status = status;
    }

    public String messageKey() {
        return messageKey;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
