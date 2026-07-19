#!/usr/bin/env bash
set -euo pipefail

required_files=(
  ".dockerignore"
  ".editorconfig"
  ".env.example"
  "Makefile"
  "README.md"
  "backend/pom.xml"
  "backend/mvnw"
  "backend/src/main/java/com/zija/ZijaApplication.java"
  "backend/src/main/resources/application.yml"
  "backend/src/main/resources/db/migration/V1__create_system_installation.sql"
  "frontend/package.json"
  "frontend/src/main.ts"
  "frontend/src/components/AppShell.vue"
  "frontend/src/views/SystemStatusView.vue"
  "compose.yaml"
  "deploy/app/Dockerfile"
  "deploy/web/Dockerfile"
  "deploy/nginx/default.conf"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "missing required file: $file" >&2
    exit 1
  fi
done

echo "repository layout verified"
