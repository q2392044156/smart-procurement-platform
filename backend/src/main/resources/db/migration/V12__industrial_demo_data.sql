-- 工业物资演示数据：在原有 IT/MRO/设备/工装基础上补齐网络、传动、过滤、
-- 润滑、紧固、物流、动力、劳保品类，形成接近真实制造企业的采购目录。
-- 全部使用 insert ... select ... where not exists，支持重复执行。

-- ===== 分类 =====
insert into md_material_category(parent_id, code, name, description, sort_order)
select null, 'SAFETY', '安全劳保', '个体防护与作业安全用品', 50
where not exists (select 1 from md_material_category where code = 'SAFETY');

insert into md_material_category(parent_id, code, name, description, sort_order)
select p.id, 'PPE', '个体防护', '安全帽、防护手套、护目镜', 51
from md_material_category p
where p.code = 'SAFETY'
  and not exists (select 1 from md_material_category where code = 'PPE');

insert into md_material_category(parent_id, code, name, description, sort_order)
select p.id, 'NETWORK', '网络与机房', '交换机、UPS、机房配套', 13
from md_material_category p
where p.code = 'IT'
  and not exists (select 1 from md_material_category where code = 'NETWORK');

insert into md_material_category(parent_id, code, name, description, sort_order)
select p.id, 'TRANSMISSION', '传动密封', '皮带、油封、联轴器', 23
from md_material_category p
where p.code = 'MRO'
  and not exists (select 1 from md_material_category where code = 'TRANSMISSION');

insert into md_material_category(parent_id, code, name, description, sort_order)
select p.id, 'FILTER', '过滤耗材', '液压油滤芯、空气滤芯', 24
from md_material_category p
where p.code = 'MRO'
  and not exists (select 1 from md_material_category where code = 'FILTER');

insert into md_material_category(parent_id, code, name, description, sort_order)
select p.id, 'LUBRICANT', '润滑材料', '液压油、润滑脂', 25
from md_material_category p
where p.code = 'MRO'
  and not exists (select 1 from md_material_category where code = 'LUBRICANT');

insert into md_material_category(parent_id, code, name, description, sort_order)
select p.id, 'FASTENER', '紧固件', '螺栓、螺母、垫圈', 26
from md_material_category p
where p.code = 'MRO'
  and not exists (select 1 from md_material_category where code = 'FASTENER');

insert into md_material_category(parent_id, code, name, description, sort_order)
select p.id, 'LOGISTICS', '物流设备', 'AGV、输送线、仓储设备', 33
from md_material_category p
where p.code = 'EQUIPMENT'
  and not exists (select 1 from md_material_category where code = 'LOGISTICS');

insert into md_material_category(parent_id, code, name, description, sort_order)
select p.id, 'UTILITY', '动力设备', '空压机、泵、冷却设备', 34
from md_material_category p
where p.code = 'EQUIPMENT'
  and not exists (select 1 from md_material_category where code = 'UTILITY');

-- ===== 分类属性 =====
insert into md_category_attribute(category_id, code, name, value_type, required, options_json, sort_order)
select c.id, 'ports', '端口数量', 'NUMBER', 1, null, 1
from md_material_category c
where c.code = 'NETWORK'
  and not exists (select 1 from md_category_attribute a where a.category_id = c.id and a.code = 'ports');

insert into md_category_attribute(category_id, code, name, value_type, required, options_json, sort_order)
select c.id, 'protectionLevel', '防护等级', 'ENUM', 1, json_array('EN388', 'EN166', 'EN397'), 1
from md_material_category c
where c.code = 'PPE'
  and not exists (select 1 from md_category_attribute a where a.category_id = c.id and a.code = 'protectionLevel');

insert into md_category_attribute(category_id, code, name, value_type, required, options_json, sort_order)
select c.id, 'viscosityGrade', '粘度等级', 'TEXT', 1, null, 1
from md_material_category c
where c.code = 'LUBRICANT'
  and not exists (select 1 from md_category_attribute a where a.category_id = c.id and a.code = 'viscosityGrade');

insert into md_category_attribute(category_id, code, name, value_type, required, options_json, sort_order)
select c.id, 'propertyClass', '性能等级', 'ENUM', 1, json_array('8.8', '10.9', '12.9'), 1
from md_material_category c
where c.code = 'FASTENER'
  and not exists (select 1 from md_category_attribute a where a.category_id = c.id and a.code = 'propertyClass');

