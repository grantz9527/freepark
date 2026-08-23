package com.freepark.cloud.common.exception;

import java.util.Locale;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED("error.validation", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("error.auth.invalid-credentials", HttpStatus.UNAUTHORIZED),
    WRONG_PASSWORD("error.auth.wrong-password", HttpStatus.BAD_REQUEST),
    ACCOUNT_DISABLED("error.auth.disabled", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("error.unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("error.forbidden", HttpStatus.FORBIDDEN),
    NOT_FOUND("error.not-found", HttpStatus.NOT_FOUND),
    LOT_CODE_EXISTS("error.lot.code-exists", HttpStatus.BAD_REQUEST),
    BILLING_PLAN_CODE_EXISTS("error.billing-plan.code-exists", HttpStatus.BAD_REQUEST),
    BILLING_PLAN_DISABLED("error.billing-plan.disabled", HttpStatus.BAD_REQUEST),
    BILLING_RULE_PLATE_COLOR_REQUIRED("error.billing-rule.plate-color-required", HttpStatus.BAD_REQUEST),
    BILLING_RULE_VEHICLE_TYPE_REQUIRED("error.billing-rule.vehicle-type-required", HttpStatus.BAD_REQUEST),
    BILLING_RULE_LENGTH_REQUIRED("error.billing-rule.length-required", HttpStatus.BAD_REQUEST),
    BILLING_RULE_LENGTH_INVALID("error.billing-rule.length-invalid", HttpStatus.BAD_REQUEST),
    BILLING_RULE_INVALID_DIMENSION("error.billing-rule.invalid-dimension", HttpStatus.BAD_REQUEST),
    BILLING_RULE_HOURLY_RATE_REQUIRED("error.billing-rule.hourly-rate-required", HttpStatus.BAD_REQUEST),
    BILLING_RULE_MONTHLY_RATE_REQUIRED("error.billing-rule.monthly-rate-required", HttpStatus.BAD_REQUEST),
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
