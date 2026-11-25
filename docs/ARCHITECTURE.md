# ProcureMind 后端分层与边界

项目是单应用、单数据库的模块化单体：部署仍是一个 Spring Boot 进程，代码按**技术职责分层**（`controller` / `service` / `mapper` / `pojo` / `exception` / `filter` …），业务能力由 `service` 包内对应的 Service 收敛。

## 后端依赖方向

```text
Controller（HTTP / 参数校验 / 权限注解）
    ↓
Service（状态机、事务、跨能力编排）
    ↓
Mapper（接口 + resources/mapper/*.xml，持有全部 SQL）
    ↓
JdbcTemplate / Flowable / Redis / MinIO（基础设施）
```

分层规则由 `backend/src/test/java/com/procuremind/platform/ArchitectureTest.java` 用 ArchUnit 持续约束：

- Controller 不依赖 `JdbcTemplate`；`LegacyErpController` 是遗留 ERP 兼容面，显式豁免。
- Service 不依赖 `JdbcTemplate`，SQL 只能出现在 `mapper` 包与 `resources/mapper/` 下的 XML 里。
- Service、Filter 不反向依赖 Controller。
- `pojo` 保持独立，不依赖 `controller` / `service` / `filter` / `aop`。请求体统一放 `pojo/request`（41 个 record），响应壳 `ApiResponse`、`TokenPair`、`UserRecord`、`SqlFilter` 留在 `pojo` 根。

## 数据访问约定

- 每个业务能力一个 `XxxMapper` 接口（`@Mapper`，参数用 `@Param` 命名），对应一份 `backend/src/main/resources/mapper/XxxMapper.xml`；`application.yml` 里 `mybatis.mapper-locations=classpath*:mapper/**/*.xml`。
- 查询结果默认 `resultType="map"`，列别名就是返回 JSON 的字段名。**MyBatis 不会把 Map 的 key 转驼峰**，所以别名必须与旧 SQL 逐字一致（`display_name displayName` 这类显式别名照写）。
- 例外是**决策读**：`lock*` / `findForUpdate` / 少数 `count*` 这类「查出来马上要拿字段做判断」的查询，`resultType` 指向 `com.procuremind.platform.pojo.row` 下的 record（如 `PurchaseOrderLock`），SQL 列名靠 `map-underscore-to-camel-case` 对上 record 组件名。这样漏列会在构造时暴露，而不是在 `row.get("total_amount")` 处变成 NPE。前提是 `mybatis.configuration.arg-name-based-constructor-auto-mapping: true` 与编译期 `-parameters`（spring-boot-starter-parent 默认开启）。列表/分页投影继续用 Map，因为它们的职责是把行透传成 JSON。
- 用户输入只走 `<if>` + `#{}`；可变的数据范围片断用 `${scope}`，其值由 `DataScopeService` 的 `SqlFilter.literal()` 生成——实参全是服务端生成的 `Long` 或枚举名，不含请求内容。
- 可空参数显式写 `jdbcType`（`VARCHAR` / `BIGINT` / `DATE`），避免 null 绑定在部分驱动上报错。
- `MapperXmlTest` 解析全部 XML 并断言「每个接口方法都有对应 statement」，`MyBatisWiringTest` 用 `ApplicationContextRunner` 断言 mapper bean 能注册；两者都不连数据库。
- `LockMappingTest`（Testcontainers）逐条断言六个加锁查询的 record 字段非空——漏一列就红，正是当初 `lockById` 漏 `total_amount` 导致订单提交 500 的回归闸门。
- 已知缺口：`PlatformIntegrationTest`（Testcontainers）是唯一会真正执行这些 SQL 的测试，需要 Docker 才能跑。
- `ApiContractTest` 把 20 个高频端点的响应形状沉成 `backend/src/test/resources/contract/api-shapes.json`：字段名、嵌套、标量类型变了就红，值变了不会。有意改契约时用 `-Dcontract.update=true` 重新生成，并把 json diff 一起提交。
- **数值口径**（与 `ApiContractTest` 一起守）：
  - `BigDecimal`（金额、数量、单价、税率、评分）→ **字符串**，前端只展示不参与算术，避免 JS 精度丢分位；
  - `Long` / `int`（计数、时长、天数、文件字节数）→ **数字**，都是能安全进 JS 的整数；
  - **ID 一律字符串**：SQL 里 `cast(x.id as char) id`，跨表外键也要 cast；只有 Java 侧拿不到 SQL 别名时才用 `@JsonSerialize(using = ToStringSerializer.class)`（如 `/auth/me` 的 `CurrentUser`）。

  机制在 `backend/src/main/java/com/procuremind/platform/config/JacksonConfig.java`：只把 `BigDecimal` 写成字符串，`Long` 保持数字。历史上这里曾把 `Long` 也写成字符串（为保护 snowflake ID），副作用是计数跟着变成 `"21"`，而 `datediff` / `timestampdiff` 这类列在不同驱动版本下时而是 `Long`、时而是 `Integer`——同一个字段忽字符串忽数字，`daysLeft` 就因此让 CI 红过一次。现在边界是「金额与 ID 用字符串，其余数值用数字」，ID 的字符串化交给 SQL 显式完成。

