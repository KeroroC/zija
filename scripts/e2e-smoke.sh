#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root_dir"

export ZIJA_HTTP_PORT=18089
export ZIJA_POSTGRES_PORT=15433

cleanup() {
  docker compose -p zija-e2e --env-file .env.example down -v
}
trap cleanup EXIT

docker compose -p zija-e2e --env-file .env.example up --build -d

for attempt in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:18089/api/v1/system/info" >/dev/null; then
    ZIJA_WEB_URL=http://127.0.0.1:18089 npm --prefix frontend run test:e2e
    echo "e2e smoke passed"
    exit 0
  fi
  sleep 2
done

docker compose -p zija-e2e --env-file .env.example ps
docker compose -p zija-e2e --env-file .env.example logs
echo "e2e smoke failed" >&2
exit 1
