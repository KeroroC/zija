#!/usr/bin/env bash
# 诊断工具：校验 "Secure Cookie 由传输层决定" 这一契约（应用侧）。
#
# 背景：应用的自定义 CookieSerializer 不读取 server.servlet.session.cookie.secure，
# Secure 标志跟随 request.isSecure()（X-Forwarded-Proto 透传）。本脚本对
# /api/v1/auth/csrf（免认证）断言两个不变量：
#   1) 纯 HTTP 下 Set-Cookie 不带 Secure —— 否则浏览器拒收 Cookie，登录不可用；
#   2) X-Forwarded-Proto: https 下 Set-Cookie 带 Secure —— TLS 终止部署的安全承诺。
# 会话 Cookie（ZIJA_SESSION）走同一机制，compose-smoke.sh 在端到端路径上覆盖。
#
# 用法：
#   scripts/check-secure-cookie.sh start    # 启动本地 prod 实例（需 dev DB 在 5432）后检查
#   scripts/check-secure-cookie.sh          # 对 BASE_URL（默认 http://127.0.0.1:18080）上已运行的实例检查
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
APP_PID=""

cleanup() {
  if [ -n "$APP_PID" ]; then kill "$APP_PID" 2>/dev/null || true; fi
  rm -f /tmp/zj-loop-cookies.txt /tmp/zj-loop-cookies-tls.txt
}
trap cleanup EXIT

start_app() {
  mkdir -p /tmp/zija-files-loop
  cd backend
  ZIJA_PROFILES_ACTIVE=prod SERVER_PORT=18080 \
    ZIJA_DB_URL=jdbc:postgresql://localhost:5432/zija \
    ZIJA_DB_USERNAME=zija ZIJA_DB_PASSWORD=change-this-password \
    ZIJA_FILE_STORAGE_PATH=/tmp/zija-files-loop \
    java -jar target/zija-backend-0.1.0-SNAPSHOT.jar > /tmp/zija-prod-loop.log 2>&1 &
  APP_PID=$!
  for _ in $(seq 1 40); do
    if curl -fsS "$BASE_URL/actuator/health/readiness" 2>/dev/null | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 2
  done
  echo "FAIL: app did not become ready" >&2
  tail -30 /tmp/zija-prod-loop.log >&2
  exit 1
}

if [ "${1:-}" = "start" ]; then
  start_app
fi

echo "== A) 纯 HTTP =="
HEADERS_A="$(curl -si "$BASE_URL/api/v1/auth/csrf" 2>/dev/null || true)"
echo "$HEADERS_A" | grep -i -E "^HTTP|^set-cookie" || true
if echo "$HEADERS_A" | grep -i "^set-cookie" | grep -qi "secure"; then
  echo "RED: 纯 HTTP 下出现 Secure Cookie（浏览器拒收 → 无法登录）"
  exit 1
fi
echo "OK: 纯 HTTP 下无 Secure 标志"

echo
echo "== B) X-Forwarded-Proto: https（模拟上游 TLS 终止）=="
HEADERS_B="$(curl -si -H "X-Forwarded-Proto: https" "$BASE_URL/api/v1/auth/csrf" 2>/dev/null || true)"
echo "$HEADERS_B" | grep -i -E "^HTTP|^set-cookie" || true
if ! echo "$HEADERS_B" | grep -i "^set-cookie" | grep -qi "secure"; then
  echo "RED: TLS 场景下 Set-Cookie 缺 Secure 标志（安全承诺落空；检查反代是否透传 X-Forwarded-Proto）"
  exit 1
fi
echo "OK: TLS 场景下 Cookie 带 Secure 标志"

echo
echo "check passed"
