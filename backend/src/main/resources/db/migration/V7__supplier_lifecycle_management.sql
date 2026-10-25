alter table sp_supplier_qualification
  add column created_by bigint not null default 0,
  add column created_at timestamp(6) not null default current_timestamp(6),
  add column updated_by bigint not null default 0,
  add column updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
  add column version int not null default 0,
  add column deleted tinyint not null default 0;

alter table sp_supplier_evaluation
  add column created_by bigint not null default 0 after comment,
  add column updated_by bigint not null default 0 after created_at,
  add column updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
  add column version int not null default 0,
  add column deleted tinyint not null default 0;

create table sp_supplier_contact (
  id bigint primary key auto_increment,
  supplier_id bigint not null,
  name varchar(100) not null,
  position varchar(100),
  phone varchar(50),
  email varchar(100),
  is_primary tinyint not null default 0,
  created_by bigint not null default 0,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_by bigint not null default 0,
  updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
  version int not null default 0,
  deleted tinyint not null default 0,
  key idx_supplier_contact_supplier(supplier_id,deleted)
);

create index idx_supplier_qualification_supplier on sp_supplier_qualification(supplier_id,deleted);
create index idx_supplier_evaluation_supplier on sp_supplier_evaluation(supplier_id,deleted);

insert into sys_permission(code,name,type,parent_code,sort_order) values
('supplier:profile:edit','维护供应商档案','BUTTON','supplier:view',28),
('supplier:qualification:edit','维护供应商资质','BUTTON','supplier:view',29),
('supplier:evaluation:edit','维护供应商绩效','BUTTON','supplier:view',30);

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code in ('ADMIN','PURCHASER') and p.code like 'supplier:%'
on duplicate key update role_id=values(role_id);

insert into sys_role(code,name,data_scope,enabled,built_in)
select 'SUPPLIER_ADMIN','供应商管理员','ALL',1,0
where not exists(select 1 from sys_role where code='SUPPLIER_ADMIN');

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code='SUPPLIER_ADMIN' and p.code in
('dashboard:view','supplier:view','supplier:profile:edit','supplier:qualification:edit','supplier:evaluation:edit')
on duplicate key update role_id=values(role_id);

insert into sp_supplier_contact(supplier_id,name,position,phone,email,is_primary)
select s.id,s.contact_name,'客户经理',s.contact_phone,s.email,1
from sp_supplier s
where s.deleted=0 and s.contact_name is not null
  and not exists(select 1 from sp_supplier_contact c where c.supplier_id=s.id and c.deleted=0);

insert into sp_supplier(code,name,unified_credit_code,level,status,contact_name,contact_phone,email,address,score)
select 'SUP-AUTO-001','华北自动化控制技术有限公司','91110000DEMOAUTO01X','A','QUALIFIED','孙工','13800001004','automation@example.com','北京市经济技术开发区',91.67
where not exists(select 1 from sp_supplier where code='SUP-AUTO-001');

insert into sp_supplier_contact(supplier_id,name,position,phone,email,is_primary)
select s.id,'孙工','技术销售经理','13800001004','automation@example.com',1
from sp_supplier s where s.code='SUP-AUTO-001'
and not exists(select 1 from sp_supplier_contact c where c.supplier_id=s.id and c.name='孙工' and c.deleted=0);

insert into sp_supplier_contact(supplier_id,name,position,phone,email,is_primary)
select s.id,'吴工','售后服务工程师','13800001005','service.automation@example.com',0
from sp_supplier s where s.code='SUP-AUTO-001'
and not exists(select 1 from sp_supplier_contact c where c.supplier_id=s.id and c.name='吴工' and c.deleted=0);

insert into sp_supplier_qualification(supplier_id,name,file_id,valid_from,valid_until,status)
select s.id,'ISO 14001环境管理体系认证',null,'2026-01-01','2029-12-31','VALID'
from sp_supplier s where s.code='SUP-AUTO-001'
and not exists(select 1 from sp_supplier_qualification q where q.supplier_id=s.id and q.name='ISO 14001环境管理体系认证' and q.deleted=0);

insert into sp_supplier_qualification(supplier_id,name,file_id,valid_from,valid_until,status)
select s.id,'工业自动化系统集成服务资质',null,'2025-06-01','2028-05-31','VALID'
from sp_supplier s where s.code='SUP-AUTO-001'
and not exists(select 1 from sp_supplier_qualification q where q.supplier_id=s.id and q.name='工业自动化系统集成服务资质' and q.deleted=0);

insert into sp_supplier_evaluation(supplier_id,period,quality_score,delivery_score,service_score,total_score,comment)
select s.id,'2026-Q2',93,89,94,92.00,'控制产品质量稳定，技术支持响应及时'
from sp_supplier s where s.code='SUP-AUTO-001'
and not exists(select 1 from sp_supplier_evaluation e where e.supplier_id=s.id and e.period='2026-Q2' and e.deleted=0);

insert into sp_supplier_evaluation(supplier_id,period,quality_score,delivery_score,service_score,total_score,comment)
select s.id,'2026-Q3',95,91,88,91.33,'交付表现改善，现场服务需要继续加强'
from sp_supplier s where s.code='SUP-AUTO-001'
and not exists(select 1 from sp_supplier_evaluation e where e.supplier_id=s.id and e.period='2026-Q3' and e.deleted=0);
