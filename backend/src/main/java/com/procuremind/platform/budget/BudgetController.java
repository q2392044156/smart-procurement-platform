package com.procuremind.platform.budget;

import com.procuremind.platform.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 部门年度预算查询与维护。 */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {
    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('budget:view')")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Integer year) {
        return ApiResponse.ok(service.list(year == null ? LocalDate.now().getYear() : year));
    }

    @PutMapping("/{departmentId}")
    @PreAuthorize("hasAuthority('budget:edit')")
    public ApiResponse<Void> save(@PathVariable Long departmentId,
                                  @RequestParam(required = false) Integer year,
                                  @RequestParam BigDecimal amount) {
        service.save(departmentId, year == null ? LocalDate.now().getYear() : year, amount);
        return ApiResponse.ok("预算已更新", null);
    }
}