insert into md_category_attribute(category_id, code, name, value_type, required, options_json, sort_order)
select c.id, 'loadCapacity', '额定载荷(kg)', 'NUMBER', 1, null, 1
from md_material_category c
where c.code = 'LOGISTICS'
  and not exists (select 1 from md_category_attribute a where a.category_id = c.id and a.code = 'loadCapacity');

insert into md_category_attribute(category_id, code, name, value_type, required, options_json, sort_order)
select c.id, 'powerKw', '功率(kW)', 'NUMBER', 1, null, 1
from md_material_category c
where c.code = 'UTILITY'
  and not exists (select 1 from md_category_attribute a where a.category_id = c.id and a.code = 'powerKw');

-- ===== 品牌 =====
insert into md_brand(code, name)
select 'H3C', '新华三' where not exists (select 1 from md_brand where code = 'H3C');
insert into md_brand(code, name)
select 'APC', '施耐德 APC' where not exists (select 1 from md_brand where code = 'APC');
insert into md_brand(code, name)
select 'NSK', 'NSK' where not exists (select 1 from md_brand where code = 'NSK');
insert into md_brand(code, name)
select 'FLUKE', '福禄克' where not exists (select 1 from md_brand where code = 'FLUKE');
insert into md_brand(code, name)
select '3M', '3M' where not exists (select 1 from md_brand where code = '3M');
insert into md_brand(code, name)
select 'PARKER', '派克' where not exists (select 1 from md_brand where code = 'PARKER');
insert into md_brand(code, name)
select 'MITUTOYO', '三丰' where not exists (select 1 from md_brand where code = 'MITUTOYO');

-- ===== 物料 =====
insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'MON-27-4K', '27英寸4K显示器', c.id, u.id, b.id,
       '3840x2160 / USB-C 90W / 升降旋转', 'QUANTITY', 10,
       json_object('resolution', '3840x2160', 'interface', 'USB-C')
from md_material_category c
join md_unit u on u.code = 'PCS'
left join md_brand b on b.code = 'DELL'
where c.code = 'COMP'
  and not exists (select 1 from md_material where code = 'MON-27-4K');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'SW-24P', '24口千兆接入交换机', c.id, u.id, b.id,
       '24×GE + 4×10G SFP+ / 双电源', 'SERIAL', 5,
       json_object('ports', 24, 'uplink', '10G SFP+')
from md_material_category c
join md_unit u on u.code = 'PCS'
left join md_brand b on b.code = 'H3C'
where c.code = 'NETWORK'
  and not exists (select 1 from md_material where code = 'SW-24P');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'UPS-3K', '3kVA在线式UPS', c.id, u.id, b.id,
       '3kVA/2.7kW / 在线双变换 / 2U机架', 'SERIAL', 3,
       json_object('capacityKva', 3, 'formFactor', '2U')
from md_material_category c
join md_unit u on u.code = 'PCS'
left join md_brand b on b.code = 'APC'
where c.code = 'NETWORK'
  and not exists (select 1 from md_material where code = 'UPS-3K');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'KB-USB', '工业防水键盘', c.id, u.id, b.id,
       'USB / IP65 / 104键', 'QUANTITY', 30,
       json_object('interface', 'USB', 'protection', 'IP65')
from md_material_category c
join md_unit u on u.code = 'PCS'
left join md_brand b on b.code = '3M'
where c.code = 'COMP'
  and not exists (select 1 from md_material where code = 'KB-USB');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'DOCK-USB4', 'USB4扩展坞', c.id, u.id, b.id,
       'USB4 / 双4K / 100W PD', 'QUANTITY', 20,
       json_object('interface', 'USB4', 'powerDelivery', '100W')
from md_material_category c
join md_unit u on u.code = 'PCS'
left join md_brand b on b.code = 'DELL'
where c.code = 'COMP'
  and not exists (select 1 from md_material where code = 'DOCK-USB4');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'BRG-6308', '深沟球轴承6308', c.id, u.id, b.id,
       '6308-2Z/C3 / 40×90×23', 'BATCH', 60,
       json_object('innerDiameter', 40, 'outerDiameter', 90, 'width', 23)
