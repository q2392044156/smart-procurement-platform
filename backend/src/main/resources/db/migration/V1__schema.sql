create table sys_department (
  id bigint primary key auto_increment, parent_id bigint null, code varchar(50) not null unique, name varchar(100) not null,
  sort_order int not null default 0, created_by bigint not null default 0, created_at timestamp(6) not null default current_timestamp(6),
  updated_by bigint not null default 0, updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);
create table sys_user (
  id bigint primary key auto_increment, username varchar(64) not null unique, password_hash varchar(100) not null, display_name varchar(100) not null,
  department_id bigint not null, enabled tinyint not null default 1, created_by bigint not null default 0, created_at timestamp(6) not null default current_timestamp(6),
  updated_by bigint not null default 0, updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);
create table sys_role (
  id bigint primary key auto_increment, code varchar(64) not null unique, name varchar(100) not null,
  data_scope enum('ALL','DEPARTMENT','DEPARTMENT_AND_CHILDREN','SELF') not null default 'SELF',
  created_by bigint not null default 0, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null default 0,
  updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);
create table sys_permission (
  id bigint primary key auto_increment, code varchar(100) not null unique, name varchar(100) not null, type enum('MENU','BUTTON','API') not null,
  parent_code varchar(100) null, sort_order int not null default 0
);
create table sys_user_role (user_id bigint not null, role_id bigint not null, primary key(user_id,role_id));
create table sys_role_permission (role_id bigint not null, permission_id bigint not null, primary key(role_id,permission_id));

create table md_material_category (
  id bigint primary key auto_increment, parent_id bigint null, code varchar(50) not null unique, name varchar(100) not null, description varchar(500), sort_order int not null default 0,
  created_by bigint not null default 0, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null default 0, updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);
create table md_category_attribute (
  id bigint primary key auto_increment, category_id bigint not null, code varchar(50) not null, name varchar(100) not null,
  value_type enum('TEXT','NUMBER','BOOLEAN','DATE','ENUM') not null, required tinyint not null default 0, options_json json null, sort_order int not null default 0,
  unique key uk_category_attribute(category_id,code)
);
create table md_unit (id bigint primary key auto_increment, code varchar(30) not null unique, name varchar(50) not null, precision_scale int not null default 0);
create table md_brand (id bigint primary key auto_increment, code varchar(50) not null unique, name varchar(100) not null);
create table md_material (
  id bigint primary key auto_increment, code varchar(50) not null unique, name varchar(200) not null, category_id bigint not null, unit_id bigint not null, brand_id bigint null,
  specification varchar(500), tracking_mode enum('QUANTITY','BATCH','SERIAL') not null default 'QUANTITY', min_stock decimal(18,4) not null default 0,
  attributes_json json null, enabled tinyint not null default 1,
  created_by bigint not null default 0, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null default 0, updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);
create table md_warehouse (
  id bigint primary key auto_increment, code varchar(50) not null unique, name varchar(100) not null, address varchar(300), manager varchar(100), enabled tinyint not null default 1,
  created_by bigint not null default 0, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null default 0, updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);

create table sp_supplier (
  id bigint primary key auto_increment, code varchar(50) not null unique, name varchar(200) not null, unified_credit_code varchar(50), level enum('A','B','C','D') not null default 'B',
  status enum('PENDING','QUALIFIED','SUSPENDED','BLACKLISTED') not null default 'PENDING', contact_name varchar(100), contact_phone varchar(50), email varchar(100), address varchar(300), score decimal(5,2) not null default 0,
  created_by bigint not null default 0, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null default 0, updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);
create table sp_supplier_qualification (
  id bigint primary key auto_increment, supplier_id bigint not null, name varchar(200) not null, file_id bigint null, valid_from date null, valid_until date null, status varchar(30) not null default 'VALID'
);
create table sp_supplier_evaluation (
  id bigint primary key auto_increment, supplier_id bigint not null, period varchar(20) not null, quality_score decimal(5,2), delivery_score decimal(5,2), service_score decimal(5,2), total_score decimal(5,2), comment varchar(500), created_at timestamp(6) not null default current_timestamp(6)
);

create table pr_requisition (
  id bigint primary key auto_increment, requisition_no varchar(50) not null unique, department_id bigint not null, applicant_id bigint not null,
  required_date date not null, purpose varchar(500) not null, budget_amount decimal(18,2) not null default 0,
  status enum('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','CONVERTED','CANCELLED') not null default 'DRAFT', process_instance_id varchar(64), rejection_reason varchar(1000),
  created_by bigint not null, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null, updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);
