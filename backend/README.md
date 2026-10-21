# Platform backend

这是采购平台的独立 Web/BFF 后端，默认监听 `8080`。它不导入 Agent 仓库中的任何 Python 包，因此即使 Agent 未安装或未启动，也可以独立启动并通过 `/health`。

当前从原项目迁移出的前端只有 AI 采购助手页面，相关 `/api/chat`、`/api/history` 和 `/api/download` 接口由本服务转发给可选的 Agent 服务。后续 Spring Boot 业务后端落地时，可逐步用真实的供应商、物资、采购和库存接口替换这个轻量 BFF。

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8080
```

Agent 未启动时，健康检查仍返回 HTTP 200；实时可用性由 `/api/capabilities` 检测，只有调用 AI 相关接口时才返回 HTTP 503。
