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

for attempt in $(seq 1 30); do
  if response="$(curl -fsS "http://127.0.0.1:18088/api/v1/system/info")"; then
    printf '%s' "$response" | grep -q '"status":"UP"'
    printf '%s' "$response" | grep -q '"application":"zija"'
    echo "compose smoke passed"
    exit 0
  fi
  sleep 2
done

docker compose -p zija-smoke --env-file .env.example ps
docker compose -p zija-smoke --env-file .env.example logs
echo "compose smoke failed" >&2
exit 1
