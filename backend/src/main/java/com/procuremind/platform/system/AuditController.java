package com.procuremind.platform.system;

import com.procuremind.platform.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;

@RestController @RequestMapping("/api/v1/audits")
public class AuditController {private final AuditService service;public AuditController(AuditService service){this.service=service;}@GetMapping @PreAuthorize("hasAuthority('audit:view')") public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.ok(service.list());}}
