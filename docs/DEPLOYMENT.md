# 部署与演示环境

## 一、本地一键启动

前置：Docker Desktop（Compose v2）、Git。Windows 上若 9000 端口被 WSL 占用，用下面脚本会自动改用 9100/9101。

```powershell
# 首次会从 .env.example 复制 .env，请修改其中的密钥后再执行一次
.\scripts\demo-up.ps1 -Build

# 停止（保留数据卷）
.\scripts\demo-up.ps1 -Down
```

入口：

- 管理端 <http://localhost:3000>
- Swagger <http://localhost:8080/swagger-ui.html>
- MinIO 控制台 <http://localhost:9001>（或 9101）
- 健康检查 <http://localhost:8080/actuator/health>

演示账号见 README「演示账号」一节。

## 二、云服务器部署（最小可用）

1. 准备一台 2C4G 以上、已装 Docker 的 Linux 机器，放行 80/443。
2. 拉代码，复制环境变量并**全部替换**：

```bash
cp .env.example .env
# 必改：DB_PASSWORD / MYSQL_ROOT_PASSWORD / MINIO_ACCESS_KEY / MINIO_SECRET_KEY
#      JWT_SECRET（≥32 字节随机）
#      MINIO_PUBLIC_ENDPOINT（浏览器可访问的地址，必须 HTTPS）
#      AGENT_INTERNAL_TOKEN（仅启用 Agent 时，≥32 字节，且与 harness 一致）
docker compose up -d --build
docker compose ps
```

3. 反向代理：把 `frontend:80` 暴露到 443，Nginx 只代理 `/api/v1/`；`/api/`（Agent 内部工具面）**不要**对外暴露。
4. 安全基线：
   - 更换全部演示密码，删除不需要的账号；
   - 限制 Swagger 与 MinIO 控制台来源 IP；
   - 数据库与对象存储端口不对外；
   - `.env` 不入库，服务器权限 600。

## 三、冒烟验证

```bash
curl -fsS http://localhost:8080/actuator/health
python3 scripts/p1-acceptance-smoke.py http://localhost:3000
python3 scripts/p2-acceptance-smoke.py http://localhost:3000
python3 scripts/knowledge-base-smoke.py http://localhost:3000
```

Agent 工具面需要 `AGENT_ENABLED=true` 且直连后端：

```bash
INTERNAL_BASE=http://localhost:8080 AGENT_INTERNAL_TOKEN=... python3 scripts/agent-contract-smoke.py
```

## 四、常见问题

| 现象 | 原因 / 处理 |
|---|---|
| MinIO 预签名地址打不开 | `MINIO_PUBLIC_ENDPOINT` 必须是**浏览器**能访问的地址；远程部署用 HTTPS 域名 |
| 后端启动即失败，提示内部令牌 | `AGENT_INTERNAL_TOKEN` 少于 32 位或仍是占位值；启用 Agent 时必须替换 |
| 9000 端口被占用 | Windows 上多为 WSL；用 `demo-up.ps1` 自动改用 9100，或释放端口 |
| Flyway 校验失败 | 不要修改已执行过的迁移；新增变更请建新版本文件 |
| 浏览器登录后 401 | 检查系统时间与 `JWT_SECRET` 是否一致；刷新令牌依赖 Redis |
| 演示数据重复 | 迁移与初始化器均按业务键幂等；重复执行不会重复插入 |

## 五、演示素材

- 三分钟演示脚本：`docs/DEMO.md`
- 界面截图：`docs/screenshots/`
- 接口契约：`docs/AGENT_TOOL_CONTRACT.md`
