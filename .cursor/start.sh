#!/usr/bin/env bash
# 知家 Cloud Agent 环境 —— 每次启动的运行时初始化（start 阶段）
#
# 启动 Docker 守护进程并拉起开发数据库（PostgreSQL）。必须可重复执行、
# 避免重复进程，并在数据库就绪后返回；随后 terminals 才启动前后端。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# 1. 启动 Docker 守护进程（若尚未运行）。云 VM 为嵌套容器，使用 fuse-overlayfs
#    存储驱动（由 /etc/docker/daemon.json 指定）。
if ! docker info >/dev/null 2>&1; then
  sudo mkdir -p /etc/docker
  if [ ! -f /etc/docker/daemon.json ]; then
    echo '{"storage-driver":"fuse-overlayfs"}' | sudo tee /etc/docker/daemon.json >/dev/null
  fi
  sudo bash -c 'nohup dockerd >/var/log/dockerd.log 2>&1 &'
  for _ in $(seq 1 30); do
    sudo docker info >/dev/null 2>&1 && break
    sleep 1
  done
  # 让 ubuntu 用户免 sudo 访问 docker（开发环境务实取舍）
  sudo chmod 666 /var/run/docker.sock || true
fi

# 2. 启动开发数据库并等待健康
make dev-db
for _ in $(seq 1 40); do
  status="$(docker inspect --format '{{.State.Health.Status}}' zija-postgres-1 2>/dev/null || true)"
  [ "$status" = "healthy" ] && break
  sleep 2
done
echo "start: postgres status=${status:-unknown}"
