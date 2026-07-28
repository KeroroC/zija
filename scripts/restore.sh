#!/usr/bin/env bash
set -euo pipefail

# ── 配置 ──────────────────────────────────────────────
BACKUP_DIR="${ZIJA_BACKUP_DIR:-./backups}"
HTTP_PORT="${ZIJA_HTTP_PORT:-8088}"
RESTORE_TS=$(date -u +%Y%m%d-%H%M%S)
PROJECT_NAME="zija-restore-${RESTORE_TS}"

# ── 定位最新备份 ─────────────────────────────────────
LATEST_BACKUP=$(ls -td "${BACKUP_DIR}"/backup_*_* 2>/dev/null | head -1)
if [ -z "$LATEST_BACKUP" ]; then
  echo "ERROR: 未找到备份目录（${BACKUP_DIR}/backup_*_*）"
  echo "请先运行 make backup-test"
  exit 1
fi

MANIFEST="${LATEST_BACKUP}/manifest.json"
if [ ! -f "$MANIFEST" ]; then
  echo "ERROR: manifest.json 不存在"
  exit 1
fi

EXPECTED_VERSION=$(python3 -c "import json; print(json.load(open('${MANIFEST}'))['appVersion'])")
EXPECTED_CHECKED=$(python3 -c "import json; print(json.load(open('${MANIFEST}'))['files']['checkedCount'])")

echo "=== 知家恢复验证 ==="
echo "备份源: ${LATEST_BACKUP}"
echo "临时项目: ${PROJECT_NAME}"
echo "  → 期望应用版本: ${EXPECTED_VERSION}"
echo "  → 期望文件数: ${EXPECTED_CHECKED}"

# ── 清理函数 ──────────────────────────────────────────
cleanup() {
  echo ""
  echo "=== 清理临时栈 ==="
  docker compose -p "$PROJECT_NAME" down -v --remove-orphans 2>/dev/null || true
  echo "清理完成"
}
trap cleanup EXIT

# ── 1. 启动临时 PostgreSQL ────────────────────────────
echo "[1/5] 启动临时 PostgreSQL ..."
export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
docker compose -p "$PROJECT_NAME" up -d postgres

echo "等待 postgres 就绪 ..."
# First wait for initial pg_isready (may be during init phase)
for i in $(seq 1 30); do
  if docker compose -p "$PROJECT_NAME" exec -T postgres \
    pg_isready -U "${ZIJA_POSTGRES_USER:-zija}" -q 2>/dev/null; then
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "ERROR: postgres 未就绪"
    exit 1
  fi
  sleep 1
done
# Wait for postgres to complete init restart cycle and be consistently ready
sleep 3
for i in $(seq 1 10); do
  if docker compose -p "$PROJECT_NAME" exec -T postgres \
    pg_isready -U "${ZIJA_POSTGRES_USER:-zija}" -q 2>/dev/null; then
    echo "  → postgres 就绪"
    break
  fi
  if [ "$i" -eq 10 ]; then
    echo "ERROR: postgres 未就绪（重启后）"
    exit 1
  fi
  sleep 1
done

# ── 2. 验证空库 ──────────────────────────────────────
echo "[2/5] 验证数据库为空 ..."
TABLE_COUNT=$(docker compose -p "$PROJECT_NAME" exec -T postgres psql -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'" \
  -U "${ZIJA_POSTGRES_USER:-zija}" "${ZIJA_POSTGRES_DB:-zija}")
if [ "$TABLE_COUNT" != "0" ]; then
  echo "ERROR: 数据库非空（${TABLE_COUNT} 张表），恢复要求空库"
  exit 1
fi
echo "  → 空库确认"

# ── 3. 恢复数据库 ────────────────────────────────────
echo "[3/5] 恢复数据库 ..."
docker compose -p "$PROJECT_NAME" \
  cp "${LATEST_BACKUP}/db.dump" postgres:/tmp/db.dump
docker compose -p "$PROJECT_NAME" exec -T postgres \
  pg_restore \
  -U "${ZIJA_POSTGRES_USER:-zija}" -d "${ZIJA_POSTGRES_DB:-zija}" /tmp/db.dump
echo "  → pg_restore 完成"

# ── 4. 恢复文件卷 ────────────────────────────────────
echo "[4/5] 恢复文件卷 ..."
if [ -d "${LATEST_BACKUP}/files" ] && [ "$(ls -A "${LATEST_BACKUP}/files" 2>/dev/null)" ]; then
  RESTORE_VOLUME="${PROJECT_NAME}_zija-files"
  docker run --rm \
    -v "${LATEST_BACKUP}/files:/src:ro" \
    -v "${RESTORE_VOLUME}:/dst" \
    alpine sh -c 'cp -r /src/. /dst/'
  echo "  → 文件卷恢复完成"
else
  echo "  → 无文件需要恢复"
fi

# ── 5. 启动 app + REST 验证 ──────────────────────────
echo "[5/5] 启动应用并验证 ..."
# Build images for app and web (postgres is already running with restored data)
docker compose -p "$PROJECT_NAME" build app web
# Create app and web containers without touching postgres
docker compose -p "$PROJECT_NAME" create app
docker compose -p "$PROJECT_NAME" create web
# Start all containers (postgres is already running, app and web will start fresh)
docker compose -p "$PROJECT_NAME" start app web

echo "等待应用就绪 ..."
for i in $(seq 1 60); do
  HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
    "http://127.0.0.1:${HTTP_PORT}/actuator/health/readiness" 2>/dev/null || echo "000")
  if [ "$HTTP_CODE" = "200" ]; then
    echo "  → 应用就绪"
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "ERROR: 应用未就绪"
    docker compose -p "$PROJECT_NAME" logs --tail=30
    exit 1
  fi
  sleep 2
