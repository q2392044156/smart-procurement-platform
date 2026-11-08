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
        seedShowcaseDocuments();
    }

    /**
     * V8 迁移里的演示单据依赖 sys_user，而演示账号由本初始化器在 Flyway 之后创建，
     * 因此在全新数据库上需要在账号就绪后补种一次（全部按单号幂等）。
     */
    private void seedShowcaseDocuments(){
        jdbc.update("""
                insert into pr_requisition(requisition_no,department_id,applicant_id,required_date,purpose,budget_amount,status,created_by,updated_by)
                select 'PR-V8-DRAFT',u.department_id,u.id,date_add(current_date,interval 30 day),
                       '[第四批演示] 可编辑、取消和删除的采购申请',3600,'DRAFT',u.id,u.id
                from sys_user u where u.username='requester'
                and not exists(select 1 from pr_requisition where requisition_no='PR-V8-DRAFT')
                """);
        jdbc.update("""
                insert into pr_requisition_item(requisition_id,material_id,quantity,estimated_unit_price,remark,created_by,updated_by)
                select r.id,m.id,3,1200,'第四批采购申请明细',r.applicant_id,r.applicant_id
                from pr_requisition r join md_material m on m.code='RAM-DDR5-032'
                where r.requisition_no='PR-V8-DRAFT'
                and not exists(select 1 from pr_requisition_item i where i.requisition_id=r.id and i.deleted=0)
                """);
        jdbc.update("""
                insert into pr_requisition(requisition_no,department_id,applicant_id,required_date,purpose,budget_amount,status,cancellation_reason,created_by,updated_by)
                select 'PR-V8-CANCELLED',u.department_id,u.id,date_add(current_date,interval 20 day),
                       '[第四批演示] 已取消采购申请',1200,'CANCELLED','业务需求调整',u.id,u.id
                from sys_user u where u.username='requester'
                and not exists(select 1 from pr_requisition where requisition_no='PR-V8-CANCELLED')
                """);
        jdbc.update("""
                insert into pr_requisition_item(requisition_id,material_id,quantity,estimated_unit_price,remark,created_by,updated_by)
                select r.id,m.id,1,1200,'取消前采购明细',r.applicant_id,r.applicant_id
                from pr_requisition r join md_material m on m.code='RAM-DDR5-032'
                where r.requisition_no='PR-V8-CANCELLED'
                and not exists(select 1 from pr_requisition_item i where i.requisition_id=r.id and i.deleted=0)
                """);
        jdbc.update("""
                insert into po_purchase_order(order_no,requisition_id,supplier_id,purchaser_id,delivery_date,tax_rate,total_amount,status,terms,created_by,updated_by)
                select 'PO-V8-DRAFT',null,s.id,u.id,date_add(current_date,interval 25 day),0.13,7200,'DRAFT',
                       '[第四批演示] 可编辑、取消和删除的采购订单',u.id,u.id
                from sp_supplier s join sys_user u on u.username='purchaser'
                where s.code='SUP-AUTO-001'
                and not exists(select 1 from po_purchase_order where order_no='PO-V8-DRAFT')
                """);
        jdbc.update("""
                insert into po_purchase_order_item(purchase_order_id,material_id,quantity,received_quantity,unit_price,tax_rate,remark,created_by,updated_by)
                select o.id,m.id,2,0,3600,0.13,'第四批订单明细',o.purchaser_id,o.purchaser_id
                from po_purchase_order o join md_material m on m.code='MOTOR-7K5'
                where o.order_no='PO-V8-DRAFT'
                and not exists(select 1 from po_purchase_order_item i where i.purchase_order_id=o.id and i.deleted=0)
                """);
    }
    private record UserSeed(String username,String displayName,String departmentCode,String roleCode,String password) {}
}
