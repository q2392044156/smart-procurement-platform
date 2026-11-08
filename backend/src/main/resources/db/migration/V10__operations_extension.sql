-- P2：出库/领用、预算控制、合同管理、询价/报价
-- 出库事件表（库存余额与流水仍由收货/出库共同维护）
create table inv_issue (
  id bigint primary key auto_increment,
  issue_no varchar(50) not null,
  warehouse_id bigint not null,
  material_id bigint not null,
  quantity decimal(18,4) not null,
  batch_no varchar(100) null,
  serial_numbers_json json null,
  purpose varchar(200) not null,
  department_id bigint not null,
  requester_id bigint not null,
  issued_by bigint not null,
  issued_at timestamp(6) not null,
  remark varchar(500) null,
  created_by bigint not null,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_by bigint not null,
  updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
  version int not null default 0,
  deleted tinyint not null default 0,
  unique key uk_issue_no(issue_no),
  key idx_issue_material(warehouse_id,material_id,created_at)
);

-- 部门年度预算
create table fin_budget (
  id bigint primary key auto_increment,
  department_id bigint not null,
  fiscal_year int not null,
  amount decimal(18,2) not null default 0,
  created_by bigint not null default 0,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_by bigint not null default 0,
  updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
  version int not null default 0,
  unique key uk_budget(department_id,fiscal_year)
);

-- 合同
create table ct_contract (
  id bigint primary key auto_increment,
  contract_no varchar(50) not null,
  name varchar(200) not null,
  supplier_id bigint not null,
  purchase_order_id bigint null,
  type enum('FRAMEWORK','PURCHASE','SERVICE','NDA') not null default 'PURCHASE',
  amount decimal(18,2) not null default 0,
  signed_date date null,
  start_date date null,
  end_date date null,
  status enum('DRAFT','ACTIVE','EXPIRED','TERMINATED') not null default 'DRAFT',
  file_id bigint null,
  remark varchar(500) null,
  created_by bigint not null,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_by bigint not null,
  updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
  version int not null default 0,
  deleted tinyint not null default 0,
  unique key uk_contract_no(contract_no),
  key idx_contract_supplier(supplier_id,status)
);

-- 询价/报价（rfq_*）不在本批实现，待下一批随接口与页面一并建表

-- 权限
insert into sys_permission(code,name,type,parent_code,sort_order) values
('inventory:issue','库存出库','BUTTON','inventory:view',50),
('budget:view','预算查看','MENU',null,51),
('budget:edit','预算维护','BUTTON','budget:view',52),
('contract:view','合同管理','MENU',null,53),
('contract:edit','维护合同','BUTTON','contract:view',54);

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code in ('WAREHOUSE','ADMIN') and p.code='inventory:issue'
on duplicate key update role_id=values(role_id);

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code in ('ADMIN','FINANCE','GENERAL_MANAGER','DEPT_MANAGER') and p.code='budget:view'
on duplicate key update role_id=values(role_id);

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code in ('ADMIN','FINANCE') and p.code='budget:edit'
on duplicate key update role_id=values(role_id);

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code in ('ADMIN','PURCHASER','FINANCE','AUDITOR','GENERAL_MANAGER') and p.code='contract:view'
on duplicate key update role_id=values(role_id);

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code in ('ADMIN','PURCHASER') and p.code='contract:edit'
on duplicate key update role_id=values(role_id);

-- 演示预算：研发/采购/仓储额度充足，质量部刻意设置小额用于验收预算拦截
insert into fin_budget(department_id,fiscal_year,amount)
select d.id,year(curdate()),50000000 from sys_department d where d.code='RD'
and not exists(select 1 from fin_budget b where b.department_id=d.id and b.fiscal_year=year(curdate()));
insert into fin_budget(department_id,fiscal_year,amount)
select d.id,year(curdate()),10000000 from sys_department d where d.code='PROC'
and not exists(select 1 from fin_budget b where b.department_id=d.id and b.fiscal_year=year(curdate()));
insert into fin_budget(department_id,fiscal_year,amount)
select d.id,year(curdate()),500000 from sys_department d where d.code='WH'
and not exists(select 1 from fin_budget b where b.department_id=d.id and b.fiscal_year=year(curdate()));
insert into fin_budget(department_id,fiscal_year,amount)
select d.id,year(curdate()),1000 from sys_department d where d.code='QA'
and not exists(select 1 from fin_budget b where b.department_id=d.id and b.fiscal_year=year(curdate()));

-- 演示合同
insert into ct_contract(contract_no,name,supplier_id,type,amount,signed_date,start_date,end_date,status,remark,created_by,updated_by)
select 'CT-DEMO-001','企业计算设备年度框架协议',s.id,'FRAMEWORK',1200000,date_sub(curdate(),interval 60 day),date_sub(curdate(),interval 60 day),date_add(curdate(),interval 300 day),'ACTIVE','[演示] 年度框架',0,0
from sp_supplier s where s.code='SUP-IT-001'
and not exists(select 1 from ct_contract where contract_no='CT-DEMO-001');
insert into ct_contract(contract_no,name,supplier_id,type,amount,signed_date,start_date,end_date,status,remark,created_by,updated_by)
select 'CT-DEMO-002','MRO 备件采购合同',s.id,'PURCHASE',360000,date_sub(curdate(),interval 200 day),date_sub(curdate(),interval 200 day),date_add(curdate(),interval 20 day),'ACTIVE','[演示] 即将到期',0,0
from sp_supplier s where s.code='SUP-MRO-001'
and not exists(select 1 from ct_contract where contract_no='CT-DEMO-002');
