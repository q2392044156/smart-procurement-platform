package com.procuremind.platform.workflow;

import com.procuremind.platform.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;

@RestController @RequestMapping("/api/v1/workflow")
public class WorkflowController {
    private final WorkflowService service;public WorkflowController(WorkflowService service){this.service=service;}
    @GetMapping("/tasks") @PreAuthorize("hasAuthority('workflow:approve')") public ApiResponse<List<Map<String,Object>>> todo(){return ApiResponse.ok(service.todo());}
    @GetMapping("/tasks/done") @PreAuthorize("hasAuthority('workflow:approve')") public ApiResponse<List<Map<String,Object>>> done(){return ApiResponse.ok(service.done());}
    @PostMapping("/{taskId}/approve") @PreAuthorize("hasAuthority('workflow:approve')") public ApiResponse<Void> approve(@PathVariable String taskId,@RequestBody(required=false) Decision r){service.approve(taskId,r==null?null:r.comment());return ApiResponse.ok(null);}
    @PostMapping("/{taskId}/reject") @PreAuthorize("hasAuthority('workflow:approve')") public ApiResponse<Void> reject(@PathVariable String taskId,@RequestBody Decision r){service.reject(taskId,r.comment());return ApiResponse.ok(null);}
    @GetMapping("/timeline/{businessType}/{businessId}") @PreAuthorize("hasAnyAuthority('requisition:view','purchaseorder:view')") public ApiResponse<List<Map<String,Object>>> timeline(@PathVariable String businessType,@PathVariable Long businessId){return ApiResponse.ok(service.timeline(businessType.toUpperCase(),businessId));}
    public record Decision(String comment){}
}