from md_material_category c
join md_unit u on u.code = 'PCS'
left join md_brand b on b.code = 'NSK'
where c.code = 'BEARING'
  and not exists (select 1 from md_material where code = 'BRG-6308');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'BELT-A45', 'A型三角带A45', c.id, u.id, null,
       'A型 / 内周长1143mm', 'BATCH', 50,
       json_object('profile', 'A', 'lengthMm', 1143)
from md_material_category c
join md_unit u on u.code = 'PCS'
where c.code = 'TRANSMISSION'
  and not exists (select 1 from md_material where code = 'BELT-A45');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'FILTER-HYD', '液压油滤芯', c.id, u.id, b.id,
       '10μm / 与 Parker 系列兼容', 'BATCH', 40,
       json_object('micron', 10, 'compatibility', 'Parker')
from md_material_category c
join md_unit u on u.code = 'PCS'
left join md_brand b on b.code = 'PARKER'
where c.code = 'FILTER'
  and not exists (select 1 from md_material where code = 'FILTER-HYD');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'LUBE-46', '46号抗磨液压油', c.id, u.id, null,
       'ISO VG 46 / 200L桶装', 'BATCH', 200,
       json_object('viscosityGrade', 'ISO VG 46', 'packing', '200L')
from md_material_category c
join md_unit u on u.code = 'KG'
where c.code = 'LUBRICANT'
  and not exists (select 1 from md_material where code = 'LUBE-46');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'SEAL-NBR', 'NBR骨架油封', c.id, u.id, null,
       '35×52×7 / NBR / 耐矿物油', 'BATCH', 100,
       json_object('material', 'NBR', 'size', '35x52x7')
from md_material_category c
join md_unit u on u.code = 'PCS'
where c.code = 'TRANSMISSION'
  and not exists (select 1 from md_material where code = 'SEAL-NBR');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'BOLT-M8', 'M8×30高强度螺栓', c.id, u.id, null,
       '8.8级 / 发黑 / 全牙', 'BATCH', 80,
       json_object('propertyClass', '8.8', 'size', 'M8x30')
from md_material_category c
join md_unit u on u.code = 'BOX'
where c.code = 'FASTENER'
  and not exists (select 1 from md_material where code = 'BOLT-M8');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'AGV-500', '潜伏式AGV 500kg', c.id, u.id, null,
       '500kg / 激光SLAM / 自动充电', 'SERIAL', 0,
       json_object('loadCapacity', 500, 'navigation', 'SLAM')
from md_material_category c
join md_unit u on u.code = 'PCS'
where c.code = 'LOGISTICS'
  and not exists (select 1 from md_material where code = 'AGV-500');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'COMP-AIR', '螺杆空压机22kW', c.id, u.id, null,
       '22kW / 8bar / 永磁变频', 'SERIAL', 0,
       json_object('powerKw', 22, 'pressureBar', 8)
from md_material_category c
join md_unit u on u.code = 'PCS'
where c.code = 'UTILITY'
  and not exists (select 1 from md_material where code = 'COMP-AIR');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'CALIPER-150', '数显游标卡尺0-150mm', c.id, u.id, b.id,
       '0-150mm / 0.01mm / 不锈钢', 'SERIAL', 5,
       json_object('rangeMm', 150, 'resolutionMm', 0.01)
from md_material_category c
join md_unit u on u.code = 'PCS'
left join md_brand b on b.code = 'MITUTOYO'
where c.code = 'TOOLING'
  and not exists (select 1 from md_material where code = 'CALIPER-150');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'MULTI-DMM', '数字万用表', c.id, u.id, b.id,
       '真有效值 / 1000V / 防烧', 'SERIAL', 5,
       json_object('maxVoltage', 1000, 'trueRms', true)
from md_material_category c
join md_unit u on u.code = 'PCS'
left join md_brand b on b.code = 'FLUKE'
where c.code = 'TOOLING'
  and not exists (select 1 from md_material where code = 'MULTI-DMM');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'GLOVE-CUT', '防切割防护手套', c.id, u.id, b.id,
       'EN388 4X43C / 13针 / 丁腈涂层', 'BATCH', 100,
       json_object('protectionLevel', 'EN388', 'coating', 'NBR')
from md_material_category c
join md_unit u on u.code = 'BOX'
left join md_brand b on b.code = '3M'
where c.code = 'PPE'
  and not exists (select 1 from md_material where code = 'GLOVE-CUT');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'HELMET-ABS', 'ABS安全帽', c.id, u.id, b.id,
       'EN397 / 可调 / 带下颏带', 'BATCH', 50,
       json_object('protectionLevel', 'EN397', 'material', 'ABS')
