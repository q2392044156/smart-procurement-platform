package com.procuremind.platform.inventory;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;

@RestController @Validated @RequestMapping("/api/v1") @PreAuthorize("hasAuthority('inventory:view')")
public class InventoryController {
    private final ReceiptService receipts;private final InventoryQueryService queries;public InventoryController(ReceiptService receipts,InventoryQueryService queries){this.receipts=receipts;this.queries=queries;}
    @PostMapping("/receipts") @PreAuthorize("hasAuthority('inventory:receive')") public ApiResponse<Map<String,Object>> receive(@RequestHeader("Idempotency-Key") @Size(max=100) String key,@Valid @RequestBody ReceiptService.Request r){return ApiResponse.ok(receipts.receive(key,r));}
    @GetMapping("/receipts") public ApiResponse<List<Map<String,Object>>> receiptList(){return ApiResponse.ok(queries.receipts());}
    @GetMapping("/receipts/{id}") public ApiResponse<Map<String,Object>> receipt(@PathVariable Long id){return ApiResponse.ok(queries.receipt(id));}
    @GetMapping("/inventory/balances") public ApiResponse<List<Map<String,Object>>> balances(){return ApiResponse.ok(queries.balances());}
    @GetMapping("/inventory/ledger") public ApiResponse<List<Map<String,Object>>> ledger(){return ApiResponse.ok(queries.ledger());}
}
