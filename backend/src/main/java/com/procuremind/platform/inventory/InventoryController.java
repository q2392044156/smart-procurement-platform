package com.procuremind.platform.inventory;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;

@RestController @Validated @RequestMapping("/api/v1") @PreAuthorize("hasAuthority('inventory:view')")
public class InventoryController {
    private final ReceiptService receipts;private final JdbcTemplate jdbc;public InventoryController(ReceiptService receipts,JdbcTemplate jdbc){this.receipts=receipts;this.jdbc=jdbc;}
    @PostMapping("/receipts") @PreAuthorize("hasAuthority('inventory:receive')") public ApiResponse<Map<String,Object>> receive(@RequestHeader("Idempotency-Key") @Size(max=100) String key,@Valid @RequestBody ReceiptService.Request r){return ApiResponse.ok(receipts.receive(key,r));}
    @GetMapping("/receipts") public ApiResponse<List<Map<String,Object>>> receiptList(){return ApiResponse.ok(jdbc.queryForList("select cast(r.id as char) id,r.receipt_no receiptNo,o.order_no orderNo,w.name warehouse,u.display_name receiver,r.receipt_date receiptDate,r.remark from inv_receipt r join po_purchase_order o on o.id=r.purchase_order_id join md_warehouse w on w.id=r.warehouse_id join sys_user u on u.id=r.receiver_id where r.deleted=0 order by r.id desc"));}
    @GetMapping("/inventory/balances") public ApiResponse<List<Map<String,Object>>> balances(){return ApiResponse.ok(jdbc.queryForList("select cast(b.id as char) id,w.name warehouse,m.code materialCode,m.name materialName,m.tracking_mode trackingMode,b.quantity,m.min_stock minStock,(b.quantity<m.min_stock) lowStock from inv_balance b join md_warehouse w on w.id=b.warehouse_id join md_material m on m.id=b.material_id where b.deleted=0 order by lowStock desc,m.code"));}
    @GetMapping("/inventory/ledger") public ApiResponse<List<Map<String,Object>>> ledger(){return ApiResponse.ok(jdbc.queryForList("select cast(l.id as char) id,w.name warehouse,m.code materialCode,m.name materialName,l.business_type businessType,cast(l.business_id as char) businessId,l.direction,l.quantity,l.balance_after balanceAfter,l.batch_no batchNo,l.created_at createdAt from inv_ledger l join md_warehouse w on w.id=l.warehouse_id join md_material m on m.id=l.material_id order by l.id desc limit 500"));}
}
