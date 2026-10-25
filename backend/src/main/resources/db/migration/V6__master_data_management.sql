alter table md_unit
  add column enabled tinyint not null default 1 after precision_scale,
  add column created_by bigint not null default 0,
  add column created_at timestamp(6) not null default current_timestamp(6),
  add column updated_by bigint not null default 0,
  add column updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
  add column version int not null default 0,
  add column deleted tinyint not null default 0;

alter table md_brand
  add column enabled tinyint not null default 1 after name,
  add column created_by bigint not null default 0,
  add column created_at timestamp(6) not null default current_timestamp(6),
  add column updated_by bigint not null default 0,
  add column updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
  add column version int not null default 0,
  add column deleted tinyint not null default 0;

insert into sys_permission(code,name,type,parent_code,sort_order) values
('masterdata:category:edit','维护物资分类','BUTTON','masterdata:view',23),
('masterdata:material:edit','维护物资档案','BUTTON','masterdata:view',24),
('masterdata:warehouse:edit','维护仓库','BUTTON','masterdata:view',25),
('masterdata:unit:edit','维护计量单位','BUTTON','masterdata:view',26),
('masterdata:brand:edit','维护品牌','BUTTON','masterdata:view',27);

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code='ADMIN' and p.code like 'masterdata:%'
on duplicate key update role_id=values(role_id);

insert into sys_role(code,name,data_scope,enabled,built_in)
select 'MASTER_DATA_ADMIN','主数据管理员','ALL',1,0
where not exists(select 1 from sys_role where code='MASTER_DATA_ADMIN');

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code='MASTER_DATA_ADMIN' and p.code in
('dashboard:view','masterdata:view','masterdata:category:edit','masterdata:material:edit','masterdata:warehouse:edit','masterdata:unit:edit','masterdata:brand:edit')
on duplicate key update role_id=values(role_id);

insert into md_unit(code,name,precision_scale)
select 'KG','千克',3 where not exists(select 1 from md_unit where code='KG');

insert into md_brand(code,name)
select 'OMRON','欧姆龙' where not exists(select 1 from md_brand where code='OMRON');

insert into md_warehouse(code,name,address,manager)
select 'QA_WH','质量检验仓','质量中心一层','赵质检'
where not exists(select 1 from md_warehouse where code='QA_WH');

insert into md_material_category(parent_id,code,name,description,sort_order)
select null,'TOOLING','工装量具','生产工装、量具与检验工具',40
where not exists(select 1 from md_material_category where code='TOOLING');

insert into md_category_attribute(category_id,code,name,value_type,required,options_json,sort_order)
select c.id,'accuracyGrade','精度等级','ENUM',1,json_array('一级','二级','三级'),1
from md_material_category c where c.code='TOOLING'
and not exists(select 1 from md_category_attribute a where a.category_id=c.id and a.code='accuracyGrade');

insert into md_material(code,name,category_id,unit_id,brand_id,specification,tracking_mode,min_stock,attributes_json)
select 'TOOL-TORQUE-001','数显扭矩扳手',c.id,u.id,null,'20-200N·m，带校准证书','SERIAL',2,json_object('accuracyGrade','一级')
from md_material_category c join md_unit u on u.code='PCS'
where c.code='TOOLING' and not exists(select 1 from md_material where code='TOOL-TORQUE-001');
