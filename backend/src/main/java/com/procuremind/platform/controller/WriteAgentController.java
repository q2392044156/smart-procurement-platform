package com.procuremind.platform.controller;

import com.procuremind.platform.pojo.ApiResponse;
import com.procuremind.platform.service.IdempotencyService;
import com.procuremind.platform.service.PurchaseOrderService;
import com.procuremind.platform.service.RequisitionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.procuremind.platform.pojo.request.ReasonRequest;
import com.procuremind.platform.pojo.request.PurchaseOrderRequest;
import com.procuremind.platform.pojo.request.RequisitionRequest;

/**
 * Agent 写入工具面：申请创建/提交/取消、申请转订单、订单取消。
 *
 * <p>所有写操作复用领域 Service 的事务与校验，并通过 {@link IdempotencyService}
 * 按“操作 + 用户 + 请求内容”去重；客户端也可显式传 {@code Idempotency-Key}。</p>
 * <p>路径前缀 {@code /api}（不是 /api/v1），只经内部令牌认证；每个端点仍显式声明领域权限码，
 * 因为内部令牌只证明调用方是 Agent 服务，不证明它有这项业务权限。</p>
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

    // 创建申请：requisition:create；创建后回读详情，便于 Agent 拿到单号与状态。
    @PostMapping("/requisitions")
    @PreAuthorize("hasAuthority('requisition:create')")
    public ApiResponse<Map<String, Object>> createRequisition(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody RequisitionRequest request) {
        return ApiResponse.ok(idempotency.execute(key, "AGENT_CREATE_REQUISITION", request, () -> {
            Long id = requisitions.create(request);
            return requisitions.detail(id);
        }));
    }

    // 提交申请：requisition:create；状态机与预算校验在 RequisitionService，重复提交由幂等键吸收。
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

    // 取消申请：requisition:create，必须带原因；是否允许取消由 Service 按状态判定。
    @PostMapping("/requisitions/{id}/cancel")
    @PreAuthorize("hasAuthority('requisition:create')")
    public ApiResponse<Map<String, Object>> cancelRequisition(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @PathVariable Long id,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(idempotency.execute(key, "AGENT_CANCEL_REQUISITION",
                Map.of("id", id, "request", request), () -> {
                    requisitions.cancel(id, request);
                    return requisitions.detail(id);
                }));
    }

    // 申请转订单：purchaseorder:edit（不是 requisition 权限）；供应商资质与行项目由 Service 校验。
    @PostMapping("/requisitions/{id}/convert")
    @PreAuthorize("hasAuthority('purchaseorder:edit')")
    public ApiResponse<Map<String, Object>> convertRequisition(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderRequest request) {
        return ApiResponse.ok(idempotency.execute(key, "AGENT_CONVERT_REQUISITION",
                Map.of("id", id, "request", request), () -> {
                    Long orderId = orders.convert(id, request);
                    return orders.detail(orderId);
                }));
    }

    // 取消订单：purchaseorder:edit，必须带原因；是否允许取消由 Service 按订单状态判定。
    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAuthority('purchaseorder:edit')")
    public ApiResponse<Map<String, Object>> cancelOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @PathVariable Long id,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(idempotency.execute(key, "AGENT_CANCEL_ORDER",
                Map.of("id", id, "request", request), () -> {
                    orders.cancel(id, request);
                    return orders.detail(id);
                }));
    }
}
