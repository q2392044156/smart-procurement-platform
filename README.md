# Smart Procurement Platform

纯 Web 前后端目录，与 `procurement-agent-harness` 同级部署。

## 边界

- `frontend/`：从原仓库迁移的 Next.js/React 采购助手界面。
- `backend/`：独立可启动的 Web/BFF 后端，不直接导入 Agent 源码。
- `procurement-agent-harness/`：可选 AI 能力，通过 HTTP/SSE 接入，不是本项目的启动前置条件。

目前原仓库没有 Java ERP 业务后端，因此这里没有伪装成已完成的采购业务服务。当前后端先稳定 Web 与 Agent 的进程边界；后续可在此目录新增计划中的 Spring Boot 业务后端，并逐步建设供应商、物资、询报价、采购、库存和审批模块。

## 独立启动

后端：

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8080
```

前端：

```powershell
cd frontend
npm ci
npm run dev
```

访问 `http://localhost:3000`。不启动 Agent 时，前后端本身能够启动，AI 请求会得到明确的 503 提示。

## 启用 Agent

另行启动 `procurement-agent-harness` 的 FastAPI 服务（默认 `http://localhost:8000`）。平台后端通过 `AGENT_SERVICE_URL` 配置其地址，不存在源码级依赖。
