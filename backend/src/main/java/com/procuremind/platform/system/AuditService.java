package com.procuremind.platform.system;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Map<String,Object>> list(){return jdbc.queryForList("select cast(id as char) id,cast(user_id as char) userId,username,method,path,result,error_message errorMessage,request_id requestId,duration_ms durationMs,created_at createdAt from sys_operation_audit order by id desc limit 500");}
}