同一层内**不再按业务分包**：控制器在 `controller` 下平铺，服务在 `service` 下平铺，业务归属由类名表达（`RequisitionService`、`PurchaseOrderService`…）。跨业务调用只走对方 Service 的公开方法，不直接访问其 SQL。

> 2026-11 之前的版本按业务能力分包（`requisition/`、`purchaseorder/`…）。那次重组把 18 个业务包展平成与团队约定一致的分层目录，代价是**原本由包边界强制的模块隔离，降级为 ArchUnit 的层间规则加代码评审**。这一点在新增跨模块调用时要靠人守。

## 业务能力清单（实现均在 `service` 包）

- 认证与组织：`AuthService`、`JwtService`、`UserDirectory`、`SystemManagementService`、`DataScopeService`、`AuditService`（认证、组织、角色权限、数据范围和审计）。
- 主数据：`MasterDataService`（分类、动态属性、物资、仓库、单位和品牌）。
- 供应商：`SupplierService`（供应商、联系人、资质及评价）。
- 采购申请：`RequisitionService`（申请生命周期，只通过公开服务使用工作流和附件能力）。
- 采购订单：`PurchaseOrderService`（订单商业条款与生命周期）。
- 库存：`ReceiptService`、`IssueService`、`InventoryQueryService`（不可变收货与出库事件、库存余额、跟踪对象和查询服务）。
- 预算：`BudgetService`（部门年度预算与占用校验，申请提交与直采下单时生效）。
- 合同：`ContractService`（供应商合同台账、订单关联与到期/终止状态）。
- 知识库：`KnowledgeService`（文档元数据、版本、可见范围与下载授权；原文仍存放在对象存储，由文件能力统一管理）。
- 提醒：`AlertService`（供应商资质临期/过期、订单交付临期/逾期、审批积压的只读提醒查询）。
- 工作流：`WorkflowService`（Flowable 流程启动、处理、历史与活动流程取消）。
- 文件：`FileStorageService`、`BusinessAttachmentService`、`FileContentValidator`（对象存储、文件元数据以及可复用的业务附件关联）。
- 看板与分析：`DashboardService`、`AnalyticsService`（只读聚合查询）。
- Agent 防腐层：`controller` 下的 `*AgentController` 与 `LegacyErpController`，负责协议转换与身份覆盖；它是旧 MCP 接口唯一允许保留直接兼容查询的边界。
- 幂等：`IdempotencyService`（按显式 `Idempotency-Key` 或“操作 + 用户 + 请求内容”指纹去重，供 Agent 重试安全）。

收货写入仍由单一事务覆盖收货单、订单累计量、跟踪库存、余额和流水。

文件删除通过 `FileReferenceChecker` 扩展点收集“哪些业务还在引用这个文件”，删除对象前统一校验；文件能力不认识任何具体业务，知识库等模块自行注册检查器即可。

`LegacyErpController` 的写操作不再直接拼 SQL：供应商、物资、订单分别复用 `SupplierService`、`MasterDataService`、`PurchaseOrderService` 的校验与事务，状态口径由 `LegacyStatus` 统一适配，幂等由 `IdempotencyService` 统一兜底。

## 前端结构

```text
views/<business-module>  页面与业务交互
components/              跨模块复用组件
stores/                  会话和全局状态
api/                     统一 HTTP、令牌刷新与错误处理
types/                   公共类型
```

采购申请和采购订单复用 `BusinessAttachments`，审批时间线复用 `WorkflowTimeline`。页面不直接拼接鉴权头，所有请求通过统一 API 客户端；按钮权限用于改善体验，后端权限和数据范围始终是最终判定。

## 单据删除原则

- 删除是软删除，仅允许草稿、已取消等没有后续不可逆业务的状态。
- 收货及库存流水是审计事实，不提供修改或删除。
- 取消或删除来源订单后，如不存在其他有效订单，来源申请从 `CONVERTED` 恢复为 `APPROVED`，可以重新下单。
- 删除单据只解除附件关联；无业务引用的对象文件可再安全删除。
