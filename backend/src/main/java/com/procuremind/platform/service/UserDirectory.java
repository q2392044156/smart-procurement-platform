package com.procuremind.platform.service;

import com.procuremind.platform.anno.CurrentUser;
import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.UserDirectoryMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.procuremind.platform.pojo.UserRecord;

/**
 * 认证侧的用户只读目录：把 sys_user 的行映射成 {@link UserRecord} 与 {@link CurrentUser}，不校验密码。
 *
 * <p>刻意不加缓存：每次登录与刷新都重新装配角色、权限码，后台改完权限后用户下一次签发令牌即生效。
 * 只读、无事务、无写操作；密码比对与失败计数都在 {@link AuthService}。</p>
 *
 * <p>依赖：UserDirectoryMapper（sys_user 与角色、权限关联表的只读查询）。</p>
 */
@Service
public class UserDirectory {
    private final UserDirectoryMapper mapper;
    public UserDirectory(UserDirectoryMapper mapper) { this.mapper = mapper; }

    /**
     * 查不到也按「用户名或密码错误」抛 401，与密码错误同一句文案，避免被用来枚举账号；
     * enabled 兼容 Boolean 与 0/1 两种驱动读数。
     */
    public UserRecord byUsername(String username) {
        Map<String, Object> row = mapper.findByUsername(username);
        if (row == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        return new UserRecord(((Number) row.get("id")).longValue(), (String) row.get("username"),
                (String) row.get("password_hash"), (String) row.get("display_name"),
                ((Number) row.get("department_id")).longValue(),
                Boolean.TRUE.equals(row.get("enabled")) || ((Number) row.get("enabled")).intValue() == 1);
    }

    /**
     * 签发令牌前重新装配身份：用户被停用或软删时 findCurrent 查不到，直接 401；
     * 角色与权限只取启用且未删除的记录，停用角色不会继续带权限。
     */
    public CurrentUser current(Long id) {
        Map<String, Object> row = mapper.findCurrent(id);
        if (row == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "账号不可用");
        Set<String> roles = new HashSet<>(mapper.roleCodes(id));
        Set<String> permissions = new HashSet<>(mapper.permissionCodes(id));
        return new CurrentUser(((Number) row.get("id")).longValue(), (String) row.get("username"),
                (String) row.get("display_name"), ((Number) row.get("department_id")).longValue(), roles, permissions);
    }

    
}
