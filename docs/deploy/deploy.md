# 知家 (zija) 部署指南

本文档面向私有部署场景，指导运维人员在一台 Linux 服务器上通过 Docker Compose 部署知家系统。

---

## 1. 前置条件

| 项目 | 最低版本 | 说明 |
|---|---|---|
| Docker Engine | 24+ | 容器运行时 |
| Docker Compose | v2 | `docker compose` 子命令（非独立的 `docker-compose`） |
| TLS 反向代理 | 任意（Nginx / Caddy / Traefik 等） | 终止 HTTPS，转发到 `web` 容器的 HTTP 端口 |
| 域名 | — | 指向服务器公网 IP 的域名（可选，也可用 IP + 自签证书） |

### 硬件建议

- CPU：2 核+
- 内存：4 GB+（Spring Boot 默认堆 256m–512m，PostgreSQL 约 256m）
- 磁盘：20 GB+（数据库 + 文件存储，视家庭物品数量而定）

---

## 2. 拉取代码

```bash
git clone https://github.com/<your-org>/zija.git
cd zija
```

如果使用私有镜像仓库，也可直接拉取预构建镜像。

---

## 3. 配置环境变量

复制示例文件并按需修改：

```bash
cp .env.example .env
```

### .env 关键变量说明

| 变量 | 必填 | 默认值 | 说明 |
|---|---|---|---|
| `ZIJA_POSTGRES_PASSWORD` | **是** | `change-this-password` | PostgreSQL 密码，**必须修改** |
| `ZIJA_DB_PASSWORD` | **是** | `change-this-password` | 应用连接数据库的密码，与上一条保持一致 |
| `ZIJA_PROFILES_ACTIVE` | **是** | *(空)* | 生产环境设为 `prod` |
| `ZIJA_VERSION` | 否 | `dev` | 显示在系统信息页的版本号 |
| `ZIJA_HTTP_PORT` | 否 | `8088` | 宿主机暴露的 HTTP 端口（供反代转发目标） |
| `ZIJA_POSTGRES_PORT` | 否 | `5432` | 宿主机暴露的 PostgreSQL 端口 |
| `ZIJA_FILE_STORAGE_PATH` | 否 | `/var/lib/zija/files` | 容器内文件存储路径（卷挂载） |
| `ZIJA_BACKUP_DIR` | 否 | `./backups` | 备份产物宿主机目录 |
| `ZIJA_AI_OLLAMA_BASE_URL` | 否 | `http://localhost:11434` | Ollama 地址；Compose 会透传到 app 容器，需保证容器可达 |
| `ZIJA_AI_CHAT_MODEL` | 否 | `qwen2.5:7b` | 默认聊天模型 |
| `ZIJA_AI_EMBEDDING_MODEL` | 否 | `nomic-embed-text` | 默认 embedding 模型，必须输出 768 维 |
| `ZIJA_SMTP_HOST` | 否 | *(空)* | SMTP 服务器地址（不配置则邮件功能静默禁用） |
| `ZIJA_SMTP_PORT` | 否 | `587` | SMTP 端口 |
| `ZIJA_SMTP_USERNAME` | 否 | | SMTP 用户名 |
| `ZIJA_SMTP_PASSWORD` | 否 | | SMTP 密码 |
| `ZIJA_SMTP_FROM` | 否 | | 发件人地址 |
| `ZIJA_SMTP_TLS` | 否 | `true` | 是否启用 STARTTLS |

### 生产环境最小配置示例

```dotenv
ZIJA_POSTGRES_DB=zija
ZIJA_POSTGRES_USER=zija
ZIJA_POSTGRES_PASSWORD=<替换为强随机密码>
ZIJA_DB_URL=jdbc:postgresql://postgres:5432/zija
ZIJA_DB_USERNAME=zija
ZIJA_DB_PASSWORD=<替换为强随机密码>
ZIJA_VERSION=1.0.0
ZIJA_PROFILES_ACTIVE=prod
ZIJA_HTTP_PORT=8088
```

> **注意**：`ZIJA_DB_URL` 中的主机名 `postgres` 对应 `compose.yaml` 中的服务名，无需修改。

---

## 4. 生产 Profile（prod）

设置 `ZIJA_PROFILES_ACTIVE=prod` 后，Spring Boot 会额外加载 `application-prod.yml`：

| 配置项 | 效果 |
|---|---|
| `springdoc.swagger-ui.enabled=false` | 关闭 Swagger UI（生产环境不暴露 API 文档） |

