#!/bin/sh
set -eu

FRONTEND_URL="${FRONTEND_URL:-}"
BACKEND_URL="${BACKEND_URL:-}"

if [ -z "$FRONTEND_URL" ] || [ -z "$BACKEND_URL" ]; then
  echo "Usage:"
  echo "  FRONTEND_URL=http://your-ec2-public-ip BACKEND_URL=http://your-ec2-public-ip/api ./scripts/prod-smoke-test.sh"
  exit 1
fi

echo "Checking frontend: $FRONTEND_URL"
frontend_status="$(curl -L -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL")"
if [ "$frontend_status" != "200" ]; then
  echo "Frontend check failed. HTTP status: $frontend_status"
  exit 1
fi
echo "Frontend OK"

echo "Checking backend health: $BACKEND_URL/health"
backend_health="$(curl -L -s "$BACKEND_URL/health")"
echo "$backend_health" | grep -q '"status":"ok"' || {
  echo "Backend health check failed:"
  echo "$backend_health"
  exit 1
}
echo "Backend health OK"

echo "Checking system status: $BACKEND_URL/system/status"
system_status="$(curl -L -s "$BACKEND_URL/system/status")"
echo "$system_status" | grep -q '"database":{"ok":true}' || {
  echo "Database check failed or database is not reachable:"
  echo "$system_status"
  exit 1
}
echo "Database OK"

echo "Production smoke test passed."
