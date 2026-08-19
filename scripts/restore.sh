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

read_manifest() {
  python3 - "$MANIFEST" "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as manifest_file:
    value = json.load(manifest_file)
for key in sys.argv[2].split("."):
    value = value[key]
print(str(value).lower() if isinstance(value, bool) else value)
PY
}

EXPECTED_VERSION=$(read_manifest "appVersion")
EXPECTED_CHECKED=$(read_manifest "files.checkedCount")
DERIVED_INCLUDED=$(read_manifest "ai.derivedKnowledge.included")
if [ "$DERIVED_INCLUDED" != "false" ]; then
  echo "ERROR: manifest 未声明 AI 派生知识数据可重建且不随备份恢复"
  exit 1
fi

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

# 派生知识不随备份恢复；在应用和调度器启动前验证这一边界并重新排队来源。
RESTORED_DERIVED_CHUNKS=$(docker compose -p "$PROJECT_NAME" exec -T postgres psql -tAc \
  "SELECT count(*) FROM ai_knowledge_chunk" \
  -U "${ZIJA_POSTGRES_USER:-zija}" "${ZIJA_POSTGRES_DB:-zija}")
if [ "$RESTORED_DERIVED_CHUNKS" != "0" ]; then
  echo "ERROR: 备份意外包含 ${RESTORED_DERIVED_CHUNKS} 条 AI 派生知识数据"
  exit 1
fi

RESTORED_KNOWLEDGE_SOURCES=$(docker compose -p "$PROJECT_NAME" exec -T postgres psql -tAc \
  "SELECT count(*) FROM ai_knowledge_source" \
  -U "${ZIJA_POSTGRES_USER:-zija}" "${ZIJA_POSTGRES_DB:-zija}")
RESTORED_DISABLED_SOURCES=$(docker compose -p "$PROJECT_NAME" exec -T postgres psql -tAc \
  "SELECT count(*) FROM ai_knowledge_source WHERE status = 'DISABLED'" \
  -U "${ZIJA_POSTGRES_USER:-zija}" "${ZIJA_POSTGRES_DB:-zija}")
EXPECTED_REQUEUED_SOURCES=$((RESTORED_KNOWLEDGE_SOURCES - RESTORED_DISABLED_SOURCES))
RESTORED_REQUEUED_SOURCES=$(docker compose -p "$PROJECT_NAME" exec -T postgres psql -tAc \
  "WITH requeued AS (
     UPDATE ai_knowledge_source
     SET status = 'PROCESSING', failure_code = NULL, failure_message = NULL,
         attempt_count = 0, next_attempt_at = CURRENT_TIMESTAMP, disabled_reason = NULL,
         processed_at = NULL, processing_version = processing_version + 1,
         updated_at = CURRENT_TIMESTAMP
     WHERE status <> 'DISABLED'
     RETURNING 1
   ) SELECT count(*) FROM requeued" \
  -U "${ZIJA_POSTGRES_USER:-zija}" "${ZIJA_POSTGRES_DB:-zija}")
if [ "$RESTORED_REQUEUED_SOURCES" != "$EXPECTED_REQUEUED_SOURCES" ]; then
  echo "ERROR: AI 知识来源重新排队数量异常（${RESTORED_REQUEUED_SOURCES}/${EXPECTED_REQUEUED_SOURCES}）"
  exit 1
fi
echo "  → AI 派生数据为空；${RESTORED_REQUEUED_SOURCES} 个来源已重新排队，${RESTORED_DISABLED_SOURCES} 个保持停用"

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

# ── 恢复后验证 ───────────────────────────────────────
echo ""
echo "=== 恢复验证 ==="
FAIL=0

# 验证 1: system/info 版本匹配
echo -n "[1/4] GET /api/v1/system/info ... "
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

if [ "$AUTH_OK" != "true" ]; then
  echo "ERROR: 所有者登录失败，无法验证文件、库存和 AI 知识恢复"
  exit 1
fi

# 验证 2: files/integrity-report
echo -n "[2/4] GET /api/v1/files/integrity-report ... "
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

# 验证 3: inventory/consistency-report
echo -n "[3/4] GET /api/v1/inventory/consistency-report ... "
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

# 验证 4: 知识来源选择保留，恢复前已自动进入重新准备流程
echo -n "[4/4] GET /api/v1/ai/knowledge-sources ... "
VISIBLE_KNOWLEDGE_SOURCES=$(curl -sf -b "$COOKIES" \
  "http://127.0.0.1:${HTTP_PORT}/api/v1/ai/knowledge-sources" \
  | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('items',[])))" 2>/dev/null || echo "-1")
if [ "$VISIBLE_KNOWLEDGE_SOURCES" = "$RESTORED_KNOWLEDGE_SOURCES" ]; then
  echo "OK (selected=${VISIBLE_KNOWLEDGE_SOURCES}, requeued=${RESTORED_REQUEUED_SOURCES}, disabled=${RESTORED_DISABLED_SOURCES}, restoredChunks=0)"
else
  echo "FAIL (selected=${VISIBLE_KNOWLEDGE_SOURCES}/${RESTORED_KNOWLEDGE_SOURCES})"
  FAIL=1
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
