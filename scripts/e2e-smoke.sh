#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root_dir"

export ZIJA_HTTP_PORT=18089
export ZIJA_POSTGRES_PORT=15433
export ZIJA_COMPOSE_PROJECT="${ZIJA_COMPOSE_PROJECT:-zija-e2e-$$}"
export ZIJA_RECOVERY_VIA_DOCKER=1

cleanup() {
  docker compose -p "$ZIJA_COMPOSE_PROJECT" --env-file .env.example down -v
}
trap cleanup EXIT

docker compose -p "$ZIJA_COMPOSE_PROJECT" --env-file .env.example up --build -d

for attempt in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:18089/api/v1/system/info" >/dev/null; then
    ZIJA_WEB_URL=http://127.0.0.1:18089 \
      ZIJA_COMPOSE_PROJECT="$ZIJA_COMPOSE_PROJECT" \
      ZIJA_RECOVERY_VIA_DOCKER=1 \
      npm --prefix frontend run test:e2e
    echo "e2e smoke passed"
    exit 0
  fi
  sleep 2
done

docker compose -p "$ZIJA_COMPOSE_PROJECT" --env-file .env.example ps
docker compose -p "$ZIJA_COMPOSE_PROJECT" --env-file .env.example logs
echo "e2e smoke failed" >&2
exit 1
