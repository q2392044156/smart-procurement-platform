package com.procuremind.platform.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    private final JdbcTemplate jdbc;
    private final HttpServletRequest request;
    private final ObjectMapper mapper;
    public AuditAspect(JdbcTemplate jdbc, HttpServletRequest request, ObjectMapper mapper) { this.jdbc=jdbc; this.request=request; this.mapper=mapper; }
    @Around("@within(org.springframework.web.bind.annotation.RestController) && (execution(@org.springframework.web.bind.annotation.PostMapping * *(..)) || execution(@org.springframework.web.bind.annotation.PutMapping * *(..)) || execution(@org.springframework.web.bind.annotation.PatchMapping * *(..)) || execution(@org.springframework.web.bind.annotation.DeleteMapping * *(..)))")
    public Object audit(ProceedingJoinPoint point) throws Throwable {
        long begin=System.currentTimeMillis(); String result="SUCCESS"; String error=null;
        try { return point.proceed(); } catch (Throwable e) { result="FAILED"; error=e.getMessage(); throw e; }
        finally {
            try { jdbc.update("insert into sys_operation_audit(user_id,username,method,path,result,error_message,request_id,duration_ms,created_at) values(?,?,?,?,?,?,?,?,utc_timestamp())",
                    SecurityUtils.userIdOrSystem(), safeUsername(), request.getMethod(), request.getRequestURI(), result, error, MDC.get("requestId"), System.currentTimeMillis()-begin); } catch (Exception ignored) {}
        }
    }
    private String safeUsername() { try { return SecurityUtils.currentUser().username(); } catch (Exception e) { return "anonymous"; } }
}
