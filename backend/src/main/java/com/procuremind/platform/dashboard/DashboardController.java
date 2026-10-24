package com.procuremind.platform.dashboard;

import com.procuremind.platform.common.ApiResponse;
import com.procuremind.platform.system.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/dashboard") @PreAuthorize("hasAuthority('dashboard:view')")
public class DashboardController {
    private final JdbcTemplate jdbc;public DashboardController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping("/summary") public ApiResponse<Map<String,Object>> summary(){Map<String,Object>d=new LinkedHashMap<>();d.put("pendingApprovals",pending());d.put("yearPurchaseAmount",jdbc.queryForObject("select coalesce(sum(total_amount),0) from po_purchase_order where deleted=0 and status not in ('DRAFT','REJECTED','CANCELLED') and year(created_at)=year(utc_timestamp())",Object.class));d.put("activeOrders",jdbc.queryForObject("select count(*) from po_purchase_order where deleted=0 and status in ('APPROVED','PARTIALLY_RECEIVED')",Long.class));d.put("lowStockCount",jdbc.queryForObject("select count(*) from inv_balance b join md_material m on m.id=b.material_id where b.deleted=0 and b.quantity<m.min_stock",Long.class));d.put("orderStatus",jdbc.queryForList("select status name,count(*) value from po_purchase_order where deleted=0 group by status"));d.put("supplierRanking",jdbc.queryForList("select s.name,coalesce(sum(o.total_amount),0) amount,s.score from sp_supplier s left join po_purchase_order o on o.supplier_id=s.id and o.status not in ('DRAFT','REJECTED','CANCELLED') where s.deleted=0 group by s.id,s.name,s.score order by amount desc limit 5"));d.put("monthlyTrend",jdbc.queryForList("select date_format(created_at,'%Y-%m') month,sum(total_amount) amount from po_purchase_order where deleted=0 and created_at>=date_sub(utc_timestamp(),interval 12 month) and status not in ('DRAFT','REJECTED','CANCELLED') group by date_format(created_at,'%Y-%m') order by month"));d.put("lowStockMaterials",jdbc.queryForList("select m.code,m.name,w.name warehouse,b.quantity,m.min_stock minStock from inv_balance b join md_material m on m.id=b.material_id join md_warehouse w on w.id=b.warehouse_id where b.deleted=0 and b.quantity<m.min_stock order by (m.min_stock-b.quantity) desc limit 10"));return ApiResponse.ok(d);}
    private long pending(){var roles=SecurityUtils.currentUser().roles();if(roles.isEmpty())return 0;String placeholders=String.join(",",Collections.nCopies(roles.size(),"?"));return jdbc.queryForObject("select count(distinct t.ID_) from ACT_RU_TASK t join ACT_RU_IDENTITYLINK l on l.TASK_ID_=t.ID_ and l.TYPE_='candidate' where l.GROUP_ID_ in ("+placeholders+")",Long.class,roles.toArray());}
}