create table pr_requisition_item (
  id bigint primary key auto_increment, requisition_id bigint not null, material_id bigint not null, quantity decimal(18,4) not null, estimated_unit_price decimal(18,2) not null, remark varchar(500),
  created_by bigint not null, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null, updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);

create table po_purchase_order (
  id bigint primary key auto_increment, order_no varchar(50) not null unique, requisition_id bigint null, supplier_id bigint not null, purchaser_id bigint not null,
  delivery_date date not null, tax_rate decimal(6,4) not null default 0, total_amount decimal(18,2) not null default 0,
  status enum('DRAFT','PENDING_APPROVAL','APPROVED','PARTIALLY_RECEIVED','RECEIVED','CLOSED','REJECTED','CANCELLED') not null default 'DRAFT', process_instance_id varchar(64), terms varchar(1000),
  created_by bigint not null, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null, updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);
create table po_purchase_order_item (
  id bigint primary key auto_increment, purchase_order_id bigint not null, material_id bigint not null, quantity decimal(18,4) not null, received_quantity decimal(18,4) not null default 0,
  unit_price decimal(18,2) not null, tax_rate decimal(6,4) not null default 0, remark varchar(500),
  created_by bigint not null, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null, updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);

create table inv_receipt (
  id bigint primary key auto_increment, receipt_no varchar(50) not null unique, purchase_order_id bigint not null, warehouse_id bigint not null, receiver_id bigint not null,
  receipt_date timestamp(6) not null, remark varchar(500), created_by bigint not null, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null,
  updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);
create table inv_receipt_item (
  id bigint primary key auto_increment, receipt_id bigint not null, order_item_id bigint not null, material_id bigint not null, quantity decimal(18,4) not null, batch_no varchar(100), serial_numbers_json json null,
  created_by bigint not null, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null, updated_at timestamp(6) not null default current_timestamp(6), version int not null default 0, deleted tinyint not null default 0
);
create table inv_balance (
  id bigint primary key auto_increment, warehouse_id bigint not null, material_id bigint not null, quantity decimal(18,4) not null default 0,
  created_by bigint not null default 0, created_at timestamp(6) not null default current_timestamp(6), updated_by bigint not null default 0, updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), version int not null default 0, deleted tinyint not null default 0,
  unique key uk_balance(warehouse_id,material_id)
);
create table inv_batch (
  id bigint primary key auto_increment, warehouse_id bigint not null, material_id bigint not null, batch_no varchar(100) not null, quantity decimal(18,4) not null default 0, production_date date, expiry_date date,
  unique key uk_batch(warehouse_id,material_id,batch_no)
);
create table inv_serial (
  id bigint primary key auto_increment, warehouse_id bigint not null, material_id bigint not null, serial_no varchar(200) not null unique, status enum('IN_STOCK','ISSUED','SCRAPPED') not null default 'IN_STOCK', receipt_item_id bigint not null
);
create table inv_ledger (
  id bigint primary key auto_increment, warehouse_id bigint not null, material_id bigint not null, business_type varchar(30) not null, business_id bigint not null,
  direction enum('IN','OUT','ADJUST') not null, quantity decimal(18,4) not null, balance_after decimal(18,4) not null, batch_no varchar(100), created_by bigint not null, created_at timestamp(6) not null default current_timestamp(6), request_id varchar(64)
);

create table fs_file_metadata (
  id bigint primary key auto_increment, original_name varchar(255) not null, object_key varchar(500) not null unique, content_type varchar(150), size_bytes bigint, bucket varchar(100) not null, uploader_id bigint not null, created_at timestamp(6) not null default current_timestamp(6), deleted tinyint not null default 0
);
create table sys_idempotency_record (
  id bigint primary key auto_increment, idempotency_key varchar(100) not null, user_id bigint not null, operation varchar(100) not null, response_json json null, status enum('PROCESSING','COMPLETED','FAILED') not null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6), unique key uk_idempotency(idempotency_key,user_id,operation)
);
create table sys_operation_audit (
  id bigint primary key auto_increment, user_id bigint, username varchar(64), method varchar(10), path varchar(500), result varchar(20), error_message varchar(1000), request_id varchar(64), duration_ms bigint, created_at timestamp(6) not null
);

create index idx_req_status on pr_requisition(status);
create index idx_req_applicant on pr_requisition(applicant_id);
create index idx_po_status on po_purchase_order(status);
create index idx_ledger_material on inv_ledger(warehouse_id,material_id,created_at);
