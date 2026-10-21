package com.procuremind.platform.system;

import com.procuremind.platform.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SystemController {
    private final JdbcTemplate jdbc;
    public SystemController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping("/departments") ApiResponse<List<Map<String,Object>>> departments() { return ApiResponse.ok(jdbc.queryForList("select cast(id as char) id,name,cast(parent_id as char) parentId,code from sys_department where deleted=0 order by sort_order")); }
    @GetMapping("/users") @PreAuthorize("hasAuthority('system:user:view')") ApiResponse<List<Map<String,Object>>> users() { return ApiResponse.ok(jdbc.queryForList("select cast(u.id as char) id,u.username,u.display_name displayName,d.name department,u.enabled from sys_user u left join sys_department d on d.id=u.department_id where u.deleted=0 order by u.id")); }
    @GetMapping("/roles") ApiResponse<List<Map<String,Object>>> roles() { return ApiResponse.ok(jdbc.queryForList("select cast(id as char) id,code,name,data_scope dataScope from sys_role where deleted=0 order by id")); }
    @GetMapping("/permissions") ApiResponse<List<Map<String,Object>>> permissions() { return ApiResponse.ok(jdbc.queryForList("select cast(id as char) id,code,name,type,parent_code parentCode from sys_permission order by sort_order,id")); }
}
