#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

MVNW="$ROOT/local_server/mvnw"
chmod +x "$MVNW" 2>/dev/null || true

echo "Installing driver-api..."
"$MVNW" -f "$ROOT/driver-api/pom.xml" -DskipTests install

echo "Installing zhensi-driver..."
"$MVNW" -f "$ROOT/zhensi-driver/pom.xml" -DskipTests install

command -v npm >/dev/null || { echo "Missing command: npm (need Node.js 22+)" >&2; exit 1; }

echo "npm install (local_frontend)..."
( cd "$ROOT/local_frontend" && npm install )

echo "OK  Next: start backend then frontend (see ai-build/AGENTS.md §3–4)"
