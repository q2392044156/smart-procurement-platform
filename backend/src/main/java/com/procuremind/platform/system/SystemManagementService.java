package com.procuremind.platform.system;

import com.procuremind.platform.common.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SystemManagementService {
    private static final Set<String> DATA_SCOPES = Set.of("ALL", "DEPARTMENT", "DEPARTMENT_AND_CHILDREN", "SELF");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public SystemManagementService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, AuthService authService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    public List<Map<String, Object>> users() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select cast(u.id as char) id,u.username,u.display_name displayName,
                       cast(u.department_id as char) departmentId,d.name department,u.enabled
                from sys_user u join sys_department d on d.id=u.department_id
                where u.deleted=0 order by u.id
                """);
        rows.forEach(row -> {
            Long id = Long.valueOf(row.get("id").toString());
            row.put("roleIds", jdbc.queryForList("select cast(r.id as char) from sys_role r join sys_user_role ur on ur.role_id=r.id where ur.user_id=? and r.deleted=0 order by r.id", String.class, id));
            row.put("roles", jdbc.queryForList("select r.name from sys_role r join sys_user_role ur on ur.role_id=r.id where ur.user_id=? and r.deleted=0 order by r.id", String.class, id));
        });
        return rows;
    }

    public Map<String, Object> user(Long id) {
        return users().stream().filter(row -> id.toString().equals(row.get("id"))).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    @Transactional
    public Long createUser(UserCreateRequest request) {
        requireDepartment(request.departmentId(), true);
        requireRoles(request.roleIds());
        validatePassword(request.password());
        Long operator = SecurityUtils.currentUser().id();
        jdbc.update("insert into sys_user(username,password_hash,display_name,department_id,enabled,created_by,updated_by) values(?,?,?,?,1,?,?)",
                request.username(), passwordEncoder.encode(request.password()), request.displayName(), request.departmentId(), operator, operator);
        Long id = jdbc.queryForObject("select id from sys_user where username=?", Long.class, request.username());
        replaceUserRoles(id, request.roleIds());
        return id;
    }

    @Transactional
    public void updateUser(Long id, UserUpdateRequest request) {
        requireUser(id);
        requireDepartment(request.departmentId(), true);
        requireRoles(request.roleIds());
        protectLastAdminRole(id, request.roleIds().contains(adminRoleId()));
        jdbc.update("update sys_user set display_name=?,department_id=?,updated_by=? where id=? and deleted=0",
                request.displayName(), request.departmentId(), SecurityUtils.currentUser().id(), id);
        replaceUserRoles(id, request.roleIds());
        authService.revokeUserSessions(id);
    }

    @Transactional
    public void setUserStatus(Long id, boolean enabled) {
        requireUser(id);
        if (!enabled && id.equals(SecurityUtils.currentUser().id())) throw conflict("不能停用当前登录账号");
        if (!enabled) protectLastAdminRole(id, false);
        jdbc.update("update sys_user set enabled=?,updated_by=? where id=? and deleted=0", enabled, SecurityUtils.currentUser().id(), id);
        if (!enabled) authService.revokeUserSessions(id);
    }

    public void resetPassword(Long id, String password) {
        requireUser(id);
        validatePassword(password);
        jdbc.update("update sys_user set password_hash=?,updated_by=? where id=? and deleted=0",
                passwordEncoder.encode(password), SecurityUtils.currentUser().id(), id);
        authService.revokeUserSessions(id);
    }

    @Transactional
    public void deleteUser(Long id) {
        requireUser(id);
        if (id.equals(SecurityUtils.currentUser().id())) throw conflict("不能删除当前登录账号");
        protectLastAdminRole(id, false);
        jdbc.update("update sys_user set enabled=0,deleted=1,updated_by=? where id=?", SecurityUtils.currentUser().id(), id);
        jdbc.update("delete from sys_user_role where user_id=?", id);
        authService.revokeUserSessions(id);
    }

    public List<Map<String, Object>> departments() {
        return jdbc.queryForList("""
                select cast(d.id as char) id,cast(d.parent_id as char) parentId,d.code,d.name,d.sort_order sortOrder,d.enabled,
                       (select count(*) from sys_user u where u.department_id=d.id and u.deleted=0) userCount
                from sys_department d where d.deleted=0 order by d.sort_order,d.id
                """);
    }

    @Transactional
    public Long createDepartment(DepartmentRequest request) {
        if (request.parentId() != null) requireDepartment(request.parentId(), true);
        Long operator = SecurityUtils.currentUser().id();
        jdbc.update("insert into sys_department(parent_id,code,name,sort_order,enabled,created_by,updated_by) values(?,?,?,?,1,?,?)",
                request.parentId(), request.code(), request.name(), request.sortOrder() == null ? 0 : request.sortOrder(), operator, operator);
        return jdbc.queryForObject("select id from sys_department where code=?", Long.class, request.code());
    }

    @Transactional
    public void updateDepartment(Long id, DepartmentRequest request) {
        requireDepartment(id, false);
        if (request.parentId() != null) {
            requireDepartment(request.parentId(), true);
            ensureNoDepartmentCycle(id, request.parentId());
        }
        jdbc.update("update sys_department set parent_id=?,code=?,name=?,sort_order=?,updated_by=? where id=? and deleted=0",
                request.parentId(), request.code(), request.name(), request.sortOrder() == null ? 0 : request.sortOrder(), SecurityUtils.currentUser().id(), id);
    }

    public void setDepartmentStatus(Long id, boolean enabled) {
        requireDepartment(id, false);
        if (!enabled) {
            if (count("select count(*) from sys_department where parent_id=? and deleted=0 and enabled=1", id) > 0) throw conflict("请先停用下级部门");
            if (count("select count(*) from sys_user where department_id=? and deleted=0 and enabled=1", id) > 0) throw conflict("部门仍有启用用户，不能停用");
        }
        jdbc.update("update sys_department set enabled=?,updated_by=? where id=? and deleted=0", enabled, SecurityUtils.currentUser().id(), id);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        requireDepartment(id, false);
        if (count("select count(*) from sys_department where parent_id=? and deleted=0", id) > 0) throw conflict("部门存在下级部门，不能删除");
        if (count("select count(*) from sys_user where department_id=? and deleted=0", id) > 0) throw conflict("部门已关联用户，不能删除");
        if (count("select count(*) from pr_requisition where department_id=? and deleted=0", id) > 0) throw conflict("部门已关联采购申请，不能删除");
        jdbc.update("update sys_department set enabled=0,deleted=1,updated_by=? where id=?", SecurityUtils.currentUser().id(), id);
    }

    public List<Map<String, Object>> roles() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select cast(r.id as char) id,r.code,r.name,r.data_scope dataScope,r.enabled,r.built_in builtIn,
                       (select count(*) from sys_user_role ur join sys_user u on u.id=ur.user_id and u.deleted=0 where ur.role_id=r.id) userCount
                from sys_role r where r.deleted=0 order by r.id
                """);
        rows.forEach(row -> row.put("permissionIds", jdbc.queryForList(
                "select cast(permission_id as char) from sys_role_permission where role_id=? order by permission_id",
                String.class, Long.valueOf(row.get("id").toString()))));
        return rows;
    }

    public List<Map<String, Object>> permissions() {
        return jdbc.queryForList("select cast(id as char) id,code,name,type,parent_code parentCode,sort_order sortOrder from sys_permission order by sort_order,id");
    }

    @Transactional
    public Long createRole(RoleRequest request) {
        validateDataScope(request.dataScope());
        requirePermissions(request.permissionIds());
        Long operator = SecurityUtils.currentUser().id();
        jdbc.update("insert into sys_role(code,name,data_scope,enabled,built_in,created_by,updated_by) values(?,?,?,1,0,?,?)",
                request.code(), request.name(), request.dataScope(), operator, operator);
        Long id = jdbc.queryForObject("select id from sys_role where code=?", Long.class, request.code());
        replaceRolePermissions(id, normalizePermissions(request.permissionIds()));
        return id;
    }

    @Transactional
    public void updateRole(Long id, RoleRequest request) {
        Map<String, Object> role = requireRole(id);
        if ("ADMIN".equals(role.get("code"))) throw conflict("系统管理员角色不允许修改");
        validateDataScope(request.dataScope());
        requirePermissions(request.permissionIds());
        jdbc.update("update sys_role set name=?,data_scope=?,updated_by=? where id=? and deleted=0",
                request.name(), request.dataScope(), SecurityUtils.currentUser().id(), id);
        replaceRolePermissions(id, normalizePermissions(request.permissionIds()));
    }

    public void setRoleStatus(Long id, boolean enabled) {
        Map<String, Object> role = requireRole(id);
        if ("ADMIN".equals(role.get("code")) && !enabled) throw conflict("系统管理员角色不能停用");
        jdbc.update("update sys_role set enabled=?,updated_by=? where id=? and deleted=0", enabled, SecurityUtils.currentUser().id(), id);
    }

    @Transactional
    public void deleteRole(Long id) {
        Map<String, Object> role = requireRole(id);
        if (Boolean.parseBoolean(role.get("builtIn").toString())) throw conflict("内置角色不能删除");
        if (count("select count(*) from sys_user_role where role_id=?", id) > 0) throw conflict("角色已分配给用户，不能删除");
        jdbc.update("delete from sys_role_permission where role_id=?", id);
        jdbc.update("update sys_role set enabled=0,deleted=1,updated_by=? where id=?", SecurityUtils.currentUser().id(), id);
    }

    private void replaceUserRoles(Long userId, Set<Long> roleIds) {
        jdbc.update("delete from sys_user_role where user_id=?", userId);
        roleIds.forEach(roleId -> jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", userId, roleId));
    }

    private void replaceRolePermissions(Long roleId, Set<Long> permissionIds) {
        jdbc.update("delete from sys_role_permission where role_id=?", roleId);
        permissionIds.forEach(permissionId -> jdbc.update("insert into sys_role_permission(role_id,permission_id) values(?,?)", roleId, permissionId));
    }

    private void requireUser(Long id) {
        if (count("select count(*) from sys_user where id=? and deleted=0", id) == 0) throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
    }

    private void requireDepartment(Long id, boolean enabled) {
        String sql = "select count(*) from sys_department where id=? and deleted=0" + (enabled ? " and enabled=1" : "");
        if (count(sql, id) == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "部门不存在或不可用");
    }

    private void requireRoles(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "至少分配一个角色");
        long valid = ids.stream().filter(id -> count("select count(*) from sys_role where id=? and deleted=0 and enabled=1", id) == 1).count();
        if (valid != ids.size()) throw new BusinessException(HttpStatus.BAD_REQUEST, "包含不存在或已停用的角色");
    }

    private void requirePermissions(Set<Long> ids) {
        if (ids == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "权限集合不能为空");
        long valid = ids.stream().filter(id -> count("select count(*) from sys_permission where id=?", id) == 1).count();
        if (valid != ids.size()) throw new BusinessException(HttpStatus.BAD_REQUEST, "包含不存在的权限");
    }

    private Set<Long> normalizePermissions(Set<Long> ids) {
        Set<Long> normalized = new HashSet<>(ids);
        for (Long id : ids) {
            List<Long> parents = jdbc.query("""
                    select parent.id from sys_permission child
                    join sys_permission parent on parent.code=child.parent_code
                    where child.id=?
                    """, (rs, n) -> rs.getLong(1), id);
            normalized.addAll(parents);
        }
        return normalized;
    }

    private Map<String, Object> requireRole(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select code,built_in builtIn from sys_role where id=? and deleted=0", id);
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "角色不存在");
        return rows.getFirst();
    }

    private void ensureNoDepartmentCycle(Long id, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long current = parentId;
        while (current != null) {
            if (current.equals(id) || !visited.add(current)) throw conflict("上级部门设置会形成循环");
            List<Long> parents = jdbc.query("select parent_id from sys_department where id=? and deleted=0", (rs, n) -> {
                long value = rs.getLong(1);
                return rs.wasNull() ? null : value;
            }, current);
            current = parents.isEmpty() ? null : parents.getFirst();
        }
    }

    private Long adminRoleId() {
        return jdbc.queryForObject("select id from sys_role where code='ADMIN' and deleted=0", Long.class);
    }

    private void protectLastAdminRole(Long userId, boolean remainsAdmin) {
        boolean currentlyAdmin = count("select count(*) from sys_user_role ur join sys_role r on r.id=ur.role_id where ur.user_id=? and r.code='ADMIN' and r.deleted=0", userId) > 0;
        if (currentlyAdmin && !remainsAdmin && count("select count(distinct u.id) from sys_user u join sys_user_role ur on ur.user_id=u.id join sys_role r on r.id=ur.role_id where u.deleted=0 and u.enabled=1 and r.code='ADMIN' and r.deleted=0 and r.enabled=1") <= 1)
            throw conflict("必须保留至少一个可用的系统管理员");
    }

    private void validateDataScope(String dataScope) {
        if (!DATA_SCOPES.contains(dataScope)) throw new BusinessException(HttpStatus.BAD_REQUEST, "数据范围无效");
    }

    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw new BusinessException(HttpStatus.BAD_REQUEST, "密码编码后不能超过72字节");
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    public record UserCreateRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{2,63}", message = "用户名格式不正确") String username,
            @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,72}$", message = "密码至少8位且需包含大小写字母、数字和特殊字符") String password,
            @NotBlank @Size(max = 100) String displayName,
            @NotNull @Positive Long departmentId,
            @NotEmpty Set<@Positive Long> roleIds) {}

    public record UserUpdateRequest(
            @NotBlank @Size(max = 100) String displayName,
            @NotNull @Positive Long departmentId,
            @NotEmpty Set<@Positive Long> roleIds) {}

    public record ResetPasswordRequest(
            @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,72}$", message = "密码至少8位且需包含大小写字母、数字和特殊字符") String password) {}

    public record StatusRequest(@NotNull Boolean enabled) {}

    public record DepartmentRequest(
            @Positive Long parentId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,49}", message = "部门编码需为大写字母开头") String code,
            @NotBlank @Size(max = 100) String name,
            Integer sortOrder) {}

    public record RoleRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}", message = "角色编码需为大写字母和下划线") String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank String dataScope,
            @NotNull Set<@Positive Long> permissionIds) {}
}