from md_material_category c
join md_unit u on u.code = 'BOX'
left join md_brand b on b.code = '3M'
where c.code = 'PPE'
  and not exists (select 1 from md_material where code = 'HELMET-ABS');

insert into md_material(code, name, category_id, unit_id, brand_id, specification, tracking_mode, min_stock, attributes_json)
select 'GOGGLE-ANTI', '防冲击护目镜', c.id, u.id, b.id,
       'EN166 / 防雾 / 可戴近视镜', 'BATCH', 40,
       json_object('protectionLevel', 'EN166', 'antiFog', true)
from md_material_category c
join md_unit u on u.code = 'BOX'
left join md_brand b on b.code = '3M'
where c.code = 'PPE'
  and not exists (select 1 from md_material where code = 'GOGGLE-ANTI');

-- ===== 供应商 =====
insert into sp_supplier(code, name, unified_credit_code, level, status, contact_name, contact_phone, email, address, score)
select 'SUP-ELEC-001', '江苏恒力电气有限公司', '91320000DEMO00005E', 'A', 'QUALIFIED',
       '周经理', '13800001005', 'elec@example.com', '江苏省无锡市', 93.00
where not exists (select 1 from sp_supplier where code = 'SUP-ELEC-001');

insert into sp_supplier(code, name, unified_credit_code, level, status, contact_name, contact_phone, email, address, score)
select 'SUP-TOOL-001', '上海精工工具有限公司', '91310000DEMO00006F', 'A', 'QUALIFIED',
       '孙经理', '13800001006', 'tool@example.com', '上海市闵行区', 90.00
where not exists (select 1 from sp_supplier where code = 'SUP-TOOL-001');

insert into sp_supplier(code, name, unified_credit_code, level, status, contact_name, contact_phone, email, address, score)
select 'SUP-SAFE-001', '浙江安盾劳保用品有限公司', '91330000DEMO00007G', 'B', 'QUALIFIED',
       '李经理', '13800001007', 'safety@example.com', '浙江省杭州市', 86.00
where not exists (select 1 from sp_supplier where code = 'SUP-SAFE-001');

insert into sp_supplier(code, name, unified_credit_code, level, status, contact_name, contact_phone, email, address, score)
select 'SUP-LOG-001', '苏州智物流装备有限公司', '91320000DEMO00008H', 'B', 'QUALIFIED',
       '吴经理', '13800001008', 'logistics@example.com', '江苏省苏州市', 87.00
where not exists (select 1 from sp_supplier where code = 'SUP-LOG-001');

insert into sp_supplier(code, name, unified_credit_code, level, status, contact_name, contact_phone, email, address, score)
select 'SUP-LUBE-001', '杭州润达工业油品有限公司', '91330000DEMO00009J', 'A', 'QUALIFIED',
       '郑经理', '13800001009', 'lube@example.com', '浙江省杭州市', 91.00
where not exists (select 1 from sp_supplier where code = 'SUP-LUBE-001');

-- ===== 仓库 =====
insert into md_warehouse(code, name, address, manager)
select 'ELEC', '电气备件仓', '维修中心二层', '钱库管'
where not exists (select 1 from md_warehouse where code = 'ELEC');
insert into md_warehouse(code, name, address, manager)
select 'TOOL', '工装工具仓', '一号厂房工具间', '孙库管'
where not exists (select 1 from md_warehouse where code = 'TOOL');
insert into md_warehouse(code, name, address, manager)
select 'SAFE', '劳保用品仓', '行政楼地下一层', '李库管'
where not exists (select 1 from md_warehouse where code = 'SAFE');
insert into md_warehouse(code, name, address, manager)
select 'AUTO', '自动化设备仓', '二号厂房东侧', '吴库管'
where not exists (select 1 from md_warehouse where code = 'AUTO');

-- ===== 合同 =====
insert into ct_contract(contract_no, name, supplier_id, type, amount, signed_date, start_date, end_date, status, remark, created_by, updated_by)
select 'CT-IND-001', '工业备件年度框架协议', s.id, 'FRAMEWORK', 500000,
       date_sub(curdate(), interval 90 day), date_sub(curdate(), interval 90 day),
       date_add(curdate(), interval 275 day), 'ACTIVE', '[演示] 工业备件框架', 0, 0
