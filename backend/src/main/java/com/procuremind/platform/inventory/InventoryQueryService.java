package com.procuremind.platform.inventory;

import com.procuremind.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
