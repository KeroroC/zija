#!/usr/bin/env bash
# 知家 Cloud Agent 环境 —— 每次启动的运行时初始化（start 阶段）
#
# 启动 Docker 守护进程、开发数据库（PostgreSQL），并拉起后端（Spring Boot，
# :8080）与前端（Vite，:5173）开发服务。
#
# 长期运行的进程放入独立 socket 的持久 tmux 会话（tmux server 作为守护进程
# 独立于本脚本存活），从而在 start 命令返回后依然存活——这与平台 terminals
# 的持久化机制一致。脚本必须可重复执行：已在运行的会话/端口将被跳过。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TMUX_BIN="$(command -v tmux || echo /exec-daemon/tmux)"
TM=("$TMUX_BIN" -L zija)   # 独立 socket，避免与平台自身 terminals 冲突

port_in_use() { { exec 3<>"/dev/tcp/127.0.0.1/$1"; } 2>/dev/null && { exec 3>&- 3<&-; return 0; } || return 1; }
tmux_has()   { "${TM[@]}" has-session -t "=$1" 2>/dev/null; }
tmux_run()   { tmux_has "$1" || "${TM[@]}" new-session -d -s "$1" -c "$ROOT" -- bash -lc "$2"; }

# 1. Docker 守护进程（持久 tmux 会话）。云 VM 为嵌套容器，使用 fuse-overlayfs
#    存储驱动（由 /etc/docker/daemon.json 指定）。
if ! docker info >/dev/null 2>&1; then
  sudo mkdir -p /etc/docker
  if [ ! -f /etc/docker/daemon.json ]; then
    echo '{"storage-driver":"fuse-overlayfs"}' | sudo tee /etc/docker/daemon.json >/dev/null
  fi
  tmux_run dockerd 'sudo dockerd 2>&1 | tee /tmp/dockerd.log'
  for _ in $(seq 1 30); do
    sudo docker info >/dev/null 2>&1 && break
    sleep 1
  done
  # 让 ubuntu 用户免 sudo 访问 docker（开发环境务实取舍）
  sudo chmod 666 /var/run/docker.sock || true
fi

# 2. 开发数据库（由 dockerd 管理的容器，独立于本脚本存活），等待健康
make dev-db
for _ in $(seq 1 40); do
  status="$(docker inspect --format '{{.State.Health.Status}}' zija-postgres-1 2>/dev/null || true)"
  [ "$status" = "healthy" ] && break
  sleep 2
done
echo "start: postgres status=${status:-unknown}"

# 3. 后端（Spring Boot，:8080，持久 tmux 会话）。端口已监听则跳过。
if port_in_use 8080; then
  echo "start: backend already listening on :8080"
else
  tmux_run backend 'make dev-backend 2>&1 | tee /tmp/dev-backend.log'
  echo "start: backend launching in tmux (socket zija, session backend)"
fi

# 4. 前端（Vite，:5173，代理 /api → :8080，持久 tmux 会话）。端口已监听则跳过。
if port_in_use 5173; then
  echo "start: frontend already listening on :5173"
else
  tmux_run frontend 'make dev-frontend 2>&1 | tee /tmp/dev-frontend.log'
  echo "start: frontend launching in tmux (socket zija, session frontend)"
fi

# 5. 等待后端就绪（Flyway 迁移 + 上下文启动需要一些时间）
for _ in $(seq 1 60); do
  curl -fsS http://localhost:8080/actuator/health/readiness >/dev/null 2>&1 && break
  sleep 3
done
echo "start: backend readiness=$(curl -fsS http://localhost:8080/actuator/health/readiness 2>/dev/null || echo unreachable)"
echo "start: done (inspect services with: ${TMUX_BIN} -L zija ls)"
