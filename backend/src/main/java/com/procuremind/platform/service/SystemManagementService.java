package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.SystemMapper;
import com.procuremind.platform.utils.SecurityUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.procuremind.platform.pojo.request.DepartmentRequest;
import com.procuremind.platform.pojo.request.RoleRequest;
import com.procuremind.platform.pojo.request.UserCreateRequest;
import com.procuremind.platform.pojo.request.UserUpdateRequest;

@Service
public class SystemManagementService {
    private static final Set<String> DATA_SCOPES = Set.of("ALL", "DEPARTMENT", "DEPARTMENT_AND_CHILDREN", "SELF");
    private final SystemMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public SystemManagementService(SystemMapper mapper, PasswordEncoder passwordEncoder, AuthService authService) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    public List<Map<String, Object>> users() {
        List<Map<String, Object>> rows = mapper.listUsers();
        rows.forEach(this::attachRoles);
        return rows;
    }

    public Map<String, Object> user(Long id) {
        Map<String, Object> row = mapper.findUser(id);
        if (row == null) throw notFound("用户不存在");
        attachRoles(row);
        return row;
    }

    private void attachRoles(Map<String, Object> row) {
        Long id = Long.valueOf(row.get("id").toString());
        row.put("roleIds", mapper.roleIdsOf(id));
        row.put("roles", mapper.roleNamesOf(id));
    }

    @Transactional
    public Long createUser(UserCreateRequest request) {
        requireDepartment(request.departmentId(), true);
        requireRoles(request.roleIds());
        validatePassword(request.password());
        Long operator = SecurityUtils.currentUser().id();
        mapper.insertUser(request.username(), passwordEncoder.encode(request.password()), request.displayName(),
                request.departmentId(), operator);
        Long id = mapper.findUserIdByUsername(request.username());
        replaceUserRoles(id, request.roleIds());
        return id;
    }

    @Transactional
    public void updateUser(Long id, UserUpdateRequest request) {
        requireUser(id);
        requireDepartment(request.departmentId(), true);
        requireRoles(request.roleIds());
        protectLastAdminRole(id, request.roleIds().contains(adminRoleId()));
        mapper.updateUser(id, request.displayName(), request.departmentId(), SecurityUtils.currentUser().id());
        replaceUserRoles(id, request.roleIds());
        authService.revokeUserSessions(id);
    }

    @Transactional
    public void setUserStatus(Long id, boolean enabled) {
        requireUser(id);
        if (!enabled && id.equals(SecurityUtils.currentUser().id())) throw conflict("不能停用当前登录账号");
        if (!enabled) protectLastAdminRole(id, false);
        mapper.updateUserStatus(id, enabled, SecurityUtils.currentUser().id());
        if (!enabled) authService.revokeUserSessions(id);
    }

    public void resetPassword(Long id, String password) {
        requireUser(id);
        validatePassword(password);
        mapper.updatePassword(id, passwordEncoder.encode(password), SecurityUtils.currentUser().id());
        authService.revokeUserSessions(id);
    }

    @Transactional
    public void deleteUser(Long id) {
        requireUser(id);
        if (id.equals(SecurityUtils.currentUser().id())) throw conflict("不能删除当前登录账号");
        protectLastAdminRole(id, false);
        mapper.softDeleteUser(id, SecurityUtils.currentUser().id());
        mapper.deleteUserRoles(id);
        authService.revokeUserSessions(id);
    }

    public List<Map<String, Object>> departments() {
        return mapper.listDepartments();
    }

    @Transactional
    public Long createDepartment(DepartmentRequest request) {
        if (request.parentId() != null) requireDepartment(request.parentId(), true);
        Long operator = SecurityUtils.currentUser().id();
        mapper.insertDepartment(request.parentId(), request.code(), request.name(),
                request.sortOrder() == null ? 0 : request.sortOrder(), operator);
        return mapper.findDepartmentIdByCode(request.code());
    }

    @Transactional
    public void updateDepartment(Long id, DepartmentRequest request) {
        requireDepartment(id, false);
        if (request.parentId() != null) {
            requireDepartment(request.parentId(), true);
            ensureNoDepartmentCycle(id, request.parentId());
        }
        mapper.updateDepartment(id, request.parentId(), request.code(), request.name(),
                request.sortOrder() == null ? 0 : request.sortOrder(), SecurityUtils.currentUser().id());
    }

    public void setDepartmentStatus(Long id, boolean enabled) {
        requireDepartment(id, false);
        if (!enabled) {
            if (mapper.countEnabledChildren(id) > 0) throw conflict("请先停用下级部门");
            if (mapper.countEnabledUsersIn(id) > 0) throw conflict("部门仍有启用用户，不能停用");
        }
        mapper.updateDepartmentStatus(id, enabled, SecurityUtils.currentUser().id());
    }

    @Transactional
    public void deleteDepartment(Long id) {
        requireDepartment(id, false);
        if (mapper.countChildren(id) > 0) throw conflict("部门存在下级部门，不能删除");
        if (mapper.countUsersIn(id) > 0) throw conflict("部门已关联用户，不能删除");
        if (mapper.countRequisitionsIn(id) > 0) throw conflict("部门已关联采购申请，不能删除");
        mapper.softDeleteDepartment(id, SecurityUtils.currentUser().id());
    }

