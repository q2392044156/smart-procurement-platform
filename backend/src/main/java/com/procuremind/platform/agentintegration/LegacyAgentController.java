package com.procuremind.platform.agentintegration;

import com.procuremind.platform.alerts.AlertService;
import com.procuremind.platform.common.ApiResponse;
import com.procuremind.platform.dashboard.DashboardService;
import com.procuremind.platform.inventory.InventoryQueryService;
import com.procuremind.platform.requisition.RequisitionService;
import com.procuremind.platform.workflow.WorkflowService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 给 Agent 的只读工具面：采购申请、审批待办、收货记录与经营看板。
 *
 * <p>与 {@link LegacyErpController} 一样只经内部令牌访问；数据范围由下游 Service 负责。</p>
 */
@RestController
@RequestMapping("/api")
public class LegacyAgentController {
    private final RequisitionService requisitions;
    private final WorkflowService workflow;
    private final InventoryQueryService inventory;
    private final DashboardService dashboard;
    private final AlertService alerts;

    public LegacyAgentController(RequisitionService requisitions, WorkflowService workflow,
                                 InventoryQueryService inventory, DashboardService dashboard, AlertService alerts) {
        this.requisitions = requisitions;
        this.workflow = workflow;
        this.inventory = inventory;
        this.dashboard = dashboard;
        this.alerts = alerts;
    }

    @GetMapping("/requisitions/page")
    @PreAuthorize("hasAuthority('requisition:view')")
    public ApiResponse<Map<String, Object>> requisitionPage(@RequestParam(defaultValue = "1") int current,
                                                            @RequestParam(defaultValue = "10") int size,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String status) {
        return ApiResponse.ok(requisitions.page(keyword, status, current, size));
    }

    @GetMapping("/requisitions/get/{id}")
    @PreAuthorize("hasAuthority('requisition:view')")
    public ApiResponse<Map<String, Object>> requisitionDetail(@PathVariable Long id) {
        return ApiResponse.ok(requisitions.detail(id));
    }

    @GetMapping("/workflow/todo")
    @PreAuthorize("hasAuthority('workflow:approve')")
    public ApiResponse<List<Map<String, Object>>> todo() {
        return ApiResponse.ok(workflow.todo());
    }

    @GetMapping("/workflow/done")
    @PreAuthorize("hasAuthority('workflow:approve')")
    public ApiResponse<List<Map<String, Object>>> done() {
        return ApiResponse.ok(workflow.done());
    }

    @GetMapping("/workflow/timeline/{businessType}/{businessId}")
    @PreAuthorize("hasAnyAuthority('requisition:view','purchaseorder:view')")
    public ApiResponse<List<Map<String, Object>>> timeline(@PathVariable String businessType, @PathVariable Long businessId) {
        return ApiResponse.ok(workflow.timeline(businessType.toUpperCase(), businessId));
    }

    @GetMapping("/receipts/page")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<Map<String, Object>> receiptPage(@RequestParam(defaultValue = "1") int current,
                                                        @RequestParam(defaultValue = "10") int size,
                                                        @RequestParam(required = false) String orderNo) {
        return ApiResponse.ok(inventory.page(orderNo, current, size));
    }

    @GetMapping("/receipts/get/{id}")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<Map<String, Object>> receiptDetail(@PathVariable Long id) {
        return ApiResponse.ok(inventory.receipt(id));
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Map<String, Object>> dashboardSummary() {
        return ApiResponse.ok(dashboard.summary());
    }

    @GetMapping("/alerts/summary")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Map<String, Object>> alertsSummary(@RequestParam(defaultValue = "30") int qualificationDays,
                                                          @RequestParam(defaultValue = "7") int deliveryDays,
                                                          @RequestParam(defaultValue = "24") int approvalHours) {
        return ApiResponse.ok(alerts.summary(qualificationDays, deliveryDays, approvalHours));
    }
}
