package com.procuremind.platform.controller;

import com.procuremind.platform.service.AuditService;

import com.procuremind.platform.pojo.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;

/**
 * 审计日志查询，路由前缀 /api/v1/audits；唯一端点 GET /list 要求 audit:view，按 id 倒序返回全部记录。
 * 数据由 AuditAspect 在写请求（POST/PUT/PATCH/DELETE）上自动追加，result 取 SUCCESS 或 FAILED，
 * 字段与 aspect 里 mapper.insert 的入参一一对应（操作人、方法、路径、结果、错误、requestId、耗时）。
 * 刻意不做：不分页、不按时间或操作人过滤、不提供删除——审计表只追加，保留期交给数据库侧策略。
 */
@RestController @RequestMapping("/api/v1/audits")
public class AuditController {private final AuditService service;public AuditController(AuditService service){this.service=service;}@GetMapping @PreAuthorize("hasAuthority('audit:view')") public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.ok(service.list());}}
