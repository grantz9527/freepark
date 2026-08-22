package com.freepark.cloud.common.exception;

import java.util.Locale;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED("error.validation", HttpStatus.BAD_REQUEST),
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
