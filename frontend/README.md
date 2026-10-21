# Platform frontend

从原 Agent 仓库迁移出的 Next.js/React Web 界面。浏览器只访问同源 `/api`，开发服务器再将请求转发给独立的平台后端，默认地址为 `http://localhost:8080`。

```powershell
npm ci
npm run dev
```

可选配置：

```dotenv
PLATFORM_BACKEND_URL=http://localhost:8080
NEXT_PUBLIC_API_BASE_URL=/api
```

前端不再直接连接 Agent 的 `8000` 端口。Agent 是否启用由平台后端负责判断。
