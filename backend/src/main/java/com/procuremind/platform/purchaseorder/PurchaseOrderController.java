package com.procuremind.platform.purchaseorder;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;

@RestController @RequestMapping("/api/v1") @PreAuthorize("hasAuthority('purchaseorder:view')")
public class PurchaseOrderController {
    private final PurchaseOrderService service;public PurchaseOrderController(PurchaseOrderService service){this.service=service;}
    @GetMapping("/purchase-orders") public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.ok(service.list());}
    @GetMapping("/purchase-orders/{id}") public ApiResponse<Map<String,Object>> detail(@PathVariable Long id){return ApiResponse.ok(service.detail(id));}
    @PostMapping("/requisitions/{id}/convert") @PreAuthorize("hasAuthority('purchaseorder:edit')") public ApiResponse<Map<String,Object>> convert(@PathVariable Long id,@Valid @RequestBody PurchaseOrderService.Request r){return ApiResponse.ok(Map.of("id",service.convert(id,r)));}
    @PostMapping("/purchase-orders/{id}/submit") @PreAuthorize("hasAuthority('purchaseorder:edit')") public ApiResponse<Void> submit(@PathVariable Long id){service.submit(id);return ApiResponse.ok(null);}
}
