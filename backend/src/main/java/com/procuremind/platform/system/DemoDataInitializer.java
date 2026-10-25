package com.procuremind.platform.system;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc; private final PasswordEncoder encoder;
    public DemoDataInitializer(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc=jdbc; this.encoder=encoder; }
    @Override @Transactional public void run(ApplicationArguments args) {
        List<UserSeed> users=List.of(
                new UserSeed("admin","系统管理员","HQ","ADMIN","Admin@123"), new UserSeed("requester","研发申请人","RD","APPLICANT","Request@123"),
                new UserSeed("manager","研发部经理","RD","DEPT_MANAGER","Manager@123"), new UserSeed("purchaser","采购专员","PROC","PURCHASER","Purchase@123"),
                new UserSeed("finance","财务审批人","FIN","FINANCE","Finance@123"), new UserSeed("warehouse","仓库管理员","WH","WAREHOUSE","Warehouse@123"),
                new UserSeed("gm","总经理","HQ","GENERAL_MANAGER","Gm@123"), new UserSeed("auditor","内审专员","HQ","AUDITOR","Audit@123"),
                new UserSeed("quality","质量部门经理","QA","QUALITY_MANAGER","Quality@123"),
                new UserSeed("quality.requester","质量部申请人","QA","APPLICANT","QualityReq@123"),
                new UserSeed("masterdata","主数据管理员","PROC","MASTER_DATA_ADMIN","MasterData@123"),
                new UserSeed("supplieradmin","供应商管理员","PROC","SUPPLIER_ADMIN","Supplier@123"),
                new UserSeed("viewer","采购只读观察员","PROC","PROCUREMENT_VIEWER","Viewer@123"));
        for (UserSeed u:users) {
            if (jdbc.queryForObject("select count(*) from sys_user where username=?", Long.class, u.username) > 0) continue;
            Long departmentId=jdbc.queryForObject("select id from sys_department where code=? and deleted=0",Long.class,u.departmentCode);
            Long roleId=jdbc.queryForObject("select id from sys_role where code=? and deleted=0",Long.class,u.roleCode);
            jdbc.update("insert into sys_user(username,password_hash,display_name,department_id) values(?,?,?,?)",u.username,encoder.encode(u.password),u.displayName,departmentId);
            Long id=jdbc.queryForObject("select id from sys_user where username=?",Long.class,u.username);
            jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)",id,roleId);
        }
    }
    private record UserSeed(String username,String displayName,String departmentCode,String roleCode,String password) {}
}
