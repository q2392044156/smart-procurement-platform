package com.procuremind.platform.alerts;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 到期与超时提醒：供应商资质临期/过期、订单交付临期/逾期、审批积压。
 *
 * <p>只做查询，不做推送；Agent 或看板按需拉取后自行组织提醒话术。</p>
 */
@Service
public class AlertService {
    private final JdbcTemplate jdbc;

    public AlertService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> summary(int qualificationDays, int deliveryDays, int approvalHours) {
        int qualificationWindow = clamp(qualificationDays, 0, 365);
        int deliveryWindow = clamp(deliveryDays, 0, 365);
        int approvalWindow = clamp(approvalHours, 1, 720);

        List<Map<String, Object>> qualifications = jdbc.queryForList("""
                select cast(q.id as char) id,cast(q.supplier_id as char) supplierId,s.name supplierName,
                       s.code supplierCode,q.name qualification,q.valid_until validUntil,
                       datediff(q.valid_until,current_date) daysLeft
                from sp_supplier_qualification q
                join sp_supplier s on s.id=q.supplier_id
                where q.deleted=0 and s.deleted=0 and q.status<>'REVOKED' and q.valid_until is not null
                  and q.valid_until <= date_add(current_date, interval ? day)
                order by q.valid_until
                """, qualificationWindow);

        List<Map<String, Object>> deliveries = jdbc.queryForList("""
                select cast(o.id as char) id,o.order_no orderNo,s.name supplierName,o.status,
                       o.delivery_date deliveryDate,datediff(o.delivery_date,current_date) daysLeft,
                       o.total_amount totalAmount
                from po_purchase_order o
                join sp_supplier s on s.id=o.supplier_id
                where o.deleted=0 and o.status in ('APPROVED','PARTIALLY_RECEIVED')
                  and o.delivery_date <= date_add(current_date, interval ? day)
                order by o.delivery_date
                """, deliveryWindow);

        List<Map<String, Object>> approvals = jdbc.queryForList("""
                select * from (
                    select 'REQUISITION' businessType,cast(r.id as char) id,r.requisition_no businessNo,
                           r.budget_amount amount,timestampdiff(hour,r.updated_at,utc_timestamp()) waitingHours
                    from pr_requisition r
                    where r.deleted=0 and r.status='PENDING_APPROVAL'
                      and r.updated_at <= utc_timestamp() - interval ? hour
                    union all
                    select 'PURCHASE_ORDER',cast(o.id as char) id,o.order_no businessNo,
                           o.total_amount amount,timestampdiff(hour,o.updated_at,utc_timestamp()) waitingHours
                    from po_purchase_order o
                    where o.deleted=0 and o.status='PENDING_APPROVAL'
                      and o.updated_at <= utc_timestamp() - interval ? hour
                ) pending order by waitingHours desc
                """, approvalWindow, approvalWindow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", Instant.now().toString());
        result.put("qualifications", block("withinDays", qualificationWindow, qualifications,
                qualifications.stream().filter(row -> ((Number) row.get("daysLeft")).intValue() < 0).count(),
                "供应商资质临期或已过期"));
        result.put("deliveries", block("withinDays", deliveryWindow, deliveries,
                deliveries.stream().filter(row -> ((Number) row.get("daysLeft")).intValue() < 0).count(),
                "订单交付临期或已逾期"));
        result.put("approvals", block("olderThanHours", approvalWindow, approvals, 0L, "审批积压"));
        return result;
    }

    private Map<String, Object> block(String windowKey, int window, List<Map<String, Object>> items, long overdue, String message) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put(windowKey, window);
        block.put("count", items.size());
        block.put("overdueCount", overdue);
        block.put("message", message);
        block.put("items", items);
        return block;
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
