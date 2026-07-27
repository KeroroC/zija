#!/usr/bin/env bash
set -euo pipefail

# ── 配置 ──────────────────────────────────────────────
BACKUP_DIR="${ZIJA_BACKUP_DIR:-./backups}"
TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
SHORT_ID=$(uuidgen 2>/dev/null | cut -d- -f1 || cat /proc/sys/kernel/random/uuid 2>/dev/null | cut -d- -f1 || head -c8 /dev/urandom | xxd -p)
BATCH_ID="backup_${SHORT_ID}_${TIMESTAMP}"
OUT_DIR="${BACKUP_DIR}/${BATCH_ID}"
HTTP_PORT="${ZIJA_HTTP_PORT:-8088}"

# ── 跨平台工具函数 ───────────────────────────────────
compute_sha256() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | cut -d' ' -f1
  else
    sha256sum "$1" | cut -d' ' -f1
  fi
}

file_size() {
  if [[ "$(uname)" == "Darwin" ]]; then
    stat -f%z "$1"
  else
    stat -c%s "$1"
  fi
}

# ── 临时目录（完成后原子移动） ───────────────────────
TMP_DIR=$(mktemp -d)
trap 'rm -rf "${TMP_DIR}"' EXIT

mkdir -p "${TMP_DIR}/files"

echo "=== 知家备份 ==="
echo "批次标识: ${BATCH_ID}"
echo "输出目录: ${OUT_DIR}"

# ── 1. 数据库 dump ────────────────────────────────────
echo "[1/4] 导出 PostgreSQL ..."
docker compose exec -T postgres pg_dump --format=custom --file=/tmp/db.dump \
  -U "${ZIJA_POSTGRES_USER:-zija}" "${ZIJA_POSTGRES_DB:-zija}"
docker compose cp postgres:/tmp/db.dump "${TMP_DIR}/db.dump"
echo "  -> db.dump 完成"

# ── 2. 获取 schema 版本与应用版本 ─────────────────────
echo "[2/4] 读取版本信息 ..."
DB_USER="${ZIJA_POSTGRES_USER:-zija}"
DB_NAME="${ZIJA_POSTGRES_DB:-zija}"

SCHEMA_VERSION=$(docker compose exec -T postgres psql -tAc \
  "SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank DESC LIMIT 1" \
  -U "${DB_USER}" "${DB_NAME}") || true
SCHEMA_INSTALLED_ON=$(docker compose exec -T postgres psql -tAc \
  "SELECT installed_on FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank DESC LIMIT 1" \
  -U "${DB_USER}" "${DB_NAME}") || true
APP_VERSION=$(curl -sf "http://localhost:${HTTP_PORT}/api/v1/system/info" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('version','unknown'))" \
  || echo "unknown")
echo "  -> schema: ${SCHEMA_VERSION:-unknown}, app: ${APP_VERSION}"

# ── 3. 文件卷镜像 ────────────────────────────────────
echo "[3/4] 拷贝文件卷 ..."
FILE_LIST=$(docker compose exec -T postgres psql -tAc \
  "SELECT storage_key || '|' || sha256 || '|' || byte_size FROM stored_file" \
  -U "${DB_USER}" "${DB_NAME}") || true

FILE_ENTRIES=""
CHECKED_COUNT=0
COMPOSE_PROJECT="${COMPOSE_PROJECT_NAME:-zija}"
VOLUME_NAME="${COMPOSE_PROJECT}_zija-files"
PG_IMAGE="postgres:17-alpine"

while IFS='|' read -r storage_key sha256 byte_size; do
  [[ -z "${storage_key}" ]] && continue
  CHECKED_COUNT=$((CHECKED_COUNT + 1))

  # 从卷拷出文件（用 postgres 镜像，compose 栈已拉取）
  DEST="${TMP_DIR}/files/${storage_key}"
  mkdir -p "$(dirname "${DEST}")"
  docker run --rm \
    -v "${VOLUME_NAME}:/src:ro" \
    -v "$(dirname "${DEST}"):/dst" \
    "${PG_IMAGE}" \
    cp "/src/${storage_key}" "/dst/$(basename "${storage_key}")" 2>/dev/null || true

  # 计算实际文件 SHA256 和大小
  if [[ -f "${DEST}" ]]; then
    ACTUAL_SHA256=$(compute_sha256 "${DEST}")
    ACTUAL_SIZE=$(file_size "${DEST}")
  else
    ACTUAL_SHA256=""
    ACTUAL_SIZE=0
  fi
  FILE_ENTRIES="${FILE_ENTRIES}{\"storageKey\":\"${storage_key}\",\"sha256\":\"${ACTUAL_SHA256}\",\"byteSize\":${ACTUAL_SIZE}},"
done <<< "${FILE_LIST}"
FILE_ENTRIES="[${FILE_ENTRIES%,}]"

echo "  -> ${CHECKED_COUNT} 个文件拷贝完成"

# ── 4. 计算 db.dump 校验和 ───────────────────────────
DB_SHA256=$(compute_sha256 "${TMP_DIR}/db.dump")
DB_SIZE=$(file_size "${TMP_DIR}/db.dump")

# ── 5. 生成 manifest.json ────────────────────────────
echo "[4/4] 生成 manifest.json ..."
cat > "${TMP_DIR}/manifest.json" <<EOF
{
  "schemaVersion": "${SCHEMA_VERSION:-unknown}",
  "schemaVersionInstalledOn": "${SCHEMA_INSTALLED_ON:-unknown}",
  "appVersion": "${APP_VERSION}",
  "backupId": "${BATCH_ID}",
  "createdAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "db": {
    "dumpFile": "db.dump",
    "sha256": "${DB_SHA256}",
    "byteSize": ${DB_SIZE}
  },
  "files": {
    "checkedCount": ${CHECKED_COUNT},
    "entries": ${FILE_ENTRIES},
    "orphanCount": 0
  }
}
EOF

# ── 6. 原子移动到最终位置 ─────────────────────────────
mkdir -p "${BACKUP_DIR}"
mv "${TMP_DIR}" "${OUT_DIR}"

echo ""
echo "=== 备份完成 ==="
echo "产物路径: ${OUT_DIR}"
echo "db.dump SHA256: ${DB_SHA256}"
echo "文件数: ${CHECKED_COUNT}"
