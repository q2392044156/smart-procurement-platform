package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 系统管理四组聚合（用户/部门/角色/权限码）的读写，对应 resources/mapper/SystemMapper.xml；无行锁。
 * 用户与角色、角色与权限是多对多，写操作一律“先 delete 全部、再 insert 当前集合”，不做差分。
 * count* 方法集中在这里，供 Service 拼“不能删自己 / 不能让最后一个管理员失效 / 部门下还有人”等保护条件。
 */
@Mapper
public interface SystemMapper {

    // 用户组：listUsers/findUser 读，roleIdsOf/roleNamesOf 取角色，deleteUserRoles+insertUserRole 重写用户角色。
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

    // countEnabledRole/countAdminRoleOf/countActiveAdmins/findAdminRoleId 是“最后一个管理员”保护的三件套。
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

    // 部门组：findDepartmentParent 加上两级 countChildren 做循环与删除保护，count*In 统计部门下用户与申请。
    Long findDepartmentParent(@Param("id") Long id);

    int countEnabledChildren(@Param("parentId") Long parentId);

    int countChildren(@Param("parentId") Long parentId);

    int countEnabledUsersIn(@Param("departmentId") Long departmentId);

    int countUsersIn(@Param("departmentId") Long departmentId);

    int countRequisitionsIn(@Param("departmentId") Long departmentId);

    List<Map<String, Object>> listRoles();

    // 角色组与权限码组：rolePermissionIds 读、delete+insert 重写；countPermission 校验权限码真实存在。
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
