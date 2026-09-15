#!/bin/sh
# Local Docker: MySQL 8.4.11 + HyperLPR3 0.1.3 + Frigate 0.17.2
# Legacy --with-lpr / --with-frigate are ignored (always started).
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

for arg in "$@"; do
  case "$arg" in
    --with-lpr|--with-frigate) ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

command -v docker >/dev/null || { echo "Missing command: docker" >&2; exit 1; }

echo "Starting Docker services (MySQL 8.4.11, HyperLPR3 0.1.3, Frigate 0.17.2)..."
docker compose -f ai-build/docker-compose.yml up -d --build

wait_healthy() {
  name="$1"
  seconds="${2:-120}"
  i=0
  while [ "$i" -lt "$seconds" ]; do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || echo missing)"
    if [ "$status" = "healthy" ]; then return 0; fi
    if [ "$name" = "frigate" ] && [ "$status" = "running" ]; then return 0; fi
    i=$((i + 3))
    sleep 3
  done
  docker logs --tail 40 "$name" || true
  echo "$name not healthy within ${seconds}s (last status: $status)" >&2
  exit 1
}

wait_healthy freepark-local-mysql 90
wait_healthy freepark-hyperlpr3 180
wait_healthy frigate 180

echo "OK  MySQL :3307  (8.4.11)"
echo "OK  HyperLPR3 :8715  (0.1.3)"
echo "OK  Frigate :5000  go2rtc :1984  (0.17.2)"
echo "Next: sh ai-build/linux/install.sh"
echo "Then start local_server (8081) and local_frontend (5173)."
