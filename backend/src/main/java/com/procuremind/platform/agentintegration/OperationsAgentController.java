package com.procuremind.platform.agentintegration;

import com.procuremind.platform.budget.BudgetService;
import com.procuremind.platform.common.ApiResponse;
import com.procuremind.platform.contract.ContractService;
import com.procuremind.platform.inventory.IssueService;
import com.procuremind.platform.masterdata.MasterDataService;
import com.procuremind.platform.purchaseorder.PurchaseOrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 运营类只读工具面：仓库、出库、预算、合同与历史成交价。
 *
 * <p>供 Agent 通过内部令牌访问，复用领域 Service 的数据范围与权限规则，
 * 不直接暴露给浏览器（Nginx 对 {@code /api/} 固定 404）。</p>
 */
@RestController
@RequestMapping("/api")
public class OperationsAgentController {
    private final MasterDataService masterData;
    private final IssueService issues;
    private final BudgetService budgets;
    private final ContractService contracts;
    private final PurchaseOrderService orders;

    public OperationsAgentController(MasterDataService masterData, IssueService issues, BudgetService budgets,
                                     ContractService contracts, PurchaseOrderService orders) {
        this.masterData = masterData;
        this.issues = issues;
        this.budgets = budgets;
        this.contracts = contracts;
        this.orders = orders;
    }

    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('masterdata:view')")
    public ApiResponse<List<Map<String, Object>>> warehouses() {
        return ApiResponse.ok(masterData.warehouses());
    }

    @GetMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('masterdata:view')")
    public ApiResponse<Map<String, Object>> warehouse(@PathVariable Long id) {
        return ApiResponse.ok(masterData.warehouse(id));
    }

    @GetMapping("/inventory/issues")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<Map<String, Object>> issuePage(@RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") int current,
                                                      @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(issues.page(keyword, current, size));
    }

    @GetMapping("/inventory/issues/{id}")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<Map<String, Object>> issue(@PathVariable Long id) {
        return ApiResponse.ok(issues.detail(id));
    }

    @GetMapping("/budgets")
    @PreAuthorize("hasAuthority('budget:view')")
    public ApiResponse<List<Map<String, Object>>> budgets(@RequestParam(defaultValue = "0") int year) {
        return ApiResponse.ok(budgets.list(year));
    }

    @GetMapping("/contracts/page")
    @PreAuthorize("hasAuthority('contract:view')")
    public ApiResponse<Map<String, Object>> contractPage(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(defaultValue = "1") int current,
                                                         @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(contracts.page(keyword, status, current, size));
    }

    @GetMapping("/contracts/get/{id}")
    @PreAuthorize("hasAuthority('contract:view')")
    public ApiResponse<Map<String, Object>> contract(@PathVariable Long id) {
        return ApiResponse.ok(contracts.detail(id));
    }

    @GetMapping("/parts/{id}/price-history")
    @PreAuthorize("hasAuthority('masterdata:view')")
    public ApiResponse<List<Map<String, Object>>> priceHistory(@PathVariable Long id) {
        return ApiResponse.ok(orders.priceHistory(id));
    }
}
