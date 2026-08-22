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
