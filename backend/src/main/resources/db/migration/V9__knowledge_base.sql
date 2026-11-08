-- 知识库：内部资料与文档管理（原文在 MinIO，元数据/权限在此，切片与向量由外部检索服务维护）

create table kb_document (
  id bigint primary key auto_increment,
  code varchar(50) not null,
  title varchar(200) not null,
  category enum('POLICY','PROCEDURE','CONTRACT','SUPPLIER','MANUAL','TEMPLATE','OTHER') not null default 'OTHER',
  summary varchar(500) null,
  tags varchar(300) null,
  owner_department_id bigint not null,
  visibility enum('ALL','DEPARTMENT','DEPARTMENT_AND_CHILDREN','SELF','CUSTOM') not null default 'ALL',
  status enum('DRAFT','PUBLISHED','ARCHIVED') not null default 'DRAFT',
  current_version int not null default 1,
  file_id bigint not null,
  effective_from date null,
  effective_to date null,
  created_by bigint not null,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_by bigint not null,
  updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
  version int not null default 0,
  deleted tinyint not null default 0,
  unique key uk_kb_document_code(code),
  key idx_kb_document_status(status,deleted),
  key idx_kb_document_owner(owner_department_id),
  key idx_kb_document_file(file_id)
);

create table kb_document_version (
  id bigint primary key auto_increment,
  document_id bigint not null,
  version int not null,
  file_id bigint not null,
  file_name varchar(255) not null,
  change_note varchar(300) null,
  created_by bigint not null,
  created_at timestamp(6) not null default current_timestamp(6),
  unique key uk_kb_version(document_id,version),
  key idx_kb_version_document(document_id,version)
);

create table kb_document_acl (
  id bigint primary key auto_increment,
  document_id bigint not null,
  subject_type enum('USER','ROLE') not null,
  subject_key varchar(64) not null,
  created_by bigint not null,
  created_at timestamp(6) not null default current_timestamp(6),
  unique key uk_kb_acl(document_id,subject_type,subject_key)
);

insert into sys_permission(code,name,type,parent_code,sort_order) values
('kb:view','知识库','MENU',null,40),
('kb:manage','维护知识库','BUTTON','kb:view',41);

-- 内部资料默认对所有已启用角色可读；维护权限仅系统管理员与主数据管理员。
insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where p.code='kb:view' and r.deleted=0 and r.enabled=1
on duplicate key update role_id=values(role_id);

insert into sys_role_permission(role_id,permission_id)
select r.id,p.id from sys_role r cross join sys_permission p
where p.code='kb:manage' and r.code in ('ADMIN','MASTER_DATA_ADMIN')
on duplicate key update role_id=values(role_id);
