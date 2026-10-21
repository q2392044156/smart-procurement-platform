package com.procuremind.platform.system;

import com.procuremind.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserDirectory {
    private final JdbcTemplate jdbc;
    public UserDirectory(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UserRecord byUsername(String username) {
        List<UserRecord> rows = jdbc.query("select id,username,password_hash,display_name,department_id,enabled from sys_user where username=? and deleted=0",
                (rs, n) -> new UserRecord(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                        rs.getString("display_name"), rs.getLong("department_id"), rs.getBoolean("enabled")), username);
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        return rows.getFirst();
    }
    public CurrentUser current(Long id) {
        List<CurrentUser> rows = jdbc.query("select id,username,display_name,department_id from sys_user where id=? and enabled=1 and deleted=0",
                (rs, n) -> {
                    Set<String> roles = new HashSet<>(jdbc.queryForList("select r.code from sys_role r join sys_user_role ur on ur.role_id=r.id where ur.user_id=?", String.class, id));
                    Set<String> permissions = new HashSet<>(jdbc.queryForList("select distinct p.code from sys_permission p join sys_role_permission rp on rp.permission_id=p.id join sys_user_role ur on ur.role_id=rp.role_id where ur.user_id=?", String.class, id));
                    return new CurrentUser(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getLong("department_id"), roles, permissions);
                }, id);
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.UNAUTHORIZED, "账号不可用");
        return rows.getFirst();
    }
    public record UserRecord(Long id, String username, String passwordHash, String displayName, Long departmentId, boolean enabled) {}
}
