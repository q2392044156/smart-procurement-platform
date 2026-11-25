package com.procuremind.platform.controller;

import com.procuremind.platform.service.SystemManagementService;

import com.procuremind.platform.pojo.ApiResponse;
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
import com.procuremind.platform.pojo.request.UserCreateRequest;
import com.procuremind.platform.pojo.request.UserUpdateRequest;
import com.procuremind.platform.pojo.request.ResetPasswordRequest;
import com.procuremind.platform.pojo.request.SystemStatusRequest;
import com.procuremind.platform.pojo.request.DepartmentRequest;
import com.procuremind.platform.pojo.request.RoleRequest;

/**
 * 系统管理：部门、用户、角色、权限码，路由前缀 /api/v1/system；没有类级权限，逐端点声明 system:*。
 * 读统一用 system:user:view（部门/角色/权限码列表也复用它），写按对象拆码：department:edit、user:create、
 * user:edit、user:disable、user:reset-password、role:edit——禁用与重置密码单独成码，便于只授给管理员。
 * 刻意不做：不校验密码复杂度与角色互斥、不计算权限集合（都在 SystemService），也不做逻辑删除的级联处理。
 */
@RestController
@RequestMapping("/api/v1")
public class SystemController {
    private final SystemManagementService service;
    public SystemController(SystemManagementService service) { this.service = service; }

    // 部门：读 system:user:view；增改/改状态/删除需 system:department:edit，引用检查在 Service。
    @GetMapping("/departments") ApiResponse<List<Map<String,Object>>> departments() { return ApiResponse.ok(service.departments()); }
    @PostMapping("/departments") @PreAuthorize("hasAuthority('system:department:edit')") ApiResponse<Map<String,Object>> createDepartment(@Valid @RequestBody DepartmentRequest request) { return ApiResponse.ok(Map.of("id", service.createDepartment(request))); }
    @PutMapping("/departments/{id}") @PreAuthorize("hasAuthority('system:department:edit')") ApiResponse<Void> updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequest request) { service.updateDepartment(id, request); return ApiResponse.ok(null); }
    @PatchMapping("/departments/{id}/status") @PreAuthorize("hasAuthority('system:department:edit')") ApiResponse<Void> departmentStatus(@PathVariable Long id, @Valid @RequestBody SystemStatusRequest request) { service.setDepartmentStatus(id, request.enabled()); return ApiResponse.ok(null); }
    @DeleteMapping("/departments/{id}") @PreAuthorize("hasAuthority('system:department:edit')") ApiResponse<Void> deleteDepartment(@PathVariable Long id) { service.deleteDepartment(id); return ApiResponse.ok(null); }

    // 用户：读 system:user:view；建 system:user:create、改 system:user:edit、禁用 system:user:disable、
    // 重置密码 system:user:reset-password；密码只以入参出现，不回显、不落日志。
    @GetMapping("/users") @PreAuthorize("hasAuthority('system:user:view')") ApiResponse<List<Map<String,Object>>> users() { return ApiResponse.ok(service.users()); }
    @GetMapping("/users/{id}") @PreAuthorize("hasAuthority('system:user:view')") ApiResponse<Map<String,Object>> user(@PathVariable Long id) { return ApiResponse.ok(service.user(id)); }
    @PostMapping("/users") @PreAuthorize("hasAuthority('system:user:create')") ApiResponse<Map<String,Object>> createUser(@Valid @RequestBody UserCreateRequest request) { return ApiResponse.ok(Map.of("id", service.createUser(request))); }
    @PutMapping("/users/{id}") @PreAuthorize("hasAuthority('system:user:edit')") ApiResponse<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) { service.updateUser(id, request); return ApiResponse.ok(null); }
    @PatchMapping("/users/{id}/status") @PreAuthorize("hasAuthority('system:user:disable')") ApiResponse<Void> userStatus(@PathVariable Long id, @Valid @RequestBody SystemStatusRequest request) { service.setUserStatus(id, request.enabled()); return ApiResponse.ok(null); }
    @PostMapping("/users/{id}/reset-password") @PreAuthorize("hasAuthority('system:user:reset-password')") ApiResponse<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) { service.resetPassword(id, request.password()); return ApiResponse.ok(null); }
    @DeleteMapping("/users/{id}") @PreAuthorize("hasAuthority('system:user:edit')") ApiResponse<Void> deleteUser(@PathVariable Long id) { service.deleteUser(id); return ApiResponse.ok(null); }

    // 角色：读 system:user:view；增改/改状态/删除需 system:role:edit，解绑用户等前置检查在 Service。
    @GetMapping("/roles") @PreAuthorize("hasAuthority('system:user:view')") ApiResponse<List<Map<String,Object>>> roles() { return ApiResponse.ok(service.roles()); }
    @PostMapping("/roles") @PreAuthorize("hasAuthority('system:role:edit')") ApiResponse<Map<String,Object>> createRole(@Valid @RequestBody RoleRequest request) { return ApiResponse.ok(Map.of("id", service.createRole(request))); }
    @PutMapping("/roles/{id}") @PreAuthorize("hasAuthority('system:role:edit')") ApiResponse<Void> updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request) { service.updateRole(id, request); return ApiResponse.ok(null); }
    @PatchMapping("/roles/{id}/status") @PreAuthorize("hasAuthority('system:role:edit')") ApiResponse<Void> roleStatus(@PathVariable Long id, @Valid @RequestBody SystemStatusRequest request) { service.setRoleStatus(id, request.enabled()); return ApiResponse.ok(null); }
    @DeleteMapping("/roles/{id}") @PreAuthorize("hasAuthority('system:role:edit')") ApiResponse<Void> deleteRole(@PathVariable Long id) { service.deleteRole(id); return ApiResponse.ok(null); }

    // 权限码：读 system:user:view；只返回全量权限清单，供角色编辑页勾选，不分页。
    @GetMapping("/permissions") @PreAuthorize("hasAuthority('system:user:view')") ApiResponse<List<Map<String,Object>>> permissions() { return ApiResponse.ok(service.permissions()); }
}
