alter table sys_department add column enabled tinyint not null default 1 after sort_order;
alter table sys_role add column enabled tinyint not null default 1 after data_scope;
alter table sys_role add column built_in tinyint not null default 0 after enabled;

update sys_role set built_in=1 where id between 1 and 8;

insert into sys_permission(code,name,type,parent_code,sort_order) values
('system:user:create','新增用户','BUTTON','system:user:view',17),
('system:user:edit','编辑用户','BUTTON','system:user:view',18),
('system:user:disable','启停用户','BUTTON','system:user:view',19),
('system:user:reset-password','重置用户密码','BUTTON','system:user:view',20),
('system:department:edit','维护组织架构','BUTTON','system:user:view',21),
('system:role:edit','维护角色权限','BUTTON','system:user:view',22);

insert into sys_role_permission(role_id,permission_id)
select 1,id from sys_permission where code like 'system:%'
on duplicate key update role_id=values(role_id);

insert into sys_department(id,parent_id,code,name,sort_order,enabled)
values (7,1,'QA','质量管理部',25,1);

insert into sys_role(id,code,name,data_scope,enabled,built_in) values
(9,'PROCUREMENT_VIEWER','采购只读','ALL',1,0),
(10,'QUALITY_MANAGER','质量部门经理','DEPARTMENT',1,0);

insert into sys_role_permission(role_id,permission_id)
select 9,id from sys_permission where code in
('dashboard:view','masterdata:view','supplier:view','requisition:view','purchaseorder:view','inventory:view');

insert into sys_role_permission(role_id,permission_id)
select 10,id from sys_permission where code in
('dashboard:view','masterdata:view','supplier:view','requisition:view','workflow:approve');
