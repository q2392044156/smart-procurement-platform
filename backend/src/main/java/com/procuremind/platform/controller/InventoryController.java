package com.procuremind.platform.controller;

import com.procuremind.platform.service.InventoryQueryService;
import com.procuremind.platform.service.IssueService;
import com.procuremind.platform.service.ReceiptService;

import com.procuremind.platform.pojo.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;
import com.procuremind.platform.pojo.request.IssueRequest;
import com.procuremind.platform.pojo.request.ReceiptRequest;

/**
 * 收货、库存余额/台账与出库，路由前缀 /api/v1；类级 inventory:view 覆盖所有读，写操作单独抬权限。
 * 收货要 Idempotency-Key（必填、最大 100 字符）且需 inventory:receive，出库需 inventory:issue；
 * 两者都加行锁改余额并写台账，锁与事务在 ReceiptService/IssueService，本类只做绑定与包装。
 */
@RestController @Validated @RequestMapping("/api/v1") @PreAuthorize("hasAuthority('inventory:view')")
public class InventoryController {
    private final ReceiptService receipts;private final InventoryQueryService queries;private final IssueService issues;public InventoryController(ReceiptService receipts,InventoryQueryService queries,IssueService issues){this.receipts=receipts;this.queries=queries;this.issues=issues;}
    // 收货：inventory:receive；幂等键必填，按订单加行锁校验可收量与追踪方式（数量/批次/序列号）。
    @PostMapping("/receipts") @PreAuthorize("hasAuthority('inventory:receive')") public ApiResponse<Map<String,Object>> receive(@RequestHeader("Idempotency-Key") @Size(max=100) String key,@Valid @RequestBody ReceiptRequest r){return ApiResponse.ok(receipts.receive(key,r));}
    // 收货列表、详情、分页：inventory:view；详情含明细行，分页按订单号过滤。
    @GetMapping("/receipts") public ApiResponse<List<Map<String,Object>>> receiptList(){return ApiResponse.ok(queries.receipts());}
    @GetMapping("/receipts/{id}") public ApiResponse<Map<String,Object>> receipt(@PathVariable Long id){return ApiResponse.ok(queries.receipt(id));}
    // 余额与台账：inventory:view；列表版一次性全量，page 版按 keyword 过滤分页。
    @GetMapping("/inventory/balances") public ApiResponse<List<Map<String,Object>>> balances(){return ApiResponse.ok(queries.balances());}
    @GetMapping("/inventory/ledger") public ApiResponse<List<Map<String,Object>>> ledger(){return ApiResponse.ok(queries.ledger());}
    @GetMapping("/inventory/balances/page") public ApiResponse<Map<String,Object>> balancesPage(@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(queries.balancesPage(keyword,current,size));}
    @GetMapping("/inventory/ledger/page") public ApiResponse<Map<String,Object>> ledgerPage(@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(queries.ledgerPage(keyword,current,size));}
    @GetMapping("/receipts/page") public ApiResponse<Map<String,Object>> receiptPage(@RequestParam(required=false) String orderNo,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(queries.page(orderNo,current,size));}
    // 出库：inventory:issue；锁余额行、按 version 做乐观锁更新，并追加库存台账。
    @PostMapping("/inventory/issues") @PreAuthorize("hasAuthority('inventory:issue')") public ApiResponse<Map<String,Object>> issueCreate(@Valid @RequestBody IssueRequest r){return ApiResponse.ok(issues.issue(r));}
    // 出库列表与详情：inventory:view；keyword 匹配出库单号/物资，page 版分页，详情带明细。
    @GetMapping("/inventory/issues") public ApiResponse<Map<String,Object>> issuePage(@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(issues.page(keyword,current,size));}
    @GetMapping("/inventory/issues/{id}") public ApiResponse<Map<String,Object>> issueDetail(@PathVariable Long id){return ApiResponse.ok(issues.detail(id));}
}
