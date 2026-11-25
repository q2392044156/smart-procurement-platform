package com.procuremind.platform.service;

import com.procuremind.platform.mapper.DashboardMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 首页看板的只读聚合：把待办数、年度采购额、在途订单、低库存、订单状态、供应商排名、月度趋势
 * 与低库存物资拼成一个 Map 一次返回，避免前端串行发 8 个请求。
 *
 * <p>边界：只读，不写库、不改状态、无事务；统计口径全部由 SQL 决定，本类不做二次计算，也不做缓存，
 * 因此数据实时但每次刷新都会打一遍库。待办数走 Flowable（WorkflowService），其余走 DashboardMapper。</p>
 */
@Service
public class DashboardService {
    private final DashboardMapper mapper;
    private final WorkflowService workflow;

    public DashboardService(DashboardMapper mapper, WorkflowService workflow) {
        this.mapper = mapper;
        this.workflow = workflow;
    }

    /**
     * 组装看板数据：8 个板块一次查完，key 顺序固定（LinkedHashMap）便于前端直接映射。
     * 各 Mapper 查询相互独立，任一板块无数据时返回空列表/0 而不是 null，前端无需判空。
     */
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
