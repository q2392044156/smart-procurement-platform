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
        if (jdbc.queryForObject("select count(*) from sys_user", Long.class) > 0) return;
        List<UserSeed> users=List.of(
                new UserSeed("admin","系统管理员",1,1,"Admin@123"), new UserSeed("requester","研发申请人",2,2,"Request@123"),
                new UserSeed("manager","研发部经理",2,3,"Manager@123"), new UserSeed("purchaser","采购专员",4,4,"Purchase@123"),
                new UserSeed("finance","财务审批人",5,5,"Finance@123"), new UserSeed("warehouse","仓库管理员",6,6,"Warehouse@123"),
                new UserSeed("gm","总经理",1,7,"Gm@123"), new UserSeed("auditor","内审专员",1,8,"Audit@123"));
        for (UserSeed u:users) {
            jdbc.update("insert into sys_user(username,password_hash,display_name,department_id) values(?,?,?,?)",u.username,encoder.encode(u.password),u.displayName,u.departmentId);
            Long id=jdbc.queryForObject("select id from sys_user where username=?",Long.class,u.username);
            jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)",id,u.roleId);
        }
    }
    private record UserSeed(String username,String displayName,long departmentId,long roleId,String password) {}
}
