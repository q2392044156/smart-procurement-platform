package com.procuremind.platform.controller;

import com.procuremind.platform.service.PurchaseOrderService;

import com.procuremind.platform.pojo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;
import com.procuremind.platform.pojo.request.PurchaseOrderUpdateRequest;
import com.procuremind.platform.pojo.request.ReasonRequest;
import com.procuremind.platform.pojo.request.PurchaseOrderRequest;

/**
 * 采购订单：由申请转单、改单、提交、取消、关闭、删除，路由前缀 /api/v1/purchase-orders。
 * 类级 purchaseorder:view 覆盖所有读；转单与提交固定要 purchaseorder:edit，改单、取消、关闭、删除用
 * hasAnyAuthority 叠加 update/cancel/close/delete 变体码，便于把"能关单"与"能删单"分开授权。
 * 刻意不做：不判断可收量、不写收货与库存——收货在 /api/v1/receipts，本类只调 PurchaseOrderService。
 */
@RestController @RequestMapping("/api/v1") @PreAuthorize("hasAuthority('purchaseorder:view')")
public class PurchaseOrderController {
    private final PurchaseOrderService service;public PurchaseOrderController(PurchaseOrderService service){this.service=service;}
    // 三个读端点：purchaseorder:view；列表与分页按行级范围过滤，详情含行项目、供应商与已收数量。
    @GetMapping("/purchase-orders") public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.ok(service.list());}
    @GetMapping("/purchase-orders/{id}") public ApiResponse<Map<String,Object>> detail(@PathVariable Long id){return ApiResponse.ok(service.detail(id));}
    @GetMapping("/purchase-orders/page") public ApiResponse<Map<String,Object>> page(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(service.page(keyword,status,current,size));}
    // 由申请转单：purchaseorder:edit；先锁申请行确认状态未变，再校验供应商资质与默认价。
    @PostMapping("/requisitions/{id}/convert") @PreAuthorize("hasAuthority('purchaseorder:edit')") public ApiResponse<Map<String,Object>> convert(@PathVariable Long id,@Valid @RequestBody PurchaseOrderRequest r){return ApiResponse.ok(Map.of("id",service.convert(id,r)));}
    @PutMapping("/purchase-orders/{id}") @PreAuthorize("hasAnyAuthority('purchaseorder:edit','purchaseorder:update')") public ApiResponse<Void> update(@PathVariable Long id,@Valid @RequestBody PurchaseOrderUpdateRequest r){service.update(id,r);return ApiResponse.ok(null);}
    // 提交 / 改单：purchaseorder:edit（改单可叠 purchaseorder:update）；只在草稿态可改，提交后冻结。
    @PostMapping("/purchase-orders/{id}/submit") @PreAuthorize("hasAuthority('purchaseorder:edit')") public ApiResponse<Void> submit(@PathVariable Long id){service.submit(id);return ApiResponse.ok(null);}
    // 取消 / 关闭 / 删除：各自变体码；取消要原因，关闭用于收尾未收完的订单，删除只对草稿态生效。
    @PatchMapping("/purchase-orders/{id}/cancel") @PreAuthorize("hasAnyAuthority('purchaseorder:edit','purchaseorder:cancel')") public ApiResponse<Void> cancel(@PathVariable Long id,@Valid @RequestBody ReasonRequest r){service.cancel(id,r);return ApiResponse.ok("订单已取消",null);}
    @PatchMapping("/purchase-orders/{id}/close") @PreAuthorize("hasAnyAuthority('purchaseorder:edit','purchaseorder:close')") public ApiResponse<Void> close(@PathVariable Long id){service.close(id);return ApiResponse.ok("订单已关闭",null);}
    @DeleteMapping("/purchase-orders/{id}") @PreAuthorize("hasAnyAuthority('purchaseorder:edit','purchaseorder:delete')") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok(null);}
}
