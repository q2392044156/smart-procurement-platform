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

/**
 * 系统管理：用户、部门、角色与权限的后台维护，是 RBAC 数据的唯一写入口。
 *
 * <p>写方法都在事务里先做引用校验（部门、角色、权限必须存在且启用）再落库，关联表一律「全量删除 + 重插」。
 * 两条硬性不变式：至少保留一个可用的系统管理员；部门树不允许成环。用户侧变更（角色、状态、密码、删除）
 * 之后会吊销该用户的刷新令牌，已签发的 access token 最多再用到期，但无法续期。</p>
 *
 * <p>本类不做登录鉴权，密码复杂度由请求 DTO 的 {@code @Pattern} 把关，这里只挡 BCrypt 的 72 字节截断。
 * 依赖：SystemMapper（用户/部门/角色/权限及关联表）、PasswordEncoder（BCrypt）、AuthService（会话吊销）。</p>
 */
@Service
public class SystemManagementService {
    /** 数据范围白名单：先校验再落库，非法值一旦进库会让 DataScopeService 的 Scope.valueOf 运行时炸。 */
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

    /**
     * 建用户：部门必须启用、角色必须存在且启用、密码不超过 BCrypt 的 72 字节上限；新用户默认启用。
     * insert 不回填主键，所以按用户名反查 id，再整体写入角色关联。
     */
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

    /**
     * 改用户：先确认改完仍满足「至少一个管理员」，再落库；角色变化后吊销该用户刷新令牌，
     * 已签发的 access token 最多再用到期，但无法续期。
     */
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

    /** 启停：停用自己直接 409；停用前确认不会拔掉最后一个管理员，停用后吊销该用户会话。 */
    @Transactional
    public void setUserStatus(Long id, boolean enabled) {
        requireUser(id);
        if (!enabled && id.equals(SecurityUtils.currentUser().id())) throw conflict("不能停用当前登录账号");
        if (!enabled) protectLastAdminRole(id, false);
        mapper.updateUserStatus(id, enabled, SecurityUtils.currentUser().id());
        if (!enabled) authService.revokeUserSessions(id);
    }

    /** 管理员重置密码：不校验旧密码；改完吊销刷新令牌，旧会话无法续期。 */
    public void resetPassword(Long id, String password) {
        requireUser(id);
        validatePassword(password);
        mapper.updatePassword(id, passwordEncoder.encode(password), SecurityUtils.currentUser().id());
        authService.revokeUserSessions(id);
    }

    /** 删用户：软删 + 清角色关联；禁止删自己，也不允许删掉最后一个可用管理员。 */
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

    /** 建部门：上级必须存在且启用，sortOrder 缺省 0；编码唯一由库约束。 */
    @Transactional
    public Long createDepartment(DepartmentRequest request) {
        if (request.parentId() != null) requireDepartment(request.parentId(), true);
        Long operator = SecurityUtils.currentUser().id();
        mapper.insertDepartment(request.parentId(), request.code(), request.name(),
                request.sortOrder() == null ? 0 : request.sortOrder(), operator);
        return mapper.findDepartmentIdByCode(request.code());
    }

    /** 改部门：换上级前沿 parent 链上溯做环检测，不能把部门挂到自己的子孙下面。 */
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

    /** 停用部门前要求没有启用的下级部门与启用用户，避免活账号挂在停用部门下。 */
    public void setDepartmentStatus(Long id, boolean enabled) {
        requireDepartment(id, false);
        if (!enabled) {
            if (mapper.countEnabledChildren(id) > 0) throw conflict("请先停用下级部门");
            if (mapper.countEnabledUsersIn(id) > 0) throw conflict("部门仍有启用用户，不能停用");
        }
        mapper.updateDepartmentStatus(id, enabled, SecurityUtils.currentUser().id());
    }

    /** 删部门：有下级、有用户、有采购申请引用时都拒绝，只能先迁移再删。 */
    @Transactional
    public void deleteDepartment(Long id) {
        requireDepartment(id, false);
        if (mapper.countChildren(id) > 0) throw conflict("部门存在下级部门，不能删除");
        if (mapper.countUsersIn(id) > 0) throw conflict("部门已关联用户，不能删除");
        if (mapper.countRequisitionsIn(id) > 0) throw conflict("部门已关联采购申请，不能删除");
        mapper.softDeleteDepartment(id, SecurityUtils.currentUser().id());
    }

    /** 角色列表：附带 permissionIds，权限树由前端组装；内置 ADMIN 也会出现在列表里。 */
    public List<Map<String, Object>> roles() {
        List<Map<String, Object>> rows = mapper.listRoles();
        rows.forEach(row -> row.put("permissionIds", mapper.rolePermissionIds(Long.valueOf(row.get("id").toString()))));
        return rows;
    }

    public List<Map<String, Object>> permissions() {
        return mapper.listPermissions();
    }

    /** 建角色：数据范围必须四选一，权限会自动补齐祖先节点，保证菜单树能一路展开。 */
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

    /** 改角色：ADMIN 是内置角色，整条拒绝修改；其余角色可改名称、数据范围与权限。 */
    @Transactional
    public void updateRole(Long id, RoleRequest request) {
        Map<String, Object> role = requireRole(id);
        if ("ADMIN".equals(role.get("code"))) throw conflict("系统管理员角色不允许修改");
        validateDataScope(request.dataScope());
        requirePermissions(request.permissionIds());
        mapper.updateRole(id, request.name(), request.dataScope(), SecurityUtils.currentUser().id());
        replaceRolePermissions(id, normalizePermissions(request.permissionIds()));
    }

    /** 停用角色：ADMIN 不能停用，否则管理员会整体失去权限来源。 */
    public void setRoleStatus(Long id, boolean enabled) {
        Map<String, Object> role = requireRole(id);
        if ("ADMIN".equals(role.get("code")) && !enabled) throw conflict("系统管理员角色不能停用");
        mapper.updateRoleStatus(id, enabled, SecurityUtils.currentUser().id());
    }

    /** 删角色：内置角色与已分配给用户的角色都不能删；先清权限关联再软删。 */
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

    /** 勾选子权限时补上祖先：权限按 parent_code 组织，少一层菜单就渲染不出来。 */
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

    /** visited 兼作脏数据兜底：parent 链上出现重复节点也按环处理，避免死循环。 */
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

    /**
     * 最后管理员保护：只在「当前挂着 ADMIN、改完不再挂」时才数活跃管理员；
     * countActiveAdmins 只算启用且未删的用户，停用账号不充当兜底。
     */
    private void protectLastAdminRole(Long userId, boolean remainsAdmin) {
        boolean currentlyAdmin = mapper.countAdminRoleOf(userId) > 0;
        if (currentlyAdmin && !remainsAdmin && mapper.countActiveAdmins() <= 1) {
            throw conflict("必须保留至少一个可用的系统管理员");
        }
    }

    private void validateDataScope(String dataScope) {
        if (!DATA_SCOPES.contains(dataScope)) throw new BusinessException(HttpStatus.BAD_REQUEST, "数据范围无效");
    }

    /** BCrypt 只取前 72 字节，超长口令会被静默截断成同一个，所以在入口挡住。 */
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
