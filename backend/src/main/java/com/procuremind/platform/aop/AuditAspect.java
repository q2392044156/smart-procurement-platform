package com.procuremind.platform.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procuremind.platform.mapper.AuditMapper;
import com.procuremind.platform.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    private final AuditMapper mapper;
    private final HttpServletRequest request;
    private final ObjectMapper json;

    public AuditAspect(AuditMapper mapper, HttpServletRequest request, ObjectMapper json) {
        this.mapper = mapper;
        this.request = request;
        this.json = json;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController) && (execution(@org.springframework.web.bind.annotation.PostMapping * *(..)) || execution(@org.springframework.web.bind.annotation.PutMapping * *(..)) || execution(@org.springframework.web.bind.annotation.PatchMapping * *(..)) || execution(@org.springframework.web.bind.annotation.DeleteMapping * *(..)))")
    public Object audit(ProceedingJoinPoint point) throws Throwable {
        long begin = System.currentTimeMillis();
        String result = "SUCCESS";
        String error = null;
        try {
            return point.proceed();
        } catch (Throwable e) {
            result = "FAILED";
            error = e.getMessage();
            throw e;
        } finally {
            try {
                mapper.insert(SecurityUtils.userIdOrSystem(), safeUsername(), request.getMethod(),
                        request.getRequestURI(), result, error, MDC.get("requestId"),
                        System.currentTimeMillis() - begin);
            } catch (Exception ignored) {
            }
        }
    }

    private String safeUsername() {
        try {
            return SecurityUtils.currentUser().username();
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
