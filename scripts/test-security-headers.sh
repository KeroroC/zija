#!/usr/bin/env bash
set -euo pipefail

# Test security headers in Nginx configuration
# This script verifies that all required security headers are present

CONF_FILE="deploy/nginx/default.conf"

echo "Testing security headers in $CONF_FILE..."

# Check if file exists
if [ ! -f "$CONF_FILE" ]; then
    echo "ERROR: Configuration file not found: $CONF_FILE"
    exit 1
fi

# Required security headers
REQUIRED_HEADERS=(
    "Referrer-Policy"
    "X-Content-Type-Options"
    "X-Frame-Options"
    "Content-Security-Policy"
)

# Check each header
MISSING_HEADERS=()
for header in "${REQUIRED_HEADERS[@]}"; do
    if ! grep -q "add_header $header" "$CONF_FILE"; then
        MISSING_HEADERS+=("$header")
    fi
done

# Report results
if [ ${#MISSING_HEADERS[@]} -eq 0 ]; then
    echo "✓ All required security headers are present"
    echo ""
    echo "Headers found:"
    grep "add_header" "$CONF_FILE" | sed 's/^[[:space:]]*/  /'
    exit 0
else
    echo "✗ Missing security headers:"
    for header in "${MISSING_HEADERS[@]}"; do
        echo "  - $header"
    done
    exit 1
fi