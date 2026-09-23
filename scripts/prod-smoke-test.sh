#!/bin/sh
set -eu

FRONTEND_URL="${FRONTEND_URL:-}"
BACKEND_URL="${BACKEND_URL:-}"
AUTH_TOKEN="${AUTH_TOKEN:-}"

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

echo "Checking protected system status: $BACKEND_URL/system/status"
if [ -n "$AUTH_TOKEN" ]; then
  case "$AUTH_TOKEN" in
    Bearer\ *) auth_header="$AUTH_TOKEN" ;;
    *) auth_header="Bearer $AUTH_TOKEN" ;;
  esac
  system_status="$(curl -L -s -H "Authorization: $auth_header" "$BACKEND_URL/system/status")"
  # Authenticated response must contain "database":{"ok":true}.
  echo "$system_status" | grep -q '"database":{"ok":true}' || {
    echo "Database check failed or database is not reachable."
    exit 1
  }
  echo "Authenticated system status and database OK"
else
  status_code="$(curl -L -s -o /dev/null -w "%{http_code}" "$BACKEND_URL/system/status")"
  if [ "$status_code" != "401" ]; then
    echo "System status protection check failed. Expected HTTP 401, got $status_code"
    exit 1
  fi
  echo "System status protected (HTTP 401 without AUTH_TOKEN)"
  echo "Set AUTH_TOKEN to verify database.ok in the protected status response."
fi

echo "Production smoke test passed."
