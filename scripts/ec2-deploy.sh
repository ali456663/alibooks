#!/bin/sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"
PROJECT_NAME="${PROJECT_NAME:-alibooks}"
FRONTEND_URL="${FRONTEND_URL:-}"
BACKEND_URL="${BACKEND_URL:-}"

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

echo "Pulling latest Docker images..."
docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$COMPOSE_FILE" pull

echo "Starting AliBooks production containers..."
docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d

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
