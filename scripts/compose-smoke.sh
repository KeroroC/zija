#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root_dir"

export ZIJA_HTTP_PORT=18088
export ZIJA_POSTGRES_PORT=15432

cleanup() {
  rm -f "${BIG_PROBE:-}"
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
LIVENESS_STATUS=$(curl -sf "$BASE_URL/actuator/health/liveness" 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" 2>/dev/null || true)
if [ "$LIVENESS_STATUS" != "UP" ]; then
  echo "FAIL: liveness status=${LIVENESS_STATUS:-<unreachable>}, expected UP" >&2
  exit 1
fi
echo "OK: liveness UP"

# 回归防护：>1MB 上传必须穿过 nginx 到达 app。
# nginx 缺 client_max_body_size 时默认 1m，会在反代层直接 413（text/html、无 X-Request-Id）；
# 未认证探测下穿过 nginx 后 app 返回 401/403 problem+json，即可证明配置在位。
echo "Checking >1MB upload passes nginx ..."
BIG_PROBE=$(mktemp /tmp/zija-upload-probe.XXXXXX)
dd if=/dev/urandom of="$BIG_PROBE" bs=1024 count=1536 2>/dev/null
UPLOAD_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -X POST "$BASE_URL/api/v1/files" -F "file=@$BIG_PROBE")
if [ "$UPLOAD_CODE" = "413" ]; then
  echo "FAIL: nginx rejected >1MB upload with 413 (client_max_body_size missing)" >&2
  exit 1
fi
echo "OK: >1MB upload passes nginx (app answered $UPLOAD_CODE)"

# 安全 Cookie 断言：Secure 标志由传输层决定（app 侧 request.isSecure()，
# 跟随反代 X-Forwarded-Proto），仅在 HTTPS 下出现；纯 HTTP smoke 下断言无意义。
# 仅当 BASE_URL 为 https 时才检查 Secure。
echo "Checking security cookies ..."
CSRF_HEADERS=$(curl -si "$BASE_URL/api/v1/auth/csrf" 2>/dev/null || true)
case "$BASE_URL" in
  https://*)
    if ! echo "$CSRF_HEADERS" | grep -qi "Secure"; then
      echo "FAIL: HTTPS endpoint but Set-Cookie missing Secure flag" >&2
      exit 1
    fi
    echo "OK: Secure cookie flag present over HTTPS"
    ;;
  *)
    echo "SKIP: plain HTTP smoke, Secure flag check skipped (Secure requires HTTPS)"
    ;;
esac

echo "compose smoke passed"
exit 0
