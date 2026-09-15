#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

WITH_LPR=0
WITH_FRIGATE=0
for arg in "$@"; do
  case "$arg" in
    --with-lpr) WITH_LPR=1 ;;
    --with-frigate) WITH_FRIGATE=1 ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

command -v docker >/dev/null || { echo "Missing command: docker" >&2; exit 1; }

CFG="$ROOT/docker/mosquitto/config"
mkdir -p "$CFG" "$ROOT/docker/mosquitto/data" "$ROOT/docker/mosquitto/log"
if [ ! -f "$CFG/pwfile" ]; then
  echo "Generating docker/mosquitto/config/pwfile (freepark / freepark)"
  docker run --rm -v "$CFG:/mosquitto/config" eclipse-mosquitto:2 \
    mosquitto_passwd -c -b /mosquitto/config/pwfile freepark freepark
fi

PROFILES=""
BUILD=""
if [ "$WITH_LPR" -eq 1 ]; then
  PROFILES="$PROFILES --profile lpr"
  BUILD="--build"
fi
if [ "$WITH_FRIGATE" -eq 1 ]; then PROFILES="$PROFILES --profile frigate"; fi

# shellcheck disable=SC2086
docker compose -f ai-build/docker-compose.yml $PROFILES up -d $BUILD

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
wait_healthy freepark-mosquitto 90
if [ "$WITH_LPR" -eq 1 ]; then wait_healthy freepark-hyperlpr3 180; fi
if [ "$WITH_FRIGATE" -eq 1 ]; then wait_healthy frigate 180; fi

echo "OK  MySQL :3307  MQTT :1883"
if [ "$WITH_LPR" -eq 1 ]; then echo "OK  HyperLPR3 :8715"; fi
if [ "$WITH_FRIGATE" -eq 1 ]; then echo "OK  Frigate :5000  go2rtc :1984"; fi
echo "Next: sh ai-build/linux/install.sh"
