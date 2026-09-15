#!/bin/sh
set -eu

curl_code() {
  curl -sS -o /dev/null -w "%{http_code}" --max-time 8 "$1"
}

health="$(curl_code http://127.0.0.1:8081/actuator/health || true)"
if [ "$health" != "200" ]; then
  echo "Backend health HTTP ${health:-none}" >&2
  exit 1
fi

token="$(curl -sS --max-time 15 -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  http://127.0.0.1:8081/api/v1/auth/login | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
if [ -z "$token" ]; then
  echo "Login response missing token" >&2
  exit 1
fi

ui=""
for port in 5173 5174 5175; do
  code="$(curl_code "http://127.0.0.1:$port/" || true)"
  case "$code" in
    2*|3*) ui="http://localhost:$port"; break ;;
  esac
done

echo "OK  backend health + admin login"
if [ -n "$ui" ]; then echo "OK  console $ui"; else echo "WARN  Vite UI not detected on 5173-5175 (start local_frontend: npm run dev)"; fi
echo "Login: admin / admin123"