**Secure Cookie 由传输层自动决定**，prod profile 不强制设置 `cookie.secure`（应用注册了自定义
`CookieSerializer`，`server.servlet.session.cookie.secure` 不会生效；Secure 标志跟随
`request.isSecure()`，即反代透传的 `X-Forwarded-Proto`）。因此：

- TLS 反代正确透传 `X-Forwarded-Proto: https` 时，会话 Cookie 自动带 Secure（仅 HTTPS 传输）；
- 纯 HTTP 暴露时不带 Secure（浏览器不会拒收 Cookie，但无加密可言，生产必须接 TLS）。

内置 `deploy/nginx/default.conf` 已实现透传（上游带 `X-Forwarded-Proto` 时原样转发，否则回退本机
scheme），TLS 反代只需设置 `X-Forwarded-Proto $scheme`（见 §5），无需额外配置。

---

## 5. TLS 反向代理配置

知家应用本身不处理 TLS 终止，需要在前端部署反向代理。以下为两种常见方案。

### 5.1 Nginx 反代示例

```nginx
server {
    listen 443 ssl http2;
    server_name zija.example.com;

    ssl_certificate     /etc/letsencrypt/live/zija.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/zija.example.com/privkey.pem;

    # 推荐：HSTS（仅 HTTPS 下发；纯 HTTP 站点下发会被浏览器忽略）
    add_header Strict-Transport-Security "max-age=63072000" always;

    location / {
        proxy_pass http://127.0.0.1:8088;   # 对应 ZIJA_HTTP_PORT
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;   # 关键：传递 https（compose 内 nginx 会透传该头）
        proxy_set_header X-Request-Id      $http_x_request_id;
    }
}

server {
    listen 80;
    server_name zija.example.com;
    return 301 https://$host$request_uri;
}
```

### 5.2 Caddy 反代示例

Caddy 自动管理 TLS 证书，配置更简洁：

```
zija.example.com {
    reverse_proxy localhost:8088
    header Strict-Transport-Security "max-age=63072000"   # 推荐：HSTS
}
```

Caddy 默认会传递 `X-Forwarded-Proto`，无需额外配置。

### 5.3 应用端信任代理

知家后端已配置 Tomcat 的 `RemoteIpValve`（`server.forward-headers-strategy=native`），信任所有 RFC 1918 私有网段：

```
10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, ::1
```

如果反代运行在非 RFC 1918 地址（如云厂商内网），需在 `application.yml` 的 `server.tomcat.remoteip.internal-proxies` 中追加对应网段。

---

## 6. 卷挂载说明

`compose.yaml` 定义了两个持久化卷：

| 卷名 | 容器内路径 | 用途 |
|---|---|---|
| `postgres-data` | `/var/lib/postgresql/data` | PostgreSQL 数据目录 |
| `zija-files` | `/var/lib/zija/files` | 用户上传的文件存储 |

这两个卷由 Docker 管理，数据持久化在宿主机的 `/var/lib/docker/volumes/` 下。

如需使用宿主机指定目录（便于备份），可在 `compose.yaml` 中改为绑定挂载：

```yaml
volumes:
  - /data/zija/postgres:/var/lib/postgresql/data
  - /data/zija/files:/var/lib/zija/files
```

> **警告**：从卷挂载切换到绑定挂载（或反之）需要先备份数据再迁移，不可直接修改后重启。

---

## 7. 首次部署与引导

### 7.1 构建并启动

```bash
# 构建镜像并启动所有服务
docker compose --env-file .env up -d --build
```

启动后可运行冒烟测试验证部署是否成功：

```bash
make compose-smoke
```

`make compose-smoke` 会启动完整栈、执行健康检查脚本（`scripts/compose-smoke.sh`）、确认三个服务均处于 healthy 状态，**然后自动清理（teardown）所有容器和临时卷**。它是一个验证工具，不是部署命令——适用于 CI 或部署后快速校验，不适用于保持服务运行。

### 7.2 验证服务状态

```bash
# 查看容器状态
docker compose ps

# 预期输出（三个服务均为 healthy）：
# NAME       STATUS
# postgres   Up (healthy)
# app        Up (healthy)
# web        Up (healthy)
```

### 7.3 健康检查端点

