package com.procuremind.platform.requisition;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.Map;

@RestController @RequestMapping("/api/v1/requisitions")
public class RequisitionController {
    private final RequisitionService service;public RequisitionController(RequisitionService service){this.service=service;}
    @GetMapping public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.ok(service.list());}
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> detail(@PathVariable Long id){return ApiResponse.ok(service.detail(id));}
    @PostMapping @PreAuthorize("hasAuthority('requisition:create')") public ApiResponse<Map<String,Object>> create(@Valid @RequestBody RequisitionService.Request r){return ApiResponse.ok(Map.of("id",service.create(r)));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('requisition:create')") public ApiResponse<Void> update(@PathVariable Long id,@Valid @RequestBody RequisitionService.Request r){service.update(id,r);return ApiResponse.ok(null);}
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('requisition:submit')") public ApiResponse<Void> submit(@PathVariable Long id){service.submit(id);return ApiResponse.ok("已提交审批",null);}
}
