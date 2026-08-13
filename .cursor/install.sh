#!/usr/bin/env bash
# 知家 Cloud Agent 环境 —— 仓库引导（install 阶段）
#
# 幂等地准备与源码相关的依赖与本地配置。系统工具链（JDK 25 / Node 24 /
# Docker）由基础镜像（快照）提供，这里只做仓库级引导，可反复执行。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# 1. 本地开发用 .env（被 .gitignore 忽略，缺失时从示例生成并改用开发默认值）
if [ ! -f .env ]; then
  cp .env.example .env
  sed -i 's/^ZIJA_POSTGRES_PASSWORD=.*/ZIJA_POSTGRES_PASSWORD=zija-dev-password/' .env
  sed -i 's/^ZIJA_DB_PASSWORD=.*/ZIJA_DB_PASSWORD=zija-dev-password/' .env
  # 非 root 本地开发：文件存储指向工作区内可写目录，而非容器路径 /var/lib/zija
  sed -i 's#^ZIJA_FILE_STORAGE_PATH=.*#ZIJA_FILE_STORAGE_PATH='"$ROOT"'/data/files#' .env
  # 未配置 SMTP 时关闭 mail 健康检查，避免整体 health 聚合为 DOWN
  grep -q '^MANAGEMENT_HEALTH_MAIL_ENABLED=' .env || echo 'MANAGEMENT_HEALTH_MAIL_ENABLED=false' >> .env
fi
mkdir -p "$ROOT/data/files"

# 2. 前端依赖（锁文件安装）
npm --prefix frontend ci

# 3. 后端依赖 + 构建，预热 ~/.m2（使用仓库自带 Maven Wrapper）
chmod +x backend/mvnw
( cd backend && ./mvnw -q -DskipTests package )

# 4. 预拉取开发数据库镜像，写入磁盘以便快照后离线快速启动（Docker 可用时）
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  docker pull postgres:17-alpine || true
fi

echo "install: done"