    public List<Map<String, Object>> roles() {
        List<Map<String, Object>> rows = mapper.listRoles();
        rows.forEach(row -> row.put("permissionIds", mapper.rolePermissionIds(Long.valueOf(row.get("id").toString()))));
        return rows;
    }

    public List<Map<String, Object>> permissions() {
        return mapper.listPermissions();
    }

    @Transactional
    public Long createRole(RoleRequest request) {
        validateDataScope(request.dataScope());
        requirePermissions(request.permissionIds());
        Long operator = SecurityUtils.currentUser().id();
        mapper.insertRole(request.code(), request.name(), request.dataScope(), operator);
        Long id = mapper.findRoleIdByCode(request.code());
        replaceRolePermissions(id, normalizePermissions(request.permissionIds()));
        return id;
    }

    @Transactional
    public void updateRole(Long id, RoleRequest request) {
        Map<String, Object> role = requireRole(id);
        if ("ADMIN".equals(role.get("code"))) throw conflict("系统管理员角色不允许修改");
        validateDataScope(request.dataScope());
        requirePermissions(request.permissionIds());
        mapper.updateRole(id, request.name(), request.dataScope(), SecurityUtils.currentUser().id());
        replaceRolePermissions(id, normalizePermissions(request.permissionIds()));
    }

    public void setRoleStatus(Long id, boolean enabled) {
        Map<String, Object> role = requireRole(id);
        if ("ADMIN".equals(role.get("code")) && !enabled) throw conflict("系统管理员角色不能停用");
        mapper.updateRoleStatus(id, enabled, SecurityUtils.currentUser().id());
    }

    @Transactional
    public void deleteRole(Long id) {
        Map<String, Object> role = requireRole(id);
        if (Boolean.parseBoolean(role.get("builtIn").toString())) throw conflict("内置角色不能删除");
        if (mapper.countUsersOfRole(id) > 0) throw conflict("角色已分配给用户，不能删除");
        mapper.deleteRolePermissions(id);
        mapper.softDeleteRole(id, SecurityUtils.currentUser().id());
    }

    private void replaceUserRoles(Long userId, Set<Long> roleIds) {
        mapper.deleteUserRoles(userId);
        roleIds.forEach(roleId -> mapper.insertUserRole(userId, roleId));
    }

    private void replaceRolePermissions(Long roleId, Set<Long> permissionIds) {
        mapper.deleteRolePermissions(roleId);
        permissionIds.forEach(permissionId -> mapper.insertRolePermission(roleId, permissionId));
    }

    private void requireUser(Long id) {
        if (mapper.countUser(id) == 0) throw notFound("用户不存在");
    }

    private void requireDepartment(Long id, boolean enabled) {
        int found = enabled ? mapper.countEnabledDepartment(id) : mapper.countDepartment(id);
        if (found == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "部门不存在或不可用");
    }

    private void requireRoles(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "至少分配一个角色");
        long valid = ids.stream().filter(id -> mapper.countEnabledRole(id) == 1).count();
        if (valid != ids.size()) throw new BusinessException(HttpStatus.BAD_REQUEST, "包含不存在或已停用的角色");
    }

    private void requirePermissions(Set<Long> ids) {
        if (ids == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "权限集合不能为空");
        long valid = ids.stream().filter(id -> mapper.countPermission(id) == 1).count();
        if (valid != ids.size()) throw new BusinessException(HttpStatus.BAD_REQUEST, "包含不存在的权限");
    }

    private Set<Long> normalizePermissions(Set<Long> ids) {
        Set<Long> normalized = new HashSet<>(ids);
        for (Long id : ids) {
            normalized.addAll(mapper.permissionParentIds(id));
        }
        return normalized;
    }

    private Map<String, Object> requireRole(Long id) {
        Map<String, Object> role = mapper.findRole(id);
        if (role == null) throw notFound("角色不存在");
        return role;
    }

    private void ensureNoDepartmentCycle(Long id, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long current = parentId;
        while (current != null) {
            if (current.equals(id) || !visited.add(current)) throw conflict("上级部门设置会形成循环");
            current = mapper.findDepartmentParent(current);
        }
    }

    private Long adminRoleId() {
        return mapper.findAdminRoleId();
    }

    private void protectLastAdminRole(Long userId, boolean remainsAdmin) {
        boolean currentlyAdmin = mapper.countAdminRoleOf(userId) > 0;
        if (currentlyAdmin && !remainsAdmin && mapper.countActiveAdmins() <= 1) {
            throw conflict("必须保留至少一个可用的系统管理员");
        }
    }

    private void validateDataScope(String dataScope) {
        if (!DATA_SCOPES.contains(dataScope)) throw new BusinessException(HttpStatus.BAD_REQUEST, "数据范围无效");
    }

    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "密码编码后不能超过72字节");
        }
    }

    private BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    

    

    

    

    

    
}
