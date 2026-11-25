package com.procuremind.platform.exception;

import com.procuremind.platform.pojo.ApiResponse;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常 → 状态码 + ApiResponse 文案的唯一出口，保证错误响应与成功响应结构一致（code/message/data）。
 * 分工：业务异常自带 status/code（BusinessException），框架异常在这里归类，未知异常兜底 500。
 * 刻意不做：不在响应里暴露堆栈、SQL 与表名；参数校验只说哪个字段不合法，不回显具体取值。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 业务异常自带状态码与错误码，这里只透传，避免领域状态映射散落到 Web 层。
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return ResponseEntity.status(e.status()).body(ApiResponse.error(e.code(), e.getMessage()));
    }
    // 校验失败：优先取字段级 defaultMessage（注解里写的中文），取不到才回落异常消息。
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ApiResponse<Void>> validation(Exception e) {
        String message = e instanceof MethodArgumentNotValidException m && m.getBindingResult().getFieldError() != null
                ? m.getBindingResult().getFieldError().getDefaultMessage() : e.getMessage();
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message));
    }
    // 请求体不可解析、类型不匹配、缺参数、方法参数校验失败——对客户端都是参数不对，合并成一条文案。
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestValueException.class, HandlerMethodValidationException.class})
    ResponseEntity<ApiResponse<Void>> malformed(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求参数格式错误或缺少必填参数"));
    }
    // 403 文案与 SecurityConfig 的 accessDeniedHandler 保持一致：同一语义不能出现两种说法。
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, "无权执行此操作"));
    }
    // 期望命中一行却查不到（如 update 影响 0 行）→ 404“数据不存在”。
    @ExceptionHandler(EmptyResultDataAccessException.class)
    ResponseEntity<ApiResponse<Void>> notFound(EmptyResultDataAccessException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "数据不存在"));
    }
    // 唯一键/外键/非空等约束冲突 → 409，文案点出三类常见原因（唯一编码、关联数据、字段取值）。
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(409, "数据冲突，请检查唯一编码、关联数据或字段取值"));
    }
    // 未映射的路径或静态资源 → 404，与“数据不存在”区分：一个是接口不存在，一个是数据不存在。
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> noResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "请求的接口不存在"));
    }
    // 405 保留框架给出的允许方法集合，排序后用“、”连接，调用方可直接照此改正。
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        String allowed = e.getSupportedHttpMethods() == null ? ""
                : e.getSupportedHttpMethods().stream().map(HttpMethod::name).sorted().collect(Collectors.joining("、"));
        String message = allowed.isEmpty() ? "请求方法不支持" : "请求方法不支持，允许的方法：" + allowed;
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error(405, message));
    }
    // 客户端已断开：写响应必然失败，只留 debug 日志，返回 void 让容器自行忽略。
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void clientDisconnected(AsyncRequestNotUsableException e) {
        log.debug("Client disconnected before the response completed: {}", e.getMessage());
    }
    // Agent 服务连不上 → 503 而不是 500：AI 助手是旁路，降级后主业务仍可用，文案也明说这一点。
    @ExceptionHandler(WebClientRequestException.class)
    ResponseEntity<ApiResponse<Void>> agentUnreachable(WebClientRequestException e) {
        log.warn("Agent service unreachable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, "AI助手暂时不可用，业务系统仍可正常使用"));
    }
    // 上游有响应但不是 2xx → 502：语义是“网关拿到坏响应”，与“完全不可达”的 503 区分开。
    @ExceptionHandler(WebClientResponseException.class)
    ResponseEntity<ApiResponse<Void>> agentUpstreamError(WebClientResponseException e) {
        log.warn("Agent service returned {}: {}", e.getStatusCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(502, "AI助手返回异常，请稍后重试"));
    }
    // 兜底 500：完整堆栈只进日志，响应里只给通用文案，避免泄漏实现细节。
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unknown(Exception e) {
        log.error("Unhandled request error", e);
        return ResponseEntity.internalServerError().body(ApiResponse.error(500, "系统内部错误"));
    }
}
