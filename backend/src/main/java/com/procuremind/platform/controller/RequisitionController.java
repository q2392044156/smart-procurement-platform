package com.procuremind.platform.controller;

import com.procuremind.platform.service.RequisitionService;

import com.procuremind.platform.pojo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;
import com.procuremind.platform.pojo.request.ReasonRequest;
import com.procuremind.platform.pojo.request.RequisitionRequest;

/**
 * 采购申请的全生命周期入口，路由前缀 /api/v1/requisitions；类级 requisition:view 覆盖所有读。
 * 写操作按动作拆码：建单与改单 requisition:create、提交 requisition:submit、取消 requisition:cancel、
 * 删除 requisition:delete——刻意不共用一个 requisition:edit，避免"能改"就等于"能提交/能删"。
 * 本类不做状态机、预算占用与行级范围判断，全在 RequisitionService（加行锁后按状态决策）。
 */
@RestController @RequestMapping("/api/v1/requisitions") @PreAuthorize("hasAuthority('requisition:view')")
public class RequisitionController {
    private final RequisitionService service;public RequisitionController(RequisitionService service){this.service=service;}
    // 三个读端点：requisition:view；列表与分页按 DataScopeService 的行级范围过滤，详情带行项目。
    @GetMapping public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.ok(service.list());}
    @GetMapping("/page") public ApiResponse<Map<String,Object>> page(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(service.page(keyword,status,current,size));}
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> detail(@PathVariable Long id){return ApiResponse.ok(service.detail(id));}
    // 建单 / 改单：requisition:create；只在草稿态可改，提交后字段冻结由 Service 判定。
    @PostMapping @PreAuthorize("hasAuthority('requisition:create')") public ApiResponse<Map<String,Object>> create(@Valid @RequestBody RequisitionRequest r){return ApiResponse.ok(Map.of("id",service.create(r)));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('requisition:create')") public ApiResponse<Void> update(@PathVariable Long id,@Valid @RequestBody RequisitionRequest r){service.update(id,r);return ApiResponse.ok(null);}
    // 提交：requisition:submit；校验必填行项目、预算与审批流，成功即进入审批中并生成待办。
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('requisition:submit')") public ApiResponse<Void> submit(@PathVariable Long id){service.submit(id);return ApiResponse.ok("已提交审批",null);}
    // 取消 / 删除：requisition:cancel 与 requisition:delete；取消要填原因，删除只对草稿态生效，均为软删除。
    @PatchMapping("/{id}/cancel") @PreAuthorize("hasAuthority('requisition:cancel')") public ApiResponse<Void> cancel(@PathVariable Long id,@Valid @RequestBody ReasonRequest r){service.cancel(id,r);return ApiResponse.ok("申请已取消",null);}
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('requisition:delete')") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok(null);}
}
