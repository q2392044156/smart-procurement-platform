package com.procuremind.platform.agentintegration;

import com.procuremind.platform.common.ApiResponse;
import com.procuremind.platform.idempotency.IdempotencyService;
import com.procuremind.platform.purchaseorder.PurchaseOrderService;
import com.procuremind.platform.requisition.RequisitionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent 写入工具面：申请创建/提交/取消、申请转订单、订单取消。
 *
 * <p>所有写操作复用领域 Service 的事务与校验，并通过 {@link IdempotencyService}
 * 按“操作 + 用户 + 请求内容”去重；客户端也可显式传 {@code Idempotency-Key}。</p>
 */
@RestController
@RequestMapping("/api")
public class WriteAgentController {
    private final RequisitionService requisitions;
    private final PurchaseOrderService orders;
    private final IdempotencyService idempotency;

    public WriteAgentController(RequisitionService requisitions, PurchaseOrderService orders,
                                IdempotencyService idempotency) {
        this.requisitions = requisitions;
        this.orders = orders;
        this.idempotency = idempotency;
    }

    @PostMapping("/requisitions")
    @PreAuthorize("hasAuthority('requisition:create')")
    public ApiResponse<Map<String, Object>> createRequisition(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody RequisitionService.Request request) {
        return ApiResponse.ok(idempotency.execute(key, "AGENT_CREATE_REQUISITION", request, () -> {
            Long id = requisitions.create(request);
            return requisitions.detail(id);
        }));
    }

    @PostMapping("/requisitions/{id}/submit")
    @PreAuthorize("hasAuthority('requisition:create')")
    public ApiResponse<Map<String, Object>> submitRequisition(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @PathVariable Long id) {
        return ApiResponse.ok(idempotency.execute(key, "AGENT_SUBMIT_REQUISITION", Map.of("id", id), () -> {
            requisitions.submit(id);
            return requisitions.detail(id);
        }));
    }

    @PostMapping("/requisitions/{id}/cancel")
    @PreAuthorize("hasAuthority('requisition:create')")
    public ApiResponse<Map<String, Object>> cancelRequisition(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @PathVariable Long id,
            @Valid @RequestBody RequisitionService.ReasonRequest request) {
        return ApiResponse.ok(idempotency.execute(key, "AGENT_CANCEL_REQUISITION",
                Map.of("id", id, "request", request), () -> {
                    requisitions.cancel(id, request);
                    return requisitions.detail(id);
                }));
    }

    @PostMapping("/requisitions/{id}/convert")
    @PreAuthorize("hasAuthority('purchaseorder:edit')")
    public ApiResponse<Map<String, Object>> convertRequisition(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderService.Request request) {
        return ApiResponse.ok(idempotency.execute(key, "AGENT_CONVERT_REQUISITION",
                Map.of("id", id, "request", request), () -> {
                    Long orderId = orders.convert(id, request);
                    return orders.detail(orderId);
                }));
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAuthority('purchaseorder:edit')")
    public ApiResponse<Map<String, Object>> cancelOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderService.ReasonRequest request) {
        return ApiResponse.ok(idempotency.execute(key, "AGENT_CANCEL_ORDER",
                Map.of("id", id, "request", request), () -> {
                    orders.cancel(id, request);
                    return orders.detail(id);
                }));
    }
}
