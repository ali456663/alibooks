#!/bin/sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"
PROJECT_NAME="${PROJECT_NAME:-alibooks}"
FRONTEND_URL="${FRONTEND_URL:-}"
BACKEND_URL="${BACKEND_URL:-}"
REQUIRE_IMMUTABLE_TAG="${REQUIRE_IMMUTABLE_TAG:-true}"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "Missing $COMPOSE_FILE. Run this script from the AliBooks project folder on EC2."
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE."
  echo "Create it first:"
  echo "  cp .env.production.example .env"
  echo "  nano .env"
  exit 1
fi

echo "Using compose file: $COMPOSE_FILE"
echo "Using env file: $ENV_FILE"

env_value() {
  key="$1"
  grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d= -f2-
}

require_env() {
  key="$1"
  value="$(env_value "$key")"
  if [ -z "$value" ]; then
    echo "Refusing deploy: $key is missing from $ENV_FILE."
    exit 1
  fi
}

require_env "DOCKERHUB_USERNAME"
require_env "IMAGE_TAG"
require_env "APP_FRONTEND_URL"
require_env "APP_CORS_ALLOWED_ORIGINS"
require_env "SPRING_DATASOURCE_URL"
require_env "SPRING_DATASOURCE_USERNAME"
require_env "SPRING_DATASOURCE_PASSWORD"
require_env "JWT_SECRET"
require_env "APP_AUTH_REGISTRATION_BOOTSTRAP_KEY"

dockerhub_username="$(env_value DOCKERHUB_USERNAME)"
image_tag="$(env_value IMAGE_TAG)"
frontend_origin="$(env_value APP_FRONTEND_URL)"
cors_origins="$(env_value APP_CORS_ALLOWED_ORIGINS)"
ddl_auto="$(env_value SPRING_JPA_HIBERNATE_DDL_AUTO)"
schema_patch="$(env_value APP_SCHEMA_PATCH_ENABLED)"
local_cors="$(env_value APP_CORS_LOCAL_DEV_ENABLED)"
test_reset="$(env_value APP_TEST_DATA_RESET_ENABLED)"
bank_reset="$(env_value APP_BANK_RECONCILIATION_RESET_ENABLED)"
jwt_secret="$(env_value JWT_SECRET)"
bootstrap_key="$(env_value APP_AUTH_REGISTRATION_BOOTSTRAP_KEY)"

case "$dockerhub_username:$frontend_origin:$cors_origins" in
  *your-dockerhub-user*|*replace_*|*localhost*|*127.0.0.1*)
    echo "Refusing deploy: Dockerhub or public origin still uses a placeholder/local value."
    exit 1
    ;;
esac

if [ "$REQUIRE_IMMUTABLE_TAG" = "true" ] && ! printf '%s' "$image_tag" | grep -Eq '^(sha-[0-9a-f]{7,64}|v[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?)$'; then
  echo "Refusing deploy: IMAGE_TAG must be an immutable sha-* or v* release tag."
  echo "Use REQUIRE_IMMUTABLE_TAG=false only for a deliberate non-production demo."
  exit 1
fi

case "$ddl_auto:$schema_patch:$local_cors:$test_reset:$bank_reset" in
  validate:false:false:false:false|none:false:false:false:false) ;;
  *)
    echo "Refusing deploy: production schema, CORS or reset flags are unsafe."
    echo "Require ddl-auto=validate/none, schema patch=false, local CORS=false and both reset flags=false."
    exit 1
    ;;
esac

if [ "${#jwt_secret}" -lt 32 ] || [ "${#bootstrap_key}" -lt 32 ] || [ "$jwt_secret" = "$bootstrap_key" ]; then
  echo "Refusing deploy: JWT_SECRET and APP_AUTH_REGISTRATION_BOOTSTRAP_KEY must be distinct secrets of at least 32 characters."
  exit 1
fi

echo "Validating production compose configuration..."
docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$COMPOSE_FILE" config --quiet
echo "Production configuration passed fail-closed checks."

# A new volume hides uploads stored in an old container's writable layer.
# Never recreate that container before its files have been migrated.
backend_id="$(docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$COMPOSE_FILE" ps -a -q backend)"
if [ -n "$backend_id" ]; then
  upload_mount="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/app/uploads"}}{{.Destination}}{{end}}{{end}}' "$backend_id")"
  if [ "$upload_mount" != "/app/uploads" ]; then
    echo "Refusing deploy: existing backend has no persistent /app/uploads volume."
    echo "Preserve and migrate receipt files first. See docs/backup-restore-runbook.md."
    exit 1
  fi
fi

echo "Pulling latest Docker images..."
docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$COMPOSE_FILE" pull

echo "Starting AliBooks production containers..."
docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --wait --wait-timeout 120

echo "Container status:"
docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$COMPOSE_FILE" ps

if [ -n "$FRONTEND_URL" ] && [ -n "$BACKEND_URL" ]; then
  echo "Running smoke test..."
  FRONTEND_URL="$FRONTEND_URL" BACKEND_URL="$BACKEND_URL" sh ./scripts/prod-smoke-test.sh
else
  echo "Smoke test skipped because FRONTEND_URL or BACKEND_URL is missing."
  echo "Example:"
  echo "  FRONTEND_URL=http://your-ec2-public-ip BACKEND_URL=http://your-ec2-public-ip/api sh ./scripts/ec2-deploy.sh"
fi

echo "Recent backend logs:"
docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=40 backend

echo "Deploy finished."
