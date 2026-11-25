package com.procuremind.platform.controller;

import com.procuremind.platform.service.BudgetService;

import com.procuremind.platform.pojo.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

    // 部门年度预算，路由前缀 /api/v1/budgets；无类级 @PreAuthorize，读需 budget:view、写需 budget:edit。
    // 金额用 BigDecimal 原样传递，不做精度换算；year 缺省取当前自然年。
    // 本类不含占用额计算与部门存在性校验，都在 BudgetService/BudgetMapper；不锁行，保存即覆盖。
/** 部门年度预算查询与维护。 */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {
    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    // 预算列表：budget:view；按年度返回各部门预算额、已占用与可用，占用额为申请与直采订单两口径之和。
    @GetMapping
    @PreAuthorize("hasAuthority('budget:view')")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Integer year) {
        return ApiResponse.ok(service.list(year == null ? LocalDate.now().getYear() : year));
    }

    // 保存预算：budget:edit；按部门+年度 upsert，重复提交覆盖上次值，不保留历史版本。
    @PutMapping("/{departmentId}")
    @PreAuthorize("hasAuthority('budget:edit')")
    public ApiResponse<Void> save(@PathVariable Long departmentId,
                                  @RequestParam(required = false) Integer year,
                                  @RequestParam BigDecimal amount) {
        service.save(departmentId, year == null ? LocalDate.now().getYear() : year, amount);
        return ApiResponse.ok("预算已更新", null);
    }
}
