-- 审批人需要只读主数据：财务与总经理在审批申请、核对订单与合同时要看到物资与供应商名称
insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where r.code in ('FINANCE','GENERAL_MANAGER') and p.code in ('masterdata:view','supplier:view')
on duplicate key update role_id=values(role_id);
