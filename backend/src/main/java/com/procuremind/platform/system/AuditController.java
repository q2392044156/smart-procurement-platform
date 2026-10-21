package com.procuremind.platform.system;

import com.procuremind.platform.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;

@RestController @RequestMapping("/api/v1/audits")
public class AuditController {private final JdbcTemplate jdbc;public AuditController(JdbcTemplate jdbc){this.jdbc=jdbc;}@GetMapping @PreAuthorize("hasAuthority('audit:view')") public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.ok(jdbc.queryForList("select cast(id as char) id,cast(user_id as char) userId,username,method,path,result,error_message errorMessage,request_id requestId,duration_ms durationMs,created_at createdAt from sys_operation_audit order by id desc limit 500"));}}
