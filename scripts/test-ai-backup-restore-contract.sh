#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TEST_DIR=$(mktemp -d)
FAKE_BIN="${TEST_DIR}/bin"
CALL_LOG="${TEST_DIR}/calls.log"
BACKUP_DIR="${TEST_DIR}/backups"
mkdir -p "$FAKE_BIN" "$BACKUP_DIR"
trap 'rm -rf "${TEST_DIR}"' EXIT

cat > "${FAKE_BIN}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "docker $*" >> "${ZIJA_TEST_CALL_LOG}"

case "$*" in
  *"pg_dump"*)
    exit 0
    ;;
  *"compose cp postgres:/tmp/db.dump"*)
    destination="${@: -1}"
    printf 'fake custom archive' > "$destination"
    ;;
  *"SELECT version FROM flyway_schema_history"*)
    printf '12\n'
    ;;
  *"SELECT installed_on FROM flyway_schema_history"*)
    printf '2026-08-19 00:00:00+00\n'
    ;;
  *"SELECT count(*) FROM ai_knowledge_chunk"*)
    printf '0\n'
    ;;
  *"WITH requeued AS"*)
    printf '1\n'
    ;;
  *"SELECT count(*) FROM ai_knowledge_source WHERE status = 'DISABLED'"*)
    printf '1\n'
    ;;
  *"SELECT count(*) FROM ai_knowledge_source"*)
    printf '2\n'
    ;;
  *"SELECT storage_key"*)
    printf 'knowledge/manual.txt||14\n'
    ;;
  *"cp /src/knowledge/manual.txt /dst/manual.txt"*)
    for argument in "$@"; do
      case "$argument" in
        *:/dst)
          destination="${argument%:/dst}"
          printf 'family manual\n' > "${destination}/manual.txt"
          ;;
      esac
    done
    ;;
  *"pg_isready"*)
    exit 0
    ;;
  *"information_schema.tables"*)
    printf '0\n'
    ;;
esac
EOF

cat > "${FAKE_BIN}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "curl $*" >> "${ZIJA_TEST_CALL_LOG}"

case "$*" in
  *"/actuator/health/readiness"*) printf '200' ;;
  *"/api/v1/system/info"*) printf '{"version":"test-version"}' ;;
  *"/api/v1/household/status"*) printf '{"initialized":true}' ;;
  *"/api/v1/auth/login"*) printf '200' ;;
  *"/api/v1/auth/csrf"*) printf '{"token":"test-csrf"}' ;;
  *"/api/v1/files/integrity-report"*)
    printf '{"checkedCount":1,"missingCount":0,"hashMismatchCount":0}'
    ;;
  *"/api/v1/inventory/consistency-report"*) printf '{"discrepancies":[]}' ;;
  *"/api/v1/ai/knowledge-sources"*)
    printf '{"items":[{"status":"PROCESSING"},{"status":"DISABLED"}]}'
    ;;
  *) printf '{}' ;;
esac
EOF

cat > "${FAKE_BIN}/sleep" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

chmod +x "${FAKE_BIN}/docker" "${FAKE_BIN}/curl" "${FAKE_BIN}/sleep"

export PATH="${FAKE_BIN}:${PATH}"
export ZIJA_TEST_CALL_LOG="$CALL_LOG"
export ZIJA_BACKUP_DIR="$BACKUP_DIR"
export ZIJA_VERSION="test-version"
export ZIJA_AI_EMBEDDING_MODEL="qwen3-embedding:0.6b"

cd "$ROOT_DIR"
bash scripts/backup.sh >/dev/null

MANIFEST=$(find "$BACKUP_DIR" -name manifest.json -print -quit)
test -n "$MANIFEST"
python3 - "$MANIFEST" <<'PY'
import json
import sys

manifest = json.load(open(sys.argv[1], encoding="utf-8"))
assert manifest["ai"]["sourceData"]["attachments"]["included"] is True
assert manifest["files"]["checkedCount"] == 1
assert manifest["files"]["entries"][0]["storageKey"] == "knowledge/manual.txt"
assert manifest["files"]["entries"][0]["byteSize"] == 14
assert manifest["ai"]["sourceData"]["knowledgeSourceSelections"]["included"] is True
assert manifest["ai"]["sourceData"]["knowledgeSourceSelections"]["statusAndScopeIncluded"] is True
assert manifest["ai"]["sourceData"]["configuration"]["included"] is True
assert manifest["ai"]["sourceData"]["configuration"]["runtimeEnvironmentIncluded"] is False
assert manifest["ai"]["derivedKnowledge"]["databaseTables"] == ["ai_knowledge_chunk"]
assert manifest["ai"]["derivedKnowledge"]["included"] is False
assert manifest["ai"]["derivedKnowledge"]["rebuildRequired"] is True
assert manifest["ai"]["derivedKnowledge"]["processingBaseline"]["embeddingDimensions"] == 1024
PY

grep -q -- '--exclude-table-data=public.ai_knowledge_chunk' "$CALL_LOG"

bash scripts/restore.sh >/dev/null

grep -q -- 'ai_knowledge_chunk' "$CALL_LOG"
grep -q -- 'UPDATE ai_knowledge_source' "$CALL_LOG"
grep -q -- "status = 'PROCESSING'" "$CALL_LOG"
grep -q -- 'failure_code = NULL' "$CALL_LOG"
grep -q -- 'failure_message = NULL' "$CALL_LOG"
grep -q -- 'attempt_count = 0' "$CALL_LOG"
grep -q -- 'next_attempt_at = CURRENT_TIMESTAMP' "$CALL_LOG"
grep -q -- 'disabled_reason = NULL' "$CALL_LOG"
grep -q -- 'processed_at = NULL' "$CALL_LOG"
grep -q -- 'processing_version = processing_version + 1' "$CALL_LOG"
grep -q -- "WHERE status <> 'DISABLED'" "$CALL_LOG"
grep -q -- '/files:/src:ro' "$CALL_LOG"
echo "AI backup/restore contract passed"
