insert into sp_supplier_qualification(supplier_id,name,file_id,valid_from,valid_until,status)
select s.id,'ISO 9001质量管理体系认证',null,'2025-01-01','2028-12-31','VALID'
from sp_supplier s
where s.code='SUP-IT-001'
  and not exists (select 1 from sp_supplier_qualification q where q.supplier_id=s.id and q.name='ISO 9001质量管理体系认证');

insert into sp_supplier_qualification(supplier_id,name,file_id,valid_from,valid_until,status)
select s.id,'信息安全管理体系认证',null,'2024-12-01','2026-12-01','EXPIRING'
from sp_supplier s
where s.code='SUP-IT-001'
  and not exists (select 1 from sp_supplier_qualification q where q.supplier_id=s.id and q.name='信息安全管理体系认证');

insert into sp_supplier_qualification(supplier_id,name,file_id,valid_from,valid_until,status)
select s.id,'工业产品质量认证',null,'2025-05-01','2029-05-01','VALID'
from sp_supplier s
where s.code='SUP-MRO-001'
  and not exists (select 1 from sp_supplier_qualification q where q.supplier_id=s.id and q.name='工业产品质量认证');

insert into sp_supplier_qualification(supplier_id,name,file_id,valid_from,valid_until,status)
select s.id,'特种设备安装许可（历史）',null,'2022-01-01','2025-12-31','EXPIRED'
from sp_supplier s
where s.code='SUP-EQP-001'
  and not exists (select 1 from sp_supplier_qualification q where q.supplier_id=s.id and q.name='特种设备安装许可（历史）');

insert into sp_supplier_evaluation(supplier_id,period,quality_score,delivery_score,service_score,total_score,comment)
select s.id,'2026-Q1',94,91,90,92.00,'产品质量稳定，交付准时'
from sp_supplier s where s.code='SUP-IT-001'
and not exists (select 1 from sp_supplier_evaluation e where e.supplier_id=s.id and e.period='2026-Q1');

insert into sp_supplier_evaluation(supplier_id,period,quality_score,delivery_score,service_score,total_score,comment)
select s.id,'2026-Q2',96,88,93,92.33,'质量表现优秀，交付周期仍有优化空间'
from sp_supplier s where s.code='SUP-IT-001'
and not exists (select 1 from sp_supplier_evaluation e where e.supplier_id=s.id and e.period='2026-Q2');

insert into sp_supplier_evaluation(supplier_id,period,quality_score,delivery_score,service_score,total_score,comment)
select s.id,'2026-Q3',95,94,92,93.67,'综合表现优秀，建议保持A级合作'
from sp_supplier s where s.code='SUP-IT-001'
and not exists (select 1 from sp_supplier_evaluation e where e.supplier_id=s.id and e.period='2026-Q3');

insert into sp_supplier_evaluation(supplier_id,period,quality_score,delivery_score,service_score,total_score,comment)
select s.id,'2026-Q3',97,96,92,95.00,'备件质量与交付表现突出'
from sp_supplier s where s.code='SUP-MRO-001'
and not exists (select 1 from sp_supplier_evaluation e where e.supplier_id=s.id and e.period='2026-Q3');

insert into sp_supplier_evaluation(supplier_id,period,quality_score,delivery_score,service_score,total_score,comment)
select s.id,'2026-Q3',90,82,91,87.67,'技术服务良好，设备交付存在延期'
from sp_supplier s where s.code='SUP-EQP-001'
and not exists (select 1 from sp_supplier_evaluation e where e.supplier_id=s.id and e.period='2026-Q3');
