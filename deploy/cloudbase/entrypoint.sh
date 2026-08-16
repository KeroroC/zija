#!/usr/bin/env bash
# CloudBase CloudRun entrypoint:
#   1. Initialize + start an in-container PostgreSQL + pgvector (data on /var/lib/zija/postgresql/data)
#   2. Start the Spring Boot backend (jar) on 127.0.0.1:8081
#   3. Start nginx on 8080 (CloudRun PORT) serving the SPA + reverse-proxying /api
set -euo pipefail

PGDATA=/var/lib/zija/postgresql/data
PG_BIN=$(ls -d /usr/lib/postgresql/*/bin | head -1)
ZIJA_DB=${ZIJA_DB_NAME:-zija}

mkdir -p "$PGDATA"
chown -R postgres:postgres "$(dirname "$PGDATA")"

if [ ! -f "$PGDATA/PG_VERSION" ]; then
  su postgres -c "$PG_BIN/initdb -D $PGDATA -U postgres -A trust"
fi

su postgres -c "$PG_BIN/pg_ctl -D $PGDATA -o '-c listen_addresses=127.0.0.1 -p 5432' -l /var/lib/zija/postgresql/pg.log start"

# Wait for PostgreSQL to accept connections
for i in $(seq 1 60); do
  if su postgres -c "psql -h 127.0.0.1 -U postgres -p 5432 -c 'SELECT 1'" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

# Create the application database if it does not exist yet
su postgres -c "psql -h 127.0.0.1 -U postgres -p 5432 -tc \"SELECT 1 FROM pg_database WHERE datname='$ZIJA_DB'\" | grep -q 1 || createdb -h 127.0.0.1 -U postgres -p 5432 $ZIJA_DB"

# Start backend on 8081; nginx owns 8080 (the port CloudRun health-checks).
# The port MUST be passed explicitly: the app defaults to 8080 and would other-
# wise lose the race against nginx with "Port 8080 was already in use".
BACKEND_PORT=${BACKEND_PORT:-8081}
java -jar /app/zija.jar --server.port="$BACKEND_PORT" &
JAVA_PID=$!

nginx -g 'daemon off;' &
NGINX_PID=$!

# Fail the container if either process dies, instead of serving silent 502s.
# `|| EXIT_CODE=$?` is required: under `set -e` a non-zero `wait -n` would abort
# the script before the exit status could be captured.
EXIT_CODE=0
wait -n "$JAVA_PID" "$NGINX_PID" || EXIT_CODE=$?
echo "entrypoint: a managed process exited (code=$EXIT_CODE); shutting down container" >&2
kill "$JAVA_PID" "$NGINX_PID" 2>/dev/null || true
exit "${EXIT_CODE:-1}"
