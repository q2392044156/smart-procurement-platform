package com.procuremind.platform.controller;

import com.procuremind.platform.service.AnalyticsService;
import com.procuremind.platform.pojo.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 分析类内部工具面：供应商记分卡、库存风险、花费分析。
 * 仅内部令牌可访问，权限统一为 {@code dashboard:view}。
 * <p>路由前缀 {@code /api/analytics}，只经内部令牌访问；三个端点统一 dashboard:view，具体路径为
 * /suppliers/{id}/scorecard、/inventory-risk、/spend，行级数据范围由 Service 用 scope 参数下推。</p>
 * <p>刻意不做：不导出、不落库、不分页；本类不拼 SQL、不校验范围，越权风险由 scope 与 SQL 共同兜住。</p>
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsAgentController {
    private final AnalyticsService analytics;

    public AnalyticsAgentController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    // 供应商记分卡：dashboard:view；year=0 表示当年，分数与订单统计由 AnalyticsMapper 聚合。
    @GetMapping("/suppliers/{id}/scorecard")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Map<String, Object>> supplierScorecard(@PathVariable Long id,
                                                              @RequestParam(defaultValue = "0") int year) {
        return ApiResponse.ok(analytics.supplierScorecard(id, year));
    }

    // 库存风险：dashboard:view；无参数，返回低库存与呆滞清单，不含分页。
    @GetMapping("/inventory-risk")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<List<Map<String, Object>>> inventoryRisk() {
        return ApiResponse.ok(analytics.inventoryRisk());
    }

    // 花费分析：dashboard:view；groupBy 选分组维度（部门/供应商/品类/月份），按 scope 过滤。
    @GetMapping("/spend")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Map<String, Object>> spend(@RequestParam(defaultValue = "0") int year,
                                                  @RequestParam(defaultValue = "department") String groupBy) {
        return ApiResponse.ok(analytics.spendAnalysis(year, groupBy));
    }
}
