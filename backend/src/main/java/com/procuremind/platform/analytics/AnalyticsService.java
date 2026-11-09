package com.procuremind.platform.analytics;

import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 分析工具的数据聚合层：供应商记分卡、库存风险、花费分析。
 *
 * <p>聚合放在服务端 SQL 中完成，避免模型多步取数后自行计算导致口径不一致；
 * 订单类指标统一应用当前用户的数据范围。</p>
 */
@Service
public class AnalyticsService {
    private final JdbcTemplate jdbc;
    private final DataScopeService dataScope;

    public AnalyticsService(JdbcTemplate jdbc, DataScopeService dataScope) {
        this.jdbc = jdbc;
        this.dataScope = dataScope;
    }

    public Map<String, Object> supplierScorecard(Long supplierId, int year) {
        int fiscalYear = fiscalYear(year);
        DataScopeService.SqlFilter filter = dataScope.purchaseOrderFilter("o", "u");
        List<Object> args = new ArrayList<>();
        args.add(fiscalYear);
        args.addAll(Arrays.asList(filter.args()));
        args.add(fiscalYear);
        args.addAll(Arrays.asList(filter.args()));
        args.add(supplierId);
        Map<String, Object> row = jdbc.queryForMap("""
                select cast(s.id as char) supplierId,s.code,s.name,s.level,s.status,s.score,
                  (select count(*) from po_purchase_order o join sys_user u on u.id=o.purchaser_id
                    where o.supplier_id=s.id and o.deleted=0 and o.status<>'CANCELLED'
                      and year(o.created_at)=? and %s) orderCount,
                  (select coalesce(sum(o.total_amount),0) from po_purchase_order o join sys_user u on u.id=o.purchaser_id
                    where o.supplier_id=s.id and o.deleted=0 and o.status<>'CANCELLED'
                      and year(o.created_at)=? and %s) amount,
                  (select min(q.valid_until) from sp_supplier_qualification q
                    where q.supplier_id=s.id and q.status='VALID') nearestQualificationExpiry,
                  (select round(avg(datediff(r.receipt_date,o.created_at)),1)
                    from inv_receipt r join po_purchase_order o on o.id=r.purchase_order_id
                    where o.supplier_id=s.id and r.deleted=0 and o.deleted=0) avgDeliveryDays,
                  (select count(*) from po_purchase_order o
                    where o.supplier_id=s.id and o.deleted=0 and o.status='CANCELLED') cancelledCount
                from sp_supplier s where s.id=? and s.deleted=0
                """.formatted(filter.clause(), filter.clause()), args.toArray());
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("year", fiscalYear);
        return result;
    }

    public List<Map<String, Object>> inventoryRisk() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select cast(m.id as char) materialId,m.code materialCode,m.name materialName,w.name warehouse,b.quantity,m.min_stock minStock,
                       cast(greatest(m.min_stock-b.quantity,0) as decimal(18,2)) shortage,
                       cast(coalesce((select sum(i.quantity-i.received_quantity)
                         from po_purchase_order_item i join po_purchase_order o on o.id=i.purchase_order_id
                         where i.material_id=m.id and i.deleted=0 and o.deleted=0
                           and o.status in ('APPROVED','PARTIALLY_RECEIVED')),0) as decimal(18,2)) inTransit
                from inv_balance b
                join md_material m on m.id=b.material_id
                join md_warehouse w on w.id=b.warehouse_id
                where b.deleted=0 and b.quantity<m.min_stock
                order by shortage desc
                """);
        for (Map<String, Object> row : rows) {
            BigDecimal shortage = decimal(row.get("shortage"));
            BigDecimal inTransit = decimal(row.get("inTransit"));
            BigDecimal netGap = shortage.subtract(inTransit).max(BigDecimal.ZERO);
            row.put("netGap", netGap.toPlainString());
            row.put("riskLevel", netGap.signum() > 0 ? "HIGH" : inTransit.signum() > 0 ? "COVERED" : "LOW");
        }
        return rows;
    }

    public Map<String, Object> spendAnalysis(int year, String groupBy) {
        int fiscalYear = fiscalYear(year);
        String group = groupBy == null || groupBy.isBlank() ? "department" : groupBy.trim().toLowerCase();
        String dimension;
        String extraJoin;
        switch (group) {
            case "department" -> {
                dimension = "d.name";
                extraJoin = "join sys_department d on d.id=u.department_id";
            }
            case "supplier" -> {
                dimension = "s.name";
                extraJoin = "join sp_supplier s on s.id=o.supplier_id";
            }
            case "category" -> {
                dimension = "c.name";
                extraJoin = "join md_material m on m.id=i.material_id join md_material_category c on c.id=m.category_id";
            }
            case "month" -> {
                dimension = "date_format(o.created_at,'%Y-%m')";
                extraJoin = "";
            }
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的统计维度");
        }
        DataScopeService.SqlFilter filter = dataScope.purchaseOrderFilter("o", "u");
        List<Object> args = new ArrayList<>();
        args.add(fiscalYear);
        args.addAll(Arrays.asList(filter.args()));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select %s name,count(distinct o.id) orders,
                       cast(coalesce(sum(i.quantity*i.unit_price),0) as decimal(18,2)) amount
                from po_purchase_order o
                join po_purchase_order_item i on i.purchase_order_id=o.id and i.deleted=0
                join sys_user u on u.id=o.purchaser_id
                %s
                where o.deleted=0 and o.status<>'CANCELLED' and year(o.created_at)=? and %s
                group by %s
                order by amount desc
                """.formatted(dimension, extraJoin, filter.clause(), dimension), args.toArray());
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            total = total.add(decimal(row.get("amount")));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", fiscalYear);
        result.put("groupBy", group);
        result.put("total", total.toPlainString());
        result.put("records", rows);
        return result;
    }

    private int fiscalYear(int year) {
        return year <= 0 ? LocalDate.now().getYear() : year;
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal big) return big;
        return new BigDecimal(value.toString());
    }
}
