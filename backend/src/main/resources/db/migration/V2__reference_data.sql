insert into sys_department(id,parent_id,code,name,sort_order) values
(1,null,'HQ','集团总部',1),(2,1,'RD','研发部',10),(3,1,'MFG','制造部',20),(4,1,'PROC','采购部',30),(5,1,'FIN','财务部',40),(6,1,'WH','仓储部',50);

insert into sys_role(id,code,name,data_scope) values
(1,'ADMIN','系统管理员','ALL'),(2,'APPLICANT','申请人','SELF'),(3,'DEPT_MANAGER','部门经理','DEPARTMENT'),(4,'PURCHASER','采购员','ALL'),
(5,'FINANCE','财务','ALL'),(6,'WAREHOUSE','仓管员','ALL'),(7,'GENERAL_MANAGER','总经理','ALL'),(8,'AUDITOR','审计员','ALL');

insert into sys_permission(id,code,name,type,parent_code,sort_order) values
(1,'dashboard:view','经营看板','MENU',null,1),(2,'masterdata:view','主数据','MENU',null,2),(3,'masterdata:edit','维护主数据','BUTTON','masterdata:view',3),
(4,'supplier:view','供应商','MENU',null,4),(5,'supplier:edit','维护供应商','BUTTON','supplier:view',5),
(6,'requisition:view','采购申请','MENU',null,6),(7,'requisition:create','创建采购申请','BUTTON','requisition:view',7),(8,'requisition:submit','提交采购申请','BUTTON','requisition:view',8),
(9,'workflow:approve','审批中心','MENU',null,9),(10,'purchaseorder:view','采购订单','MENU',null,10),(11,'purchaseorder:edit','维护采购订单','BUTTON','purchaseorder:view',11),
(12,'inventory:view','库存管理','MENU',null,12),(13,'inventory:receive','采购收货','BUTTON','inventory:view',13),(14,'agent:use','AI采购助手','MENU',null,14),
(15,'system:user:view','用户管理','MENU',null,15),(16,'audit:view','审计查询','MENU',null,16);

insert into sys_role_permission(role_id,permission_id) select 1,id from sys_permission;
insert into sys_role_permission(role_id,permission_id) values
(2,1),(2,2),(2,4),(2,6),(2,7),(2,8),(2,14),
(3,1),(3,2),(3,4),(3,6),(3,9),(3,14),
(4,1),(4,2),(4,4),(4,5),(4,6),(4,9),(4,10),(4,11),(4,12),(4,14),
(5,1),(5,6),(5,9),(5,10),(5,14),
(6,1),(6,2),(6,10),(6,12),(6,13),(6,14),
(7,1),(7,6),(7,9),(7,10),(7,14),
(8,1),(8,2),(8,4),(8,6),(8,10),(8,12),(8,16);

insert into md_material_category(id,parent_id,code,name,description,sort_order) values
(1,null,'IT','IT设备','办公及数据中心设备',10),(2,1,'PC','计算机','台式机、笔记本与服务器',11),(3,1,'COMP','电脑配件','内存、硬盘及外设',12),
(4,null,'MRO','MRO备件','生产维护维修备件',20),(5,4,'BEARING','轴承','工业轴承',21),(6,4,'ELECTRICAL','电气元件','电机与传感器',22),
(7,null,'EQUIPMENT','工厂设备','生产制造设备',30),(8,7,'CNC','数控设备','数控机床',31),(9,7,'ROBOT','工业机器人','自动化机器人',32);
insert into md_category_attribute(category_id,code,name,value_type,required,options_json,sort_order) values
(2,'cpu','处理器','TEXT',1,null,1),(2,'memory','内存容量(GB)','NUMBER',1,null,2),(3,'interface','接口类型','ENUM',1,json_array('SATA','NVMe','USB','PCIe'),1),
(5,'innerDiameter','内径(mm)','NUMBER',1,null,1),(6,'voltage','额定电压(V)','NUMBER',1,null,1),(8,'axisCount','轴数','NUMBER',1,null,1),(9,'payload','负载(kg)','NUMBER',1,null,1);
insert into md_unit(id,code,name,precision_scale) values (1,'PCS','台/件',0),(2,'SET','套',0),(3,'BOX','箱',0),(4,'M','米',2);
insert into md_brand(id,code,name) values (1,'LENOVO','联想'),(2,'DELL','戴尔'),(3,'SKF','SKF'),(4,'SIEMENS','西门子'),(5,'FANUC','发那科'),(6,'KUKA','库卡');
insert into md_material(id,code,name,category_id,unit_id,brand_id,specification,tracking_mode,min_stock,attributes_json) values
(1,'PC-LT-001','研发笔记本电脑',2,1,1,'ThinkPad P系列 / 32GB / 1TB','SERIAL',5,json_object('cpu','Intel Core Ultra 7','memory',32)),
(2,'SRV-RACK-001','机架式服务器',2,1,2,'2U 双路服务器','SERIAL',2,json_object('cpu','Intel Xeon','memory',256)),
(3,'RAM-DDR5-032','DDR5服务器内存32GB',3,1,null,'DDR5 ECC 32GB','BATCH',20,json_object('interface','PCIe')),
(4,'BRG-6205','深沟球轴承6205',5,1,3,'6205-2Z/C3','BATCH',100,json_object('innerDiameter',25)),
(5,'MOTOR-7K5','三相异步电机7.5kW',6,1,4,'7.5kW 380V','SERIAL',3,json_object('voltage',380)),
(6,'SENSOR-PROX','接近传感器',6,1,4,'M18 PNP 常开','BATCH',30,json_object('voltage',24)),
(7,'CNC-VMC850','立式数控加工中心',8,1,5,'VMC850 三轴','SERIAL',0,json_object('axisCount',3)),
(8,'ROBOT-KR10','六轴工业机器人',9,1,6,'KR10 R1100','SERIAL',0,json_object('payload',10));
insert into md_warehouse(id,code,name,address,manager) values (1,'MAIN','主原料仓','一号厂房北侧','王库管'),(2,'IT','IT设备仓','行政楼一层','王库管'),(3,'MRO','MRO备件仓','维修中心','王库管');
insert into sp_supplier(id,code,name,unified_credit_code,level,status,contact_name,contact_phone,email,address,score) values
(1,'SUP-IT-001','华东企业计算科技有限公司','91310000DEMO00001A','A','QUALIFIED','陈经理','13800001001','sales@example.com','上海市浦东新区',92.50),
(2,'SUP-MRO-001','精工工业备件有限公司','91320000DEMO00002B','A','QUALIFIED','刘经理','13800001002','mro@example.com','江苏省苏州市',95.00),
(3,'SUP-EQP-001','智能装备系统集成有限公司','91440000DEMO00003C','B','QUALIFIED','周经理','13800001003','equipment@example.com','广东省深圳市',88.00);
