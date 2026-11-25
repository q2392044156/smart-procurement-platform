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

/**
 * 写操作审计切片：@RestController 上的 POST/PUT/PATCH/DELETE 在结束时补一条审计记录。
 * 切点：@within(RestController) 限定 Web 层，四种 @*Mapping 限定写语义；读接口不记，避免淹没业务表。
 * 记录字段：userId（取不到时回落 system）、username（取不到时回落 anonymous）、method、URI、
 * result（SUCCESS/FAILED）、error 消息（仅失败时非空）、MDC 里的 requestId、耗时毫秒。
 * 吞掉落库异常的理由：审计是旁路，写不进去也不能连累业务响应；且此时响应可能已提交，再抛也改不了结果。
 */
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
        // 计时从进入切面开始，只覆盖控制器执行本身，不含前面的过滤器与反序列化。
        long begin = System.currentTimeMillis();
        String result = "SUCCESS";
        String error = null;
        try {
            // 失败也要留痕：先记 FAILED 与原异常消息，再把异常原样抛回，业务语义不受切片影响。
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

    // 取不到登录用户（匿名或未认证）时回落 anonymous，避免审计写入因空指针而整条丢失。
    private String safeUsername() {
        try {
            return SecurityUtils.currentUser().username();
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
