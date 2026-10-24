package com.procuremind.platform.system;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SystemController {
    private final SystemManagementService service;
    public SystemController(SystemManagementService service) { this.service = service; }

    @GetMapping("/departments") ApiResponse<List<Map<String,Object>>> departments() { return ApiResponse.ok(service.departments()); }
    @PostMapping("/departments") @PreAuthorize("hasAuthority('system:department:edit')") ApiResponse<Map<String,Object>> createDepartment(@Valid @RequestBody SystemManagementService.DepartmentRequest request) { return ApiResponse.ok(Map.of("id", service.createDepartment(request))); }
    @PutMapping("/departments/{id}") @PreAuthorize("hasAuthority('system:department:edit')") ApiResponse<Void> updateDepartment(@PathVariable Long id, @Valid @RequestBody SystemManagementService.DepartmentRequest request) { service.updateDepartment(id, request); return ApiResponse.ok(null); }
    @PatchMapping("/departments/{id}/status") @PreAuthorize("hasAuthority('system:department:edit')") ApiResponse<Void> departmentStatus(@PathVariable Long id, @Valid @RequestBody SystemManagementService.StatusRequest request) { service.setDepartmentStatus(id, request.enabled()); return ApiResponse.ok(null); }
    @DeleteMapping("/departments/{id}") @PreAuthorize("hasAuthority('system:department:edit')") ApiResponse<Void> deleteDepartment(@PathVariable Long id) { service.deleteDepartment(id); return ApiResponse.ok(null); }

    @GetMapping("/users") @PreAuthorize("hasAuthority('system:user:view')") ApiResponse<List<Map<String,Object>>> users() { return ApiResponse.ok(service.users()); }
    @GetMapping("/users/{id}") @PreAuthorize("hasAuthority('system:user:view')") ApiResponse<Map<String,Object>> user(@PathVariable Long id) { return ApiResponse.ok(service.user(id)); }
    @PostMapping("/users") @PreAuthorize("hasAuthority('system:user:create')") ApiResponse<Map<String,Object>> createUser(@Valid @RequestBody SystemManagementService.UserCreateRequest request) { return ApiResponse.ok(Map.of("id", service.createUser(request))); }
    @PutMapping("/users/{id}") @PreAuthorize("hasAuthority('system:user:edit')") ApiResponse<Void> updateUser(@PathVariable Long id, @Valid @RequestBody SystemManagementService.UserUpdateRequest request) { service.updateUser(id, request); return ApiResponse.ok(null); }
    @PatchMapping("/users/{id}/status") @PreAuthorize("hasAuthority('system:user:disable')") ApiResponse<Void> userStatus(@PathVariable Long id, @Valid @RequestBody SystemManagementService.StatusRequest request) { service.setUserStatus(id, request.enabled()); return ApiResponse.ok(null); }
    @PostMapping("/users/{id}/reset-password") @PreAuthorize("hasAuthority('system:user:reset-password')") ApiResponse<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody SystemManagementService.ResetPasswordRequest request) { service.resetPassword(id, request.password()); return ApiResponse.ok(null); }
    @DeleteMapping("/users/{id}") @PreAuthorize("hasAuthority('system:user:edit')") ApiResponse<Void> deleteUser(@PathVariable Long id) { service.deleteUser(id); return ApiResponse.ok(null); }

    @GetMapping("/roles") @PreAuthorize("hasAuthority('system:user:view')") ApiResponse<List<Map<String,Object>>> roles() { return ApiResponse.ok(service.roles()); }
    @PostMapping("/roles") @PreAuthorize("hasAuthority('system:role:edit')") ApiResponse<Map<String,Object>> createRole(@Valid @RequestBody SystemManagementService.RoleRequest request) { return ApiResponse.ok(Map.of("id", service.createRole(request))); }
    @PutMapping("/roles/{id}") @PreAuthorize("hasAuthority('system:role:edit')") ApiResponse<Void> updateRole(@PathVariable Long id, @Valid @RequestBody SystemManagementService.RoleRequest request) { service.updateRole(id, request); return ApiResponse.ok(null); }
    @PatchMapping("/roles/{id}/status") @PreAuthorize("hasAuthority('system:role:edit')") ApiResponse<Void> roleStatus(@PathVariable Long id, @Valid @RequestBody SystemManagementService.StatusRequest request) { service.setRoleStatus(id, request.enabled()); return ApiResponse.ok(null); }
    @DeleteMapping("/roles/{id}") @PreAuthorize("hasAuthority('system:role:edit')") ApiResponse<Void> deleteRole(@PathVariable Long id) { service.deleteRole(id); return ApiResponse.ok(null); }

    @GetMapping("/permissions") @PreAuthorize("hasAuthority('system:user:view')") ApiResponse<List<Map<String,Object>>> permissions() { return ApiResponse.ok(service.permissions()); }
}