from sp_supplier s
where s.code = 'SUP-MRO-001'
  and not exists (select 1 from ct_contract where contract_no = 'CT-IND-001');

insert into ct_contract(contract_no, name, supplier_id, type, amount, signed_date, start_date, end_date, status, remark, created_by, updated_by)
select 'CT-IND-002', '工装量具采购合同', s.id, 'PURCHASE', 120000,
       date_sub(curdate(), interval 30 day), date_sub(curdate(), interval 30 day),
       date_add(curdate(), interval 335 day), 'ACTIVE', '[演示] 工装量具', 0, 0
from sp_supplier s
where s.code = 'SUP-TOOL-001'
  and not exists (select 1 from ct_contract where contract_no = 'CT-IND-002');

insert into ct_contract(contract_no, name, supplier_id, type, amount, signed_date, start_date, end_date, status, remark, created_by, updated_by)
select 'CT-IND-003', '劳保用品年度框架协议', s.id, 'FRAMEWORK', 80000,
       date_sub(curdate(), interval 45 day), date_sub(curdate(), interval 45 day),
       date_add(curdate(), interval 320 day), 'ACTIVE', '[演示] 劳保用品', 0, 0
from sp_supplier s
where s.code = 'SUP-SAFE-001'
  and not exists (select 1 from ct_contract where contract_no = 'CT-IND-003');

-- ===== 预算 =====
insert into fin_budget(department_id, fiscal_year, amount)
select d.id, year(curdate()), 20000000 from sys_department d
where d.code = 'MFG'
  and not exists (select 1 from fin_budget b where b.department_id = d.id and b.fiscal_year = year(curdate()));

-- ===== 期初库存（关键耗材） =====
insert into inv_balance(warehouse_id, material_id, quantity, created_by, updated_by)
select w.id, m.id, 25, 0, 0
from md_warehouse w join md_material m on m.code = 'MON-27-4K'
where w.code = 'IT'
  and not exists (select 1 from inv_balance b where b.warehouse_id = w.id and b.material_id = m.id);

insert into inv_ledger(warehouse_id, material_id, business_type, business_id, direction, quantity, balance_after, created_by, created_at)
select b.warehouse_id, b.material_id, 'OPENING', 0, 'IN', 25, 25, 0, utc_timestamp(6)
from inv_balance b join md_material m on m.id = b.material_id
where m.code = 'MON-27-4K'
  and not exists (select 1 from inv_ledger l where l.business_type = 'OPENING' and l.material_id = b.material_id and l.warehouse_id = b.warehouse_id);

insert into inv_balance(warehouse_id, material_id, quantity, created_by, updated_by)
select w.id, m.id, 120, 0, 0
from md_warehouse w join md_material m on m.code = 'BRG-6308'
where w.code = 'MRO'
  and not exists (select 1 from inv_balance b where b.warehouse_id = w.id and b.material_id = m.id);

insert into inv_ledger(warehouse_id, material_id, business_type, business_id, direction, quantity, balance_after, created_by, created_at)
select b.warehouse_id, b.material_id, 'OPENING', 0, 'IN', 120, 120, 0, utc_timestamp(6)
from inv_balance b join md_material m on m.id = b.material_id
where m.code = 'BRG-6308'
  and not exists (select 1 from inv_ledger l where l.business_type = 'OPENING' and l.material_id = b.material_id and l.warehouse_id = b.warehouse_id);

insert into inv_balance(warehouse_id, material_id, quantity, created_by, updated_by)
select w.id, m.id, 200, 0, 0
from md_warehouse w join md_material m on m.code = 'GLOVE-CUT'
where w.code = 'SAFE'
  and not exists (select 1 from inv_balance b where b.warehouse_id = w.id and b.material_id = m.id);

insert into inv_ledger(warehouse_id, material_id, business_type, business_id, direction, quantity, balance_after, created_by, created_at)
select b.warehouse_id, b.material_id, 'OPENING', 0, 'IN', 200, 200, 0, utc_timestamp(6)
from inv_balance b join md_material m on m.id = b.material_id
where m.code = 'GLOVE-CUT'
  and not exists (select 1 from inv_ledger l where l.business_type = 'OPENING' and l.material_id = b.material_id and l.warehouse_id = b.warehouse_id);
