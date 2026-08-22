package com.freepark.local.common.exception;

import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageService messages;

    public GlobalExceptionHandler(MessageService messages) {
        this.messages = messages;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode errorCode = ex.errorCode();
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.fail(errorCode.code(), messages.get(errorCode.messageKey(), ex.args())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fieldMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.code(),
                        messages.get(ErrorCode.VALIDATION_FAILED.messageKey(), detail)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.code(),
                        messages.get(ErrorCode.VALIDATION_FAILED.messageKey(), detail)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.code(),
                        messages.get(ErrorCode.INTERNAL_ERROR.messageKey())));
    }

    private String fieldMessage(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
