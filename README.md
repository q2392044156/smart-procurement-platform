# ProcureMind 智能采购平台

面向制造企业的采购与物资供应链平台。业务系统与 `procurement-agent-harness` 在源码和运行时上解耦；不启动 Agent 时，登录、审批、订单、收货、库存和看板均可独立运行。

## 技术架构

- `frontend/`：Vue 3、TypeScript、Vite、Pinia、Element Plus、ECharts
- `backend/`：Java 21、Spring Boot 3.5、Spring Security、MyBatis-Plus、Flowable 7.2、Flyway
- 基础设施：MySQL 8.4、Redis 7、MinIO、Docker Compose
- 可选智能能力：同级目录 `../procurement-agent-harness`，通过 Spring Boot 代理 SSE

## 一键启动

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

访问入口：

- 管理端：http://localhost:3000
- Swagger：http://localhost:8080/swagger-ui.html
- MinIO 控制台：http://localhost:9001
- 后端健康检查：http://localhost:8080/actuator/health

Flyway 会自动建表，应用首次启动自动写入 BCrypt 演示账号和制造业演示主数据。

## 演示账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 系统管理员 | `admin` | `Admin@123` |
| 申请人 | `requester` | `Request@123` |
| 部门经理 | `manager` | `Manager@123` |
| 采购员 | `purchaser` | `Purchase@123` |
| 财务 | `finance` | `Finance@123` |
| 仓管员 | `warehouse` | `Warehouse@123` |
| 总经理 | `gm` | `Gm@123` |
| 审计员 | `auditor` | `Audit@123` |

建议演示顺序：申请人创建并提交申请 → 根据金额切换对应审批账号 → 采购员转换并提交订单 → 财务/总经理审批 → 仓管员分批收货 → 查看库存余额、流水及看板。

审批规则：5万元以下为部门经理、采购经理；5万至50万元增加财务；50万元及以上再增加总经理。申请人兼任当前审批角色时自动跳过该节点。

## 管理端已实现操作

- 物资主数据：查询和新增物资，维护树形分类及文本、数值、布尔、日期、枚举动态属性。
- 供应商：查询、新增、编辑，查看基础档案、资质证照和绩效评价。
- 采购申请：新建、编辑草稿或驳回单、提交、查看明细及审批时间线；审批后按明细调整订单单价并转换订单。
- 审批中心：审批前预览完整单据，填写意见后通过或驳回，查看已办任务。
- 采购订单：查看明细及审批时间线、提交审批；仓管员可分批收货，页面会预校验剩余数量、批次号和序列号数量/重复值。
- 库存与看板：查看库存余额、低库存状态、库存流水、收货记录、采购趋势、订单状态及供应商排名。
- 系统与审计：查看用户、组织树、角色数据范围、权限清单和操作审计。

文件预签名上传接口已经提供，但采购附件的业务关联表和表单控件尚未加入；请勿把仅上传到 MinIO 但未绑定业务单据的文件当作正式附件功能。

## 启用 Agent（可选）

准备好 `../procurement-agent-harness/.env` 后：

```powershell
$env:AGENT_ENABLED='true'
docker compose --profile agent up -d --build
```

该 profile 会额外启动 Agent API、MCP 网关和 Agent 专用 MongoDB。Spring 会从 JWT 中提取用户身份并覆盖浏览器传入的用户字段；MCP 再用内部令牌、用户 ID 与请求 ID访问业务后端。Agent 未启用或不可用时，AI 接口返回 503，但不影响业务接口。

## 本地开发

```powershell
cd backend
mvn spring-boot:run
```

```powershell
cd frontend
npm install
npm run dev
```

前端开发服务器将 `/api` 代理到 `http://localhost:8080`。

## 自动化验收

默认服务健康后，可以执行可重复的端到端业务验收：

```powershell
.\scripts\acceptance-smoke.ps1
```

脚本通过 Nginx 入口真实调用 API，覆盖角色权限与数据范围、三档金额审批链、驳回重提及多流程历史、申请转订单、订单审批、分批收货、幂等重放、超量收货、批次号校验、序列号唯一性以及库存余额一致性。脚本会写入带“验收”用途的演示单据。

浏览器端登录、菜单权限和越权路由由 Playwright 验证：

```powershell
cd frontend
npx playwright install chromium
npm run test:e2e
```

## 关键业务约束

- 采购申请：`DRAFT → PENDING_APPROVAL → APPROVED/REJECTED → CONVERTED/CANCELLED`
- 采购订单：`DRAFT → PENDING_APPROVAL → APPROVED → PARTIALLY_RECEIVED → RECEIVED → CLOSED`
- 已审批申请才能转换订单；已审批订单才能收货
- 批次物资必须登记批次号；序列号物资逐个登记且全局唯一
- 收货与库存入账处于同一事务；余额使用乐观锁；写入接口通过 `Idempotency-Key` 防重
- 金额使用 `DECIMAL(18,2)` / `BigDecimal`，Long ID 和金额在 JSON 中序列化为字符串