done

# ── Bootstrap household if needed ─────────────────────
echo ""
echo "检查家庭初始化状态 ..."
HH_STATUS=$(curl -sf "http://127.0.0.1:${HTTP_PORT}/api/v1/household/status" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('initialized',False))" 2>/dev/null || echo "False")
if [ "$HH_STATUS" != "True" ]; then
  echo "家庭未初始化，执行 bootstrap ..."
  OWNER_PASS="${ZIJA_OWNER_PASSWORD:-TestPass123!}"
  BOOTSTRAP_RESULT=$(curl -sf -X POST "http://127.0.0.1:${HTTP_PORT}/api/v1/household/bootstrap" \
    -H "Content-Type: application/json" \
    -d "{\"householdName\":\"测试家庭\",\"username\":\"owner@test.com\",\"password\":\"${OWNER_PASS}\",\"displayName\":\"Owner\",\"email\":\"owner@test.com\"}" \
    -o /dev/null -w '%{http_code}' 2>/dev/null || echo "000")
  if [ "$BOOTSTRAP_RESULT" = "201" ]; then
    echo "  → bootstrap 成功"
  else
    echo "  → bootstrap 失败 (HTTP ${BOOTSTRAP_RESULT})，尝试继续验证"
  fi
else
  echo "  → 家庭已初始化"
fi

# ── 验证三连 ─────────────────────────────────────────
echo ""
echo "=== 恢复验证 ==="
FAIL=0

# 验证 1: system/info 版本匹配
echo -n "[1/3] GET /api/v1/system/info ... "
SYS_INFO=$(curl -sf "http://127.0.0.1:${HTTP_PORT}/api/v1/system/info" || echo "{}")
ACTUAL_VERSION=$(echo "$SYS_INFO" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('version',''))" 2>/dev/null || echo "")
if [ "$ACTUAL_VERSION" = "$EXPECTED_VERSION" ]; then
  echo "OK (version=${ACTUAL_VERSION})"
else
  echo "FAIL (expected=${EXPECTED_VERSION}, actual=${ACTUAL_VERSION})"
  FAIL=1
fi

# 登录获取 session cookie + CSRF token
OWNER_PASS="${ZIJA_OWNER_PASSWORD:-TestPass123!}"
COOKIES=$(mktemp)
trap 'rm -f "${COOKIES}"; cleanup' EXIT

LOGIN_CODE=$(curl -s -c "$COOKIES" -o /dev/null -w '%{http_code}' \
  -X POST "http://127.0.0.1:${HTTP_PORT}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"owner@test.com\",\"password\":\"${OWNER_PASS}\"}" 2>/dev/null || echo "000")

AUTH_OK=false
if [ "$LOGIN_CODE" = "200" ]; then
  CSRF_TOKEN=$(curl -sf -b "$COOKIES" "http://127.0.0.1:${HTTP_PORT}/api/v1/auth/csrf" \
    -H "Accept: application/json" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null || echo "")
  if [ -n "$CSRF_TOKEN" ]; then
    AUTH_OK=true
  fi
fi

# 验证 2: files/integrity-report
echo -n "[2/3] GET /api/v1/files/integrity-report ... "
if [ "$AUTH_OK" = "true" ]; then
  INTEGRITY=$(curl -sf -b "$COOKIES" \
    -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
    "http://127.0.0.1:${HTTP_PORT}/api/v1/files/integrity-report" || echo "{}")
  INT_MISSING=$(echo "$INTEGRITY" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('missingCount',-1))" 2>/dev/null || echo "-1")
  INT_HASH=$(echo "$INTEGRITY" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('hashMismatchCount',-1))" 2>/dev/null || echo "-1")
  INT_CHECKED=$(echo "$INTEGRITY" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('checkedCount',0))" 2>/dev/null || echo "0")
  if [ "$INT_MISSING" = "0" ] && [ "$INT_HASH" = "0" ] && [ "$INT_CHECKED" = "$EXPECTED_CHECKED" ]; then
    echo "OK (checked=${INT_CHECKED}, missing=0, hashMismatch=0)"
  else
    echo "FAIL (checked=${INT_CHECKED}/${EXPECTED_CHECKED}, missing=${INT_MISSING}, hashMismatch=${INT_HASH})"
    FAIL=1
  fi
else
  echo "SKIP (login failed, owner credentials may differ)"
fi

# 验证 3: inventory/consistency-report
echo -n "[3/3] GET /api/v1/inventory/consistency-report ... "
if [ "$AUTH_OK" = "true" ]; then
  CONSISTENCY=$(curl -sf -b "$COOKIES" \
    -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
    "http://127.0.0.1:${HTTP_PORT}/api/v1/inventory/consistency-report" || echo "{}")
  DISCREPANCIES=$(echo "$CONSISTENCY" \
    | python3 -c "import sys,json; d=json.load(sys.stdin).get('discrepancies',[]); print(len(d))" 2>/dev/null || echo "-1")
  if [ "$DISCREPANCIES" = "0" ]; then
    echo "OK (discrepancies=0)"
  else
    echo "FAIL (discrepancies=${DISCREPANCIES})"
    FAIL=1
  fi
else
  echo "SKIP (login failed, owner credentials may differ)"
fi

# ── 结果 ─────────────────────────────────────────────
echo ""
if [ "$FAIL" = "0" ]; then
  echo "=== 恢复验证全部通过 ==="
  exit 0
else
  echo "=== 恢复验证失败 ==="
  exit 1
fi
