package com.procuremind.platform.agentintegration;

import com.procuremind.platform.analytics.AnalyticsService;
import com.procuremind.platform.common.ApiResponse;
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
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsAgentController {
    private final AnalyticsService analytics;

    public AnalyticsAgentController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/suppliers/{id}/scorecard")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Map<String, Object>> supplierScorecard(@PathVariable Long id,
                                                              @RequestParam(defaultValue = "0") int year) {
        return ApiResponse.ok(analytics.supplierScorecard(id, year));
    }

    @GetMapping("/inventory-risk")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<List<Map<String, Object>>> inventoryRisk() {
        return ApiResponse.ok(analytics.inventoryRisk());
    }

    @GetMapping("/spend")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Map<String, Object>> spend(@RequestParam(defaultValue = "0") int year,
                                                  @RequestParam(defaultValue = "department") String groupBy) {
        return ApiResponse.ok(analytics.spendAnalysis(year, groupBy));
    }
}
