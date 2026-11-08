-- 期初库存：给数量型演示物资建立初始余额与期初流水，便于出库/领用演示与验收
insert into inv_balance(warehouse_id,material_id,quantity,created_by,updated_by)
select w.id,m.id,100,0,0
from md_warehouse w join md_material m on m.code='MOUSE-USB-001'
where w.code='MAIN'
and not exists(select 1 from inv_balance b where b.warehouse_id=w.id and b.material_id=m.id);

insert into inv_ledger(warehouse_id,material_id,business_type,business_id,direction,quantity,balance_after,created_by,created_at)
select b.warehouse_id,b.material_id,'OPENING',0,'IN',100,100,0,utc_timestamp(6)
from inv_balance b join md_material m on m.id=b.material_id
where m.code='MOUSE-USB-001'
and not exists(select 1 from inv_ledger l where l.business_type='OPENING' and l.material_id=b.material_id and l.warehouse_id=b.warehouse_id);
