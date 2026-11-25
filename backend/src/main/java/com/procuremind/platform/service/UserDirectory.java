package com.procuremind.platform.service;

import com.procuremind.platform.anno.CurrentUser;
import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.UserDirectoryMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class UserDirectory {
    private final UserDirectoryMapper mapper;
    public UserDirectory(UserDirectoryMapper mapper) { this.mapper = mapper; }

    public UserRecord byUsername(String username) {
        Map<String, Object> row = mapper.findByUsername(username);
        if (row == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        return new UserRecord(((Number) row.get("id")).longValue(), (String) row.get("username"),
                (String) row.get("password_hash"), (String) row.get("display_name"),
                ((Number) row.get("department_id")).longValue(),
                Boolean.TRUE.equals(row.get("enabled")) || ((Number) row.get("enabled")).intValue() == 1);
    }

    public CurrentUser current(Long id) {
        Map<String, Object> row = mapper.findCurrent(id);
        if (row == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "账号不可用");
        Set<String> roles = new HashSet<>(mapper.roleCodes(id));
        Set<String> permissions = new HashSet<>(mapper.permissionCodes(id));
        return new CurrentUser(((Number) row.get("id")).longValue(), (String) row.get("username"),
                (String) row.get("display_name"), ((Number) row.get("department_id")).longValue(), roles, permissions);
    }

    public record UserRecord(Long id, String username, String passwordHash, String displayName, Long departmentId, boolean enabled) {}
}
