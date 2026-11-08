package com.procuremind.platform.inventory;

import com.procuremind.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryQueryService {
    private final JdbcTemplate jdbc;

    public InventoryQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> receipts() {
        return jdbc.queryForList("""
                select cast(r.id as char) id,r.receipt_no receiptNo,cast(r.purchase_order_id as char) purchaseOrderId,
                       o.order_no orderNo,w.name warehouse,u.display_name receiver,r.receipt_date receiptDate,r.remark,
                       (select count(*) from inv_receipt_item i where i.receipt_id=r.id and i.deleted=0) itemCount
                from inv_receipt r
                join po_purchase_order o on o.id=r.purchase_order_id
                join md_warehouse w on w.id=r.warehouse_id
                join sys_user u on u.id=r.receiver_id
                where r.deleted=0 order by r.id desc
                """);
    }

    public Map<String, Object> page(String orderNo, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String keyword = orderNo == null || orderNo.isBlank() ? null : "%" + orderNo.trim() + "%";
        String clause = "where r.deleted=0" + (keyword == null ? "" : " and o.order_no like ?");
        List<Object> args = new ArrayList<>();
        if (keyword != null) args.add(keyword);
        Long total = jdbc.queryForObject(
                "select count(*) from inv_receipt r join po_purchase_order o on o.id=r.purchase_order_id " + clause,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList("""
                select cast(r.id as char) id,r.receipt_no receiptNo,cast(r.purchase_order_id as char) purchaseOrderId,
                       o.order_no orderNo,w.name warehouse,u.display_name receiver,r.receipt_date receiptDate,r.remark,
                       (select count(*) from inv_receipt_item i where i.receipt_id=r.id and i.deleted=0) itemCount
                from inv_receipt r
                join po_purchase_order o on o.id=r.purchase_order_id
                join md_warehouse w on w.id=r.warehouse_id
                join sys_user u on u.id=r.receiver_id
                """ + " " + clause + " order by r.id desc limit ? offset ?", pageArgs.toArray());
        long totalCount = total == null ? 0 : total;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", (int) totalCount);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) ((totalCount + pageSize - 1) / pageSize));
        return result;
    }

    public Map<String, Object> receipt(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select cast(r.id as char) id,r.receipt_no receiptNo,cast(r.purchase_order_id as char) purchaseOrderId,
                       o.order_no orderNo,cast(r.warehouse_id as char) warehouseId,w.name warehouse,
                       cast(r.receiver_id as char) receiverId,u.display_name receiver,r.receipt_date receiptDate,r.remark,r.created_at createdAt
                from inv_receipt r
                join po_purchase_order o on o.id=r.purchase_order_id
                join md_warehouse w on w.id=r.warehouse_id
                join sys_user u on u.id=r.receiver_id
                where r.id=? and r.deleted=0
                """, id);
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "收货单不存在");
        Map<String, Object> detail = rows.getFirst();
        detail.put("items", jdbc.queryForList("""
                select cast(i.id as char) id,cast(i.order_item_id as char) purchaseOrderItemId,
                       cast(i.material_id as char) materialId,m.code materialCode,m.name materialName,
                       m.tracking_mode trackingMode,i.quantity,i.batch_no batchNo,i.serial_numbers_json serialNumbers
                from inv_receipt_item i join md_material m on m.id=i.material_id
                where i.receipt_id=? and i.deleted=0 order by i.id
                """, id));
        return detail;
    }

    public Map<String, Object> balancesPage(String keyword, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        String clause = "b.deleted=0 and (m.code like ? or m.name like ? or w.name like ?)";
        Long total = jdbc.queryForObject("""
                select count(*) from inv_balance b
                join md_warehouse w on w.id=b.warehouse_id join md_material m on m.id=b.material_id
                where """ + " " + clause, Long.class, like, like, like);
        List<Map<String, Object>> records = jdbc.queryForList("""
                select cast(b.id as char) id,w.name warehouse,m.code materialCode,m.name materialName,
                       m.tracking_mode trackingMode,b.quantity,m.min_stock minStock,(b.quantity<m.min_stock) lowStock
                from inv_balance b join md_warehouse w on w.id=b.warehouse_id join md_material m on m.id=b.material_id
                where """ + " " + clause + " order by (b.quantity<m.min_stock) desc,m.code limit ? offset ?",
                like, like, like, pageSize, (page - 1) * pageSize);
        return pageResult(records, total, page, pageSize);
    }

    public Map<String, Object> ledgerPage(String keyword, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        String clause = "m.code like ? or m.name like ?";
        Long total = jdbc.queryForObject("""
                select count(*) from inv_ledger l
                join md_warehouse w on w.id=l.warehouse_id join md_material m on m.id=l.material_id
                where """ + " " + clause, Long.class, like, like);
        List<Map<String, Object>> records = jdbc.queryForList("""
                select cast(l.id as char) id,w.name warehouse,m.code materialCode,m.name materialName,
                       l.business_type businessType,cast(l.business_id as char) businessId,l.direction,l.quantity,
                       l.balance_after balanceAfter,l.batch_no batchNo,l.created_at createdAt
                from inv_ledger l join md_warehouse w on w.id=l.warehouse_id join md_material m on m.id=l.material_id
                where """ + " " + clause + " order by l.id desc limit ? offset ?",
                like, like, pageSize, (page - 1) * pageSize);
        return pageResult(records, total, page, pageSize);
    }

    private Map<String, Object> pageResult(List<Map<String, Object>> records, Long total, int page, int pageSize) {
        long totalCount = total == null ? 0 : total;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", (int) totalCount);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) ((totalCount + pageSize - 1) / pageSize));
        return result;
    }

    public List<Map<String, Object>> balances() {
        return jdbc.queryForList("""
                select cast(b.id as char) id,w.name warehouse,m.code materialCode,m.name materialName,
                       m.tracking_mode trackingMode,b.quantity,m.min_stock minStock,(b.quantity<m.min_stock) lowStock
                from inv_balance b join md_warehouse w on w.id=b.warehouse_id join md_material m on m.id=b.material_id
                where b.deleted=0 order by lowStock desc,m.code
                """);
    }

    public List<Map<String, Object>> ledger() {
        return jdbc.queryForList("""
                select cast(l.id as char) id,w.name warehouse,m.code materialCode,m.name materialName,
                       l.business_type businessType,cast(l.business_id as char) businessId,l.direction,l.quantity,
                       l.balance_after balanceAfter,l.batch_no batchNo,l.created_at createdAt
                from inv_ledger l join md_warehouse w on w.id=l.warehouse_id join md_material m on m.id=l.material_id
                order by l.id desc limit 500
                """);
    }
}
