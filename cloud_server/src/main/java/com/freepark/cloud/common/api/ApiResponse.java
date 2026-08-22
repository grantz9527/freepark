package com.freepark.cloud.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.freepark.cloud.common.i18n.MessageService;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> ok(MessageService messages, T data) {
        return new ApiResponse<>(true, "ok", messages.get("api.success"), data);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
