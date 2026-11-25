package com.procuremind.platform.service;

import com.procuremind.platform.mapper.DashboardMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardService {
    private final DashboardMapper mapper;
    private final WorkflowService workflow;

    public DashboardService(DashboardMapper mapper, WorkflowService workflow) {
        this.mapper = mapper;
        this.workflow = workflow;
    }

    public Map<String, Object> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pendingApprovals", workflow.todoCount());
        data.put("yearPurchaseAmount", mapper.yearPurchaseAmount());
        data.put("activeOrders", mapper.activeOrders());
        data.put("lowStockCount", mapper.lowStockCount());
        data.put("orderStatus", mapper.orderStatus());
        data.put("supplierRanking", mapper.supplierRanking());
        data.put("monthlyTrend", mapper.monthlyTrend());
        data.put("lowStockMaterials", mapper.lowStockMaterials());
        return data;
    }
}
