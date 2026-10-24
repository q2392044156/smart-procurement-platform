insert into md_material(code,name,category_id,unit_id,brand_id,specification,tracking_mode,min_stock,attributes_json)
select 'MOUSE-USB-001','工业有线鼠标',3,1,null,'USB接口，适用于车间工位','QUANTITY',20,json_object('interface','USB')
where not exists (select 1 from md_material where code='MOUSE-USB-001');
