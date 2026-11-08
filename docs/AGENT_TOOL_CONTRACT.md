# Agent 内部工具契约

面向 `procurement-agent-harness`（MCP 网关）的内部接口约定。所有接口挂在 `/api/**` 下，**不经 Nginx 对外暴露**，只接受内部令牌。

## 1. 认证与权限

| 请求头 | 说明 |
|---|---|
| `X-Internal-Token` | 与后端 `AGENT_INTERNAL_TOKEN` 一致，至少 32 位；占位值会阻止后端启动 |
| `X-User-Id` | 平台用户 ID，后端用它加载角色、权限与数据范围 |
| `X-Request-Id` | 可选，写入审计日志便于串联 |

- `AGENT_ENABLED=false` 时所有 `/api/**` 返回 **404**，业务系统不受影响；
- 权限在过滤器内按路径判定，再叠加后端 `@PreAuthorize` 与数据范围，两层都必须通过。

| 路径前缀 | 读 | 写 |
|---|---|---|
| `/api/suppliers/` | `supplier:view` | `supplier:edit` |
| `/api/parts/` | `masterdata:view` | `masterdata:edit` |
| `/api/orders/` | `purchaseorder:view` | `purchaseorder:edit` |
| `/api/inventory/` | `inventory:view` | `inventory:receive` |
| `/api/requisitions/` | `requisition:view` | `requisition:create` |
| `/api/workflow/` | `workflow:approve` | — |
| `/api/receipts/` | `inventory:view` | — |
| `/api/dashboard/` | `dashboard:view` | — |
| `/api/kb/` | `kb:view` | — |

## 2. 响应与错误

统一包装：`{ "code": 0, "message": "success", "data": ..., "requestId": "..." }`。
分页数据：`{ "records": [], "total": 0, "current": 1, "size": 10, "pages": 0 }`。

| HTTP | 含义 | Agent 应采取的动作 |
|---|---|---|
| 400 | 参数或业务前置条件不满足 | 修正参数后重试，不要原样重放 |
| 401 | 内部令牌或用户身份无效 | 终止，检查配置 |
| 403 | 当前用户无该业务权限 | 终止，换有权限的账号或提示用户 |
| 404 | 不存在，或不在当前用户可见范围/数据范围 | 视为不可见，不要猜测 |
| 409 | 业务状态冲突（重复、越权状态、引用保护） | **不要重试**，向用户说明原因 |
| 503 | Agent 未启用或不可用 | 终止 |

## 3. 状态口径

订单状态统一使用字符串（出参只给字符串）：

`DRAFT` → `PENDING_APPROVAL` → `APPROVED` → `PARTIALLY_RECEIVED` → `RECEIVED` → `CLOSED`，另有 `REJECTED`、`CANCELLED`。

- 入参兼容历史数字：`0=DRAFT`、`1=APPROVED`、`2=PARTIALLY_RECEIVED`、`3=RECEIVED`、`4=CLOSED`；未知值返回 **400**，不再静默返回空列表。
- 状态变更（`PATCH /api/orders/update-status/{id}`）只接受：`1|SUBMIT|PENDING_APPROVAL`（提交审批）与 `4|CLOSE|CLOSED`（关闭已收货订单），其余 **409**。
- 供应商同时返回 `status`（布尔，兼容旧协议）与 `lifecycleStatus`（`PENDING/QUALIFIED/SUSPENDED/BLACKLISTED`）。

## 4. 幂等

`POST /api/orders/create`、`PUT /api/orders/update/{id}` 支持幂等：

- 优先使用请求头 `Idempotency-Key`（同一 key 的重复提交返回首次结果）；
- 未传时按「操作 + 用户 + 规范化请求内容」生成指纹，**Agent 重放同一工具调用不会重复建单**；
- 自动指纹窗口 10 分钟，超出窗口视为新的业务操作；
- 执行失败会标记为 `FAILED`，允许用同一请求重试。

## 5. 端点清单

### 供应商
- `GET /api/suppliers/search?name=`
- `GET /api/suppliers/page?current=&size=&name=&status=&creditRating=`（`status` 支持 `1|0|QUALIFIED|SUSPENDED`）
- `GET /api/suppliers/get/{id}`
- `POST /api/suppliers/create` — 只创建为 `PENDING`，需先补有效资质才能转合格
- `PATCH /api/suppliers/update-status/{id}?status=` — 转 `QUALIFIED` 时校验有效资质

### 物资
- `GET /api/parts/search|page|get/{id}|supplier/{supplierId}`
- `POST /api/parts/create` — 分类/单位必须存在，否则 **400**（不再回退 `id=1`）

### 订单
- `GET /api/orders/page|get/{id}|search-details|statistics`（`page` 走数据范围）
- `GET /api/orders/get/{id}` — 同时返回 `items` 与 `orderDetail`（相同内容）
- `POST /api/orders/create` — 直采草稿单，校验合格供应商、物资有效性、数量/单价
- `PUT /api/orders/update/{id}` — 仅草稿/驳回态，整体替换明细
- `PATCH /api/orders/update-status/{id}?status=`

### 库存
- `GET /api/inventory/warning|page|check|get/{id}`
- `POST /api/inventory/inbound|outbound` — 固定 **409**，必须走正式收货流程

### 采购申请 / 审批 / 收货 / 看板
- `GET /api/requisitions/page?current=&size=&keyword=&status=`（数据范围生效）
- `GET /api/requisitions/get/{id}`
- `GET /api/workflow/todo`、`GET /api/workflow/done`、`GET /api/workflow/timeline/{businessType}/{businessId}`
- `GET /api/receipts/page?current=&size=&orderNo=`、`GET /api/receipts/get/{id}`
- `GET /api/dashboard/summary`

### 知识库（RAG 取数）
- `GET /api/kb/visible-doc-ids` — 当前用户可见的**已发布**文档，检索前过滤，避免越权召回
- `GET /api/kb/documents/changed?since=ISO-8601` — 增量变更（含归档与删除标记），用于重建索引
- `GET /api/kb/documents/{id}/content` — 可见性校验后返回预签名下载地址

> 原文仍存放在 MinIO；检索服务只保存切片与向量，删除/归档以 `changed` 接口的 `status`、`deleted` 为准。

### 提醒（P1）
- `GET /api/alerts/summary?qualificationDays=30&deliveryDays=7&approvalHours=24`
  返回三块：`qualifications`（资质临期/过期）、`deliveries`（交付临期/逾期）、`approvals`（审批积压），每块含 `count`、`overdueCount` 与 `items`。

## 6. 分页与单号

- 分页列表统一返回 `{records,total,current,size,pages}`，`total/pages` 为数字；`size` 上限 100。
  主列表分页端点：`/api/v1/materials/page`、`/api/v1/suppliers/page`、`/api/v1/requisitions/page`、
  `/api/v1/purchase-orders/page`、`/api/v1/receipts/page`、`/api/v1/inventory/balances/page`、
  `/api/v1/inventory/ledger/page`。原有数组接口保留给下拉选择与小数据量场景。
- 业务单号由 Redis 按天自增：`PR20261108-0001`、`PO20261108-0001`、`GRN20261108-0001`、`KB20261108-0001`。
  Agent 不需要生成单号，创建接口会自动分配并在响应中返回。
