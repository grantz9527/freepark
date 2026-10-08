#!/bin/sh
# Save MySQL + Frigate + HyperLPR3 + freepark-local into one tar (offline load).
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

mkdir -p ai-build/dist
TAR="$ROOT/ai-build/dist/freepark-images.tar"

IMAGES="mysql:8.4.11 ghcr.io/blakeblackshear/frigate:0.17.2 freepark-hyperlpr3:0.1.3 freepark-local:0.0.1"
for img in $IMAGES; do
  docker image inspect "$img" >/dev/null || {
    echo "Missing image $img. Build/start the stack first." >&2
    exit 1
  }
done

echo "Saving $IMAGES -> $TAR"
# shellcheck disable=SC2086
docker save -o "$TAR" $IMAGES
echo "OK  $TAR"
echo "Load: docker load -i ai-build/dist/freepark-images.tar"
