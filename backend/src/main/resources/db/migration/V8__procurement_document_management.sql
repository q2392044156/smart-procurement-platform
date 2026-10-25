alter table pr_requisition add column cancellation_reason varchar(500) null after rejection_reason;
alter table po_purchase_order
  add column cancellation_reason varchar(500) null after terms,
  add column closed_at timestamp(6) null after cancellation_reason;

create table fs_business_attachment (
  id bigint primary key auto_increment,
  business_type enum('REQUISITION','PURCHASE_ORDER') not null,
  business_id bigint not null,
  file_id bigint not null,
  category varchar(50) not null default 'OTHER',
  note varchar(300),
  created_by bigint not null,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_by bigint not null,
  updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
  version int not null default 0,
  deleted tinyint not null default 0,
  key idx_attachment_business(business_type,business_id,deleted),
  key idx_attachment_file(file_id,deleted)
);

insert into sys_permission(code,name,type,parent_code,sort_order) values
('requisition:cancel','取消采购申请','BUTTON','requisition:view',31),
('requisition:delete','删除采购申请','BUTTON','requisition:view',32),
('purchaseorder:update','编辑采购订单','BUTTON','purchaseorder:view',33),
('purchaseorder:cancel','取消采购订单','BUTTON','purchaseorder:view',34),
('purchaseorder:close','关闭采购订单','BUTTON','purchaseorder:view',35),
('purchaseorder:delete','删除采购订单','BUTTON','purchaseorder:view',36);

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code='ADMIN' and p.code in ('requisition:cancel','requisition:delete','purchaseorder:update','purchaseorder:cancel','purchaseorder:close','purchaseorder:delete')
on duplicate key update role_id=values(role_id);

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code='APPLICANT' and p.code in ('requisition:cancel','requisition:delete')
on duplicate key update role_id=values(role_id);

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code='PURCHASER' and p.code in ('purchaseorder:update','purchaseorder:cancel','purchaseorder:close','purchaseorder:delete')
on duplicate key update role_id=values(role_id);

insert into pr_requisition(requisition_no,department_id,applicant_id,required_date,purpose,budget_amount,status,created_by,updated_by)
select 'PR-V8-DRAFT',u.department_id,u.id,date_add(current_date,interval 30 day),'[第四批演示] 可编辑、取消和删除的采购申请',3600,'DRAFT',u.id,u.id
from sys_user u where u.username='requester'
and not exists(select 1 from pr_requisition where requisition_no='PR-V8-DRAFT');

insert into pr_requisition_item(requisition_id,material_id,quantity,estimated_unit_price,remark,created_by,updated_by)
select r.id,m.id,3,1200,'第四批采购申请明细',r.applicant_id,r.applicant_id
from pr_requisition r join md_material m on m.code='RAM-DDR5-032'
where r.requisition_no='PR-V8-DRAFT'
and not exists(select 1 from pr_requisition_item i where i.requisition_id=r.id and i.deleted=0);

insert into pr_requisition(requisition_no,department_id,applicant_id,required_date,purpose,budget_amount,status,cancellation_reason,created_by,updated_by)
select 'PR-V8-CANCELLED',u.department_id,u.id,date_add(current_date,interval 20 day),'[第四批演示] 已取消采购申请',1200,'CANCELLED','业务需求调整',u.id,u.id
from sys_user u where u.username='requester'
and not exists(select 1 from pr_requisition where requisition_no='PR-V8-CANCELLED');

insert into pr_requisition_item(requisition_id,material_id,quantity,estimated_unit_price,remark,created_by,updated_by)
select r.id,m.id,1,1200,'取消前采购明细',r.applicant_id,r.applicant_id
from pr_requisition r join md_material m on m.code='RAM-DDR5-032'
where r.requisition_no='PR-V8-CANCELLED'
and not exists(select 1 from pr_requisition_item i where i.requisition_id=r.id and i.deleted=0);

insert into po_purchase_order(order_no,requisition_id,supplier_id,purchaser_id,delivery_date,tax_rate,total_amount,status,terms,created_by,updated_by)
select 'PO-V8-DRAFT',null,s.id,u.id,date_add(current_date,interval 25 day),0.13,7200,'DRAFT','[第四批演示] 可编辑、取消和删除的采购订单',u.id,u.id
from sp_supplier s join sys_user u on u.username='purchaser'
where s.code='SUP-AUTO-001'
and not exists(select 1 from po_purchase_order where order_no='PO-V8-DRAFT');

insert into po_purchase_order_item(purchase_order_id,material_id,quantity,received_quantity,unit_price,tax_rate,remark,created_by,updated_by)
select o.id,m.id,2,0,3600,0.13,'第四批订单明细',o.purchaser_id,o.purchaser_id
from po_purchase_order o join md_material m on m.code='MOTOR-7K5'
where o.order_no='PO-V8-DRAFT'
and not exists(select 1 from po_purchase_order_item i where i.purchase_order_id=o.id and i.deleted=0);
