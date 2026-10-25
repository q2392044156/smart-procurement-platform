package com.procuremind.platform.dashboard;

import com.procuremind.platform.workflow.WorkflowService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardService {
    private final JdbcTemplate jdbc;
    private final WorkflowService workflow;

    public DashboardService(JdbcTemplate jdbc, WorkflowService workflow) {
        this.jdbc = jdbc;
        this.workflow = workflow;
    }

    public Map<String, Object> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pendingApprovals", workflow.todoCount());
        data.put("yearPurchaseAmount", jdbc.queryForObject("select coalesce(sum(total_amount),0) from po_purchase_order where deleted=0 and status not in ('DRAFT','REJECTED','CANCELLED') and year(created_at)=year(utc_timestamp())", Object.class));
        data.put("activeOrders", jdbc.queryForObject("select count(*) from po_purchase_order where deleted=0 and status in ('APPROVED','PARTIALLY_RECEIVED')", Long.class));
        data.put("lowStockCount", jdbc.queryForObject("select count(*) from inv_balance b join md_material m on m.id=b.material_id where b.deleted=0 and b.quantity<m.min_stock", Long.class));
        data.put("orderStatus", jdbc.queryForList("select status name,count(*) value from po_purchase_order where deleted=0 group by status"));
        data.put("supplierRanking", jdbc.queryForList("select s.name,coalesce(sum(o.total_amount),0) amount,s.score from sp_supplier s left join po_purchase_order o on o.supplier_id=s.id and o.status not in ('DRAFT','REJECTED','CANCELLED') where s.deleted=0 group by s.id,s.name,s.score order by amount desc limit 5"));
        data.put("monthlyTrend", jdbc.queryForList("select date_format(created_at,'%Y-%m') month,sum(total_amount) amount from po_purchase_order where deleted=0 and created_at>=date_sub(utc_timestamp(),interval 12 month) and status not in ('DRAFT','REJECTED','CANCELLED') group by date_format(created_at,'%Y-%m') order by month"));
        data.put("lowStockMaterials", jdbc.queryForList("select m.code,m.name,w.name warehouse,b.quantity,m.min_stock minStock from inv_balance b join md_material m on m.id=b.material_id join md_warehouse w on w.id=b.warehouse_id where b.deleted=0 and b.quantity<m.min_stock order by (m.min_stock-b.quantity) desc limit 10"));
        return data;
    }
}
