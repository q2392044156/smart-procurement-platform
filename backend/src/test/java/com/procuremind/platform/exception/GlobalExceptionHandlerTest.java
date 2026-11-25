package com.procuremind.platform.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异常到状态码的映射约定：
 * 方法不匹配 405（不能被兜底成 500，Harness 会重试 500）；
 * AI 助手不可达 503、上游异常 502（同样不能是 500）。
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

    @Test
    void agentConnectionFailureReturns503() {
        WebClientRequestException error = new WebClientRequestException(
                new ConnectException("Connection refused"), HttpMethod.GET,
                URI.create("http://localhost:8000/api/history"), HttpHeaders.EMPTY);

        ResponseEntity<?> response = handler.agentUnreachable(error);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().toString()).contains("AI助手暂时不可用");
    }

    @Test
    void agentUpstreamErrorReturns502() {
        WebClientResponseException error = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "boom", HttpHeaders.EMPTY, new byte[0], null);

        ResponseEntity<?> response = handler.agentUpstreamError(error);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().toString()).contains("AI助手返回异常");
    }
}
