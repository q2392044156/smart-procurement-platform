package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SystemMapper {

    List<Map<String, Object>> listUsers();

    Map<String, Object> findUser(@Param("id") Long id);

    List<String> roleIdsOf(@Param("userId") Long userId);

    List<String> roleNamesOf(@Param("userId") Long userId);

    int countUser(@Param("id") Long id);

    int insertUser(@Param("username") String username, @Param("passwordHash") String passwordHash,
                   @Param("displayName") String displayName, @Param("departmentId") Long departmentId,
                   @Param("operator") Long operator);

    Long findUserIdByUsername(@Param("username") String username);

    int updateUser(@Param("id") Long id, @Param("displayName") String displayName,
                   @Param("departmentId") Long departmentId, @Param("operator") Long operator);

    int updateUserStatus(@Param("id") Long id, @Param("enabled") boolean enabled, @Param("operator") Long operator);

    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash, @Param("operator") Long operator);

    int softDeleteUser(@Param("id") Long id, @Param("operator") Long operator);

    int deleteUserRoles(@Param("userId") Long userId);

    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int countEnabledRole(@Param("id") Long id);

    int countAdminRoleOf(@Param("userId") Long userId);

    int countActiveAdmins();

    Long findAdminRoleId();

    List<Map<String, Object>> listDepartments();

    int countDepartment(@Param("id") Long id);

    int countEnabledDepartment(@Param("id") Long id);

    int insertDepartment(@Param("parentId") Long parentId, @Param("code") String code,
                         @Param("name") String name, @Param("sortOrder") int sortOrder,
                         @Param("operator") Long operator);

    Long findDepartmentIdByCode(@Param("code") String code);

    int updateDepartment(@Param("id") Long id, @Param("parentId") Long parentId, @Param("code") String code,
                         @Param("name") String name, @Param("sortOrder") int sortOrder,
                         @Param("operator") Long operator);

    int updateDepartmentStatus(@Param("id") Long id, @Param("enabled") boolean enabled, @Param("operator") Long operator);

    int softDeleteDepartment(@Param("id") Long id, @Param("operator") Long operator);

    Long findDepartmentParent(@Param("id") Long id);

    int countEnabledChildren(@Param("parentId") Long parentId);

    int countChildren(@Param("parentId") Long parentId);

    int countEnabledUsersIn(@Param("departmentId") Long departmentId);

    int countUsersIn(@Param("departmentId") Long departmentId);

    int countRequisitionsIn(@Param("departmentId") Long departmentId);

    List<Map<String, Object>> listRoles();

    List<String> rolePermissionIds(@Param("roleId") Long roleId);

    Map<String, Object> findRole(@Param("id") Long id);

    int insertRole(@Param("code") String code, @Param("name") String name,
                   @Param("dataScope") String dataScope, @Param("operator") Long operator);

    Long findRoleIdByCode(@Param("code") String code);

    int updateRole(@Param("id") Long id, @Param("name") String name,
                   @Param("dataScope") String dataScope, @Param("operator") Long operator);

    int updateRoleStatus(@Param("id") Long id, @Param("enabled") boolean enabled, @Param("operator") Long operator);

    int softDeleteRole(@Param("id") Long id, @Param("operator") Long operator);

    int countUsersOfRole(@Param("roleId") Long roleId);

    List<Map<String, Object>> listPermissions();

    int countPermission(@Param("id") Long id);

    List<Long> permissionParentIds(@Param("id") Long id);

    int deleteRolePermissions(@Param("roleId") Long roleId);

    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
