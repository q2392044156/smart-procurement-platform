package com.procuremind.platform.controller;

import com.procuremind.platform.service.WorkflowService;

import com.procuremind.platform.pojo.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;
import com.procuremind.platform.pojo.request.WorkflowDecision;

/**
 * 审批任务与业务时间线，路由前缀 /api/v1/workflow；无类级权限，逐端点声明。
 * 任务读写统一 workflow:approve（待办、已办、通过、驳回）；时间线按业务类型校验读权限：
 * 申请用 requisition:view、订单用 purchaseorder:view，任一满足即可。
 * 刻意不做：不在本类推进业务状态——approve/reject 只提交审批结果，业务侧状态由 WorkflowService 回调写回。
 */
@RestController @RequestMapping("/api/v1/workflow")
public class WorkflowController {
    private final WorkflowService service;public WorkflowController(WorkflowService service){this.service=service;}
    // 待办 / 已办：workflow:approve；只返回当前用户可处理的实例，不含他人任务。
    @GetMapping("/tasks") @PreAuthorize("hasAuthority('workflow:approve')") public ApiResponse<List<Map<String,Object>>> todo(){return ApiResponse.ok(service.todo());}
    @GetMapping("/tasks/done") @PreAuthorize("hasAuthority('workflow:approve')") public ApiResponse<List<Map<String,Object>>> done(){return ApiResponse.ok(service.done());}
    // 通过 / 驳回：workflow:approve；驳回必须带意见，通过的意见可选，结果由 Service 回调写回业务状态。
    @PostMapping("/{taskId}/approve") @PreAuthorize("hasAuthority('workflow:approve')") public ApiResponse<Void> approve(@PathVariable String taskId,@RequestBody(required=false) WorkflowDecision r){service.approve(taskId,r==null?null:r.comment());return ApiResponse.ok(null);}
    @PostMapping("/{taskId}/reject") @PreAuthorize("hasAuthority('workflow:approve')") public ApiResponse<Void> reject(@PathVariable String taskId,@RequestBody WorkflowDecision r){service.reject(taskId,r.comment());return ApiResponse.ok(null);}
    // 时间线：requisition:view 或 purchaseorder:view；businessType 统一转大写，返回按时间排序的审批轨迹。
    @GetMapping("/timeline/{businessType}/{businessId}") @PreAuthorize("hasAnyAuthority('requisition:view','purchaseorder:view')") public ApiResponse<List<Map<String,Object>>> timeline(@PathVariable String businessType,@PathVariable Long businessId){return ApiResponse.ok(service.timeline(businessType.toUpperCase(),businessId));}
    
}
