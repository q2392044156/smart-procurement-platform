# ProcureMind 模块边界

项目采用模块化单体：部署仍是一个 Spring Boot 应用，但代码按业务能力组织，避免 Controller、SQL 和跨模块细节混杂。

## 后端依赖方向

```text
Controller（HTTP / DTO 校验）
    ↓
领域 Service（状态机、权限、事务）
    ↓
JdbcTemplate / Flowable / Redis / MinIO（基础设施）
```

- `system`：认证、组织、角色权限、数据范围和审计。
- `masterdata`：分类、动态属性、物资、仓库、单位和品牌。
- `supplier`：供应商、联系人、资质及评价。
- `requisition`：采购申请生命周期，只通过公开服务使用工作流和附件能力。
- `purchaseorder`：订单商业条款与生命周期。
- `inventory`：不可变收货事件、库存余额、跟踪对象和查询服务。
- `knowledge`：知识库文档元数据、版本、可见范围与下载授权；原文仍存放在对象存储，由 `file` 模块统一管理。
- `workflow`：Flowable 流程启动、处理、历史与活动流程取消。
- `file`：对象存储、文件元数据以及可复用的业务附件关联。
- `dashboard`：只读聚合查询。
- `agentintegration`：可选 Agent 的防腐层，负责协议转换与身份覆盖；它是旧 MCP 接口唯一允许保留直接兼容查询的边界。

普通 Controller 不允许依赖 `JdbcTemplate`，由 `ArchitectureTest` 持续约束。跨业务模块不得访问对方 Mapper/内部实现；共享能力以公开 Service 暴露。收货写入仍由单一事务覆盖收货单、订单累计量、跟踪库存、余额和流水。

`file` 模块通过 `FileReferenceChecker` 扩展点收集“哪些业务还在引用这个文件”，删除对象前统一校验；文件模块不认识任何具体业务模块，知识库等模块自行注册检查器即可。

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
