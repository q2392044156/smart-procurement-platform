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
    private final ReceiptService receipts;private final InventoryQueryService queries;private final IssueService issues;public InventoryController(ReceiptService receipts,InventoryQueryService queries,IssueService issues){this.receipts=receipts;this.queries=queries;this.issues=issues;}
    @PostMapping("/receipts") @PreAuthorize("hasAuthority('inventory:receive')") public ApiResponse<Map<String,Object>> receive(@RequestHeader("Idempotency-Key") @Size(max=100) String key,@Valid @RequestBody ReceiptService.Request r){return ApiResponse.ok(receipts.receive(key,r));}
    @GetMapping("/receipts") public ApiResponse<List<Map<String,Object>>> receiptList(){return ApiResponse.ok(queries.receipts());}
    @GetMapping("/receipts/{id}") public ApiResponse<Map<String,Object>> receipt(@PathVariable Long id){return ApiResponse.ok(queries.receipt(id));}
    @GetMapping("/inventory/balances") public ApiResponse<List<Map<String,Object>>> balances(){return ApiResponse.ok(queries.balances());}
    @GetMapping("/inventory/ledger") public ApiResponse<List<Map<String,Object>>> ledger(){return ApiResponse.ok(queries.ledger());}
    @GetMapping("/inventory/balances/page") public ApiResponse<Map<String,Object>> balancesPage(@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(queries.balancesPage(keyword,current,size));}
    @GetMapping("/inventory/ledger/page") public ApiResponse<Map<String,Object>> ledgerPage(@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(queries.ledgerPage(keyword,current,size));}
    @GetMapping("/receipts/page") public ApiResponse<Map<String,Object>> receiptPage(@RequestParam(required=false) String orderNo,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(queries.page(orderNo,current,size));}
    @PostMapping("/inventory/issues") @PreAuthorize("hasAuthority('inventory:issue')") public ApiResponse<Map<String,Object>> issueCreate(@Valid @RequestBody IssueService.IssueRequest r){return ApiResponse.ok(issues.issue(r));}
    @GetMapping("/inventory/issues") public ApiResponse<Map<String,Object>> issuePage(@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(issues.page(keyword,current,size));}
    @GetMapping("/inventory/issues/{id}") public ApiResponse<Map<String,Object>> issueDetail(@PathVariable Long id){return ApiResponse.ok(issues.detail(id));}
}