| 端点 | 服务 | 说明 |
|---|---|---|
| `GET /healthz` | web (nginx) | 返回 `200 ok`，用于负载均衡器探活 |
| `GET /actuator/health/liveness` | app | K8s liveness probe（仅检查应用进程存活） |
| `GET /actuator/health/readiness` | app | K8s readiness probe（检查数据库连接等就绪状态） |
| `GET /actuator/health` | app | 综合健康信息（生产环境 `show-details=never`，不暴露内部细节） |

验证示例：

```bash
# Nginx 层
curl -s http://localhost:8088/healthz
# ok

# 应用层（通过 Nginx 代理）
curl -s http://localhost:8088/actuator/health/readiness
# {"status":"UP"}
```

### 7.4 初始化家庭（Bootstrap）

首次部署后，系统尚无任何家庭和用户。通过浏览器访问前端页面，系统会自动进入引导流程：

1. 访问 `https://zija.example.com`（或 `http://localhost:8088`）
2. 系统检测到尚未初始化，显示创建家庭页面
3. 填写家庭名称、所有者姓名、邮箱、密码
4. 提交后系统创建家庭、所有者账户，并自动登录

> **安全提示**：引导接口 `POST /api/v1/household/bootstrap` 仅在系统未初始化时开放，初始化后自动关闭。建议在首次部署后立即完成引导，避免暴露在公网。

---

## 8. 所有者账户恢复

如果所有者忘记密码或账户被锁定，可通过容器内命令行工具生成恢复链接：

```bash
make recover-owner
```

该命令等效于：

```bash
docker compose exec app java -jar /app/zija.jar \
    --spring.main.web-application-type=none \
    --zija.command=recover-owner
```

执行后会输出一个有时效性的恢复链接，在浏览器中打开即可重置所有者密码。

---

## 9. 备份与恢复

### 9.1 备份

```bash
make backup-test
```

该命令执行 `scripts/backup.sh`，将 PostgreSQL 数据库转储和文件存储打包到 `ZIJA_BACKUP_DIR`（默认 `./backups/`）。

### 9.2 恢复验证

```bash
make restore-smoke
```

该命令执行 `scripts/restore.sh`，在临时 Docker 卷上恢复最近一次备份并验证数据完整性。

---

## 10. 升级

```bash
# 1. 拉取最新代码
git pull

# 2. 更新 .env 中的 ZIJA_VERSION（可选）
# ZIJA_VERSION=1.1.0

# 3. 重新构建并启动
docker compose --env-file .env up -d --build

# 4. 验证健康状态
docker compose ps
curl -s http://localhost:8088/actuator/health/readiness
```

数据库迁移由 Flyway 在应用启动时自动执行，无需手动操作。

---

## 11. 常见问题

### 会话 Cookie 不生效（登录后立即跳回登录页）

检查 TLS 反代是否正确传递 `X-Forwarded-Proto: https`。生产 Profile 启用了 `Secure` Cookie，如果反代未传递 HTTPS 协议标识，浏览器会拒绝存储 Cookie。

```bash
# 验证：检查响应头中的 Set-Cookie 是否包含 Secure 标志
curl -I -X POST https://zija.example.com/api/v1/auth/login \
  -d '{"username":"...","password":"..."}' \
  -H 'Content-Type: application/json'
```

### 容器启动后数据库连接失败

确认 `postgres` 服务已通过健康检查。`app` 服务配置了 `depends_on: condition: service_healthy`，但首次初始化 PostgreSQL 可能需要几秒：

```bash
docker compose logs postgres
docker compose logs app
```

### Swagger UI 仍然可访问

确认 `.env` 中设置了 `ZIJA_PROFILES_ACTIVE=prod`，然后重启应用容器：

```bash
docker compose --env-file .env up -d app
```

### 磁盘空间不足

检查 Docker 卷和日志：

```bash
docker system df
docker compose logs --tail=0 --follow   # 实时查看日志大小
```

定期执行 `make backup-test` 并清理旧备份。

---

## 12. 安全建议

1. **密码强度**：`ZIJA_POSTGRES_PASSWORD` 应使用 16 位以上随机字符串
2. **端口暴露**：`ZIJA_POSTGRES_PORT` 仅在需要外部访问数据库时映射，否则建议移除 `ports` 配置
3. **防火墙**：仅开放 80/443 端口，`ZIJA_HTTP_PORT` 仅对反代可达
4. **定期备份**：建议配置 cron 定时执行 `make backup-test`
5. **日志监控**：关注 `docker compose logs` 中的异常信息
6. **TLS 证书**：使用 Let's Encrypt 等自动续期方案，避免证书过期
