package com.freepark.local.common.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.messageKey());
        this.errorCode = errorCode;
        this.args = args;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object[] args() {
        return args;
    }
}
