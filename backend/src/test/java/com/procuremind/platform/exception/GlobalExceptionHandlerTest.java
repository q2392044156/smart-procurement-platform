package com.procuremind.platform.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 方法不匹配必须回 405，而不是被兜底成 500。
 * Harness 把 500 视为可重试，一旦退化成 500 会触发无意义重试。
 */
class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unsupportedMethodReturns405WithAllowedMethods() {
        HttpRequestMethodNotSupportedException error =
                new HttpRequestMethodNotSupportedException("GET", List.of("PATCH", "PUT"));

        ResponseEntity<?> response = handler.methodNotAllowed(error);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().toString()).contains("405").contains("PATCH").contains("PUT");
    }

    @Test
    void unsupportedMethodWithoutAllowedListStillReturns405() {
        HttpRequestMethodNotSupportedException error = new HttpRequestMethodNotSupportedException("DELETE");

        ResponseEntity<?> response = handler.methodNotAllowed(error);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().toString()).doesNotContain("允许的方法");
    }
}
