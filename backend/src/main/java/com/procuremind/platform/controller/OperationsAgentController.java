package com.procuremind.platform.controller;

import com.procuremind.platform.service.BudgetService;
import com.procuremind.platform.pojo.ApiResponse;
import com.procuremind.platform.service.ContractService;
import com.procuremind.platform.service.IssueService;
import com.procuremind.platform.service.MasterDataService;
import com.procuremind.platform.service.PurchaseOrderService;
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
 * <p>路径前缀 {@code /api/ops}，只经内部令牌访问；没有统一的类级权限，每个端点按所属领域声明权限码。</p>
 * <p>数据都来自各业务 Service 的既有查询，本类不新增 SQL、不放大范围——行级范围仍由调用方的 scope 决定。</p>
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

    // 仓库与物资价格：masterdata:view；仓库只读，价格历史按物资+供应商返回已成交价序列。
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

    // 出库单：inventory:view；列表按 keyword 分页，详情带明细与批次扣减记录。
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

    // 预算、合同：budget:view 与 contract:view；合同分页支持 keyword/status，详情带关联订单与供应商。
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
