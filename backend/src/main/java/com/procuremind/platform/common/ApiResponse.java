package com.procuremind.platform.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(int code, String message, T data, String requestId) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data, MDC.get("requestId"));
    }
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(0, message, data, MDC.get("requestId"));
    }
    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null, MDC.get("requestId"));
    }
}
