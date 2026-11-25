package com.procuremind.platform.controller;

import com.procuremind.platform.service.ContractService;

import com.procuremind.platform.pojo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.procuremind.platform.pojo.request.ContractCreateRequest;
import com.procuremind.platform.pojo.request.ContractUpdateRequest;

    // 合同档案，路由前缀 /api/v1/contracts；类级 contract:view 可读，增改、启用、终止、删除均需 contract:edit。
    // 状态流转只有 activate/terminate 两个动作端点，不允许直接改状态字段，避免绕过状态机。
    // 本类不含状态机与供应商/订单关联校验，全在 ContractService；到期批量失效由 ContractMapper.expireDue 定时触发。
/** 合同管理接口。 */
@RestController
@RequestMapping("/api/v1/contracts")
@PreAuthorize("hasAuthority('contract:view')")
public class ContractController {
    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    // 分页列表：contract:view；keyword 模糊匹配合同号/名称，status 精确匹配状态。
    @GetMapping
    public ApiResponse<Map<String, Object>> page(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int current,
                                                 @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.page(keyword, status, current, size));
    }

    // 详情：contract:view；附带关联供应商与采购订单摘要。
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    // 新建 / 修改：contract:edit；合同号唯一性与金额、日期区间由 Service 校验。
    @PostMapping
    @PreAuthorize("hasAuthority('contract:edit')")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody ContractCreateRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('contract:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ContractUpdateRequest request) {
        service.update(id, request);
        return ApiResponse.ok("合同已更新", null);
    }

    // 启用 / 终止：contract:edit；非法状态迁移由 Service 拒绝，这里只做转发。
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('contract:edit')")
    public ApiResponse<Void> activate(@PathVariable Long id) {
        service.activate(id);
        return ApiResponse.ok("合同已启用", null);
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAuthority('contract:edit')")
    public ApiResponse<Void> terminate(@PathVariable Long id) {
        service.terminate(id);
        return ApiResponse.ok("合同已终止", null);
    }

    // 删除：contract:edit；软删除，是否仍被订单引用由 Service 判定。
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('contract:edit')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
