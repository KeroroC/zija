#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root_dir"

export ZIJA_HTTP_PORT=18088
export ZIJA_POSTGRES_PORT=15432

cleanup() {
  docker compose -p zija-smoke --env-file .env.example down -v
}
trap cleanup EXIT

docker compose -p zija-smoke --env-file .env.example up --build -d

BASE_URL="http://127.0.0.1:18088"
SYSTEM_UP=false

for attempt in $(seq 1 30); do
  if response="$(curl -fsS "$BASE_URL/api/v1/system/info")"; then
    printf '%s' "$response" | grep -q '"status":"UP"'
    printf '%s' "$response" | grep -q '"application":"zija"'
    echo "OK: system info UP"
    SYSTEM_UP=true
    break
  fi
  sleep 2
done

if [ "$SYSTEM_UP" != "true" ]; then
  docker compose -p zija-smoke --env-file .env.example ps
  docker compose -p zija-smoke --env-file .env.example logs
  echo "compose smoke failed: system info never responded" >&2
  exit 1
fi

# Liveness 健康端点
echo "Checking /actuator/health/liveness ..."
LIVENESS_STATUS=$(curl -sf "$BASE_URL/actuator/health/liveness" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
if [ "$LIVENESS_STATUS" != "UP" ]; then
  echo "FAIL: liveness status=$LIVENESS_STATUS, expected UP" >&2
  exit 1
fi
echo "OK: liveness UP"

# 安全 Cookie 断言（prod profile 下会话和 CSRF cookie 应带 Secure 标志）
echo "Checking security cookies ..."
CSRF_HEADERS=$(curl -si "$BASE_URL/api/v1/auth/csrf" 2>/dev/null || true)
if echo "${ZIJA_PROFILES_ACTIVE:-}" | grep -q "prod"; then
  if ! echo "$CSRF_HEADERS" | grep -qi "Secure"; then
    echo "FAIL: prod profile but Set-Cookie missing Secure flag" >&2
    exit 1
  fi
  echo "OK: Secure cookie flag present in prod profile"
else
  echo "SKIP: not running in prod profile, cookie Secure check skipped"
fi

echo "compose smoke passed"
exit 0
