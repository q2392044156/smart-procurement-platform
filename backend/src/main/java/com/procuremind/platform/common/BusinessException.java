package com.procuremind.platform.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final int code;
    public BusinessException(HttpStatus status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public BusinessException(HttpStatus status, String message) {
        this(status, status.value(), message);
    }
    public HttpStatus status() { return status; }
    public int code() { return code; }
}
