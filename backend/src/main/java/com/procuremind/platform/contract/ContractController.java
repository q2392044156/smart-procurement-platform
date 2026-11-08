package com.procuremind.platform.contract;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 合同管理接口。 */
@RestController
@RequestMapping("/api/v1/contracts")
@PreAuthorize("hasAuthority('contract:view')")
public class ContractController {
    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> page(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int current,
                                                 @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.page(keyword, status, current, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('contract:edit')")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody ContractService.CreateRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('contract:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ContractService.UpdateRequest request) {
        service.update(id, request);
        return ApiResponse.ok("合同已更新", null);
    }

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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('contract:edit')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
