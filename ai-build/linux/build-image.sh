#!/bin/sh
# Build freepark-local:0.0.1 (console + API + drivers)
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

command -v docker >/dev/null || { echo "Missing command: docker" >&2; exit 1; }

echo "Building freepark-local:0.0.1 (frontend + local_server)..."
docker build -f docker/local/Dockerfile -t freepark-local:0.0.1 .

echo "OK  Image freepark-local:0.0.1"
echo "Run stack: docker compose -f docker/local/docker-compose.yml up -d"
echo "Console:   http://127.0.0.1:8081  (admin / admin123)"
