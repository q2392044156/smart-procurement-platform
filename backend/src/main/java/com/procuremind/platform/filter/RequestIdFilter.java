package com.procuremind.platform.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * 请求号过滤器：给每个请求一个可追踪的 requestId，贯穿 MDC 日志、响应头与审计表。
 * 范围：对所有请求生效（OncePerRequestFilter），不解析身份、不做权限判断，也永不拒绝请求。
 * 放行条件：请求头 X-Request-Id 必须匹配 [A-Za-z0-9._-]{1,64}，否则改用 UUID——外部传入的脏值不会进日志。
 * 失败时没有状态码与文案：本过滤器只补上下文，401/403 由 SecurityConfig 与两个认证过滤器给出。
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 合法请求头就沿用（便于跨服务串联），否则新生成 UUID；两者都写进 MDC 与响应头。
        String requestId = Optional.ofNullable(request.getHeader("X-Request-Id"))
                .filter(s -> s.matches("[A-Za-z0-9._-]{1,64}")).orElse(UUID.randomUUID().toString());
        // finally 里清 MDC：线程池复用下不清理，会把上一个请求的 requestId 带进下一个请求的日志。
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        try { chain.doFilter(request, response); } finally { MDC.remove("requestId"); }
    }
}
