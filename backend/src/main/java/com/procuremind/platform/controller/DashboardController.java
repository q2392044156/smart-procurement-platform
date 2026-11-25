package com.procuremind.platform.controller;

import com.procuremind.platform.service.DashboardService;

import com.procuremind.platform.pojo.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 经营看板汇总，路由前缀 /api/v1/dashboard；类级 dashboard:view，与预警、分析工具面共用同一权限码。
 * 单端点返回金额、在途订单、低库存、状态分布、供应商排名与月度趋势，口径全部在 DashboardMapper。
 * 刻意不做：不做行级数据范围过滤（DashboardMapper 没有 scope 参数）、不缓存、不落库，看板是全局经营视角。
 */
@RestController @RequestMapping("/api/v1/dashboard") @PreAuthorize("hasAuthority('dashboard:view')")
public class DashboardController {
    private final DashboardService service;public DashboardController(DashboardService service){this.service=service;}
    // 看板汇总：dashboard:view；无参数无分页，一次返回整屏指标，前端首屏与 Agent 工具共用此端点。
    @GetMapping("/summary") public ApiResponse<Map<String,Object>> summary(){return ApiResponse.ok(service.summary());}
}
