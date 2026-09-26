#!/usr/bin/env bash
# Validates the production infra configs with the exact images pinned in docker-compose.prod.yml,
# so a broken Caddyfile/prometheus.yml or an incompatible image bump fails in CI instead of
# crash-looping production (`restart: always`). Also validates the compose file itself.
# Usage (repo root): bash scripts/qa/validate-infra-configs.sh
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash on Windows would rewrite the in-container /etc/... paths

COMPOSE_FILE="backend/docker-compose.prod.yml"
# Own project name: never touches a running production stack (project "backend") on the same host.
PROJECT="povzetkar-infra-validate"

# Dummy values for every `${VAR:?}` in the compose file; only DOMAIN_NAME/CADDY_EMAIL reach a config.
export APP_VERSION=validate DB_NAME=validate DB_USER=validate DB_PASS=validate \
  YOUTUBE_API_KEY=validate GOOGLE_API_KEYS=validate GOOGLE_OIDC_CLIENT_ID=validate \
  GOOGLE_OIDC_CLIENT_SECRET=validate FRONTEND_URL=https://example.com TRANSCRIPT_PROXY_API_KEY=validate \
  DOMAIN_NAME=example.com CADDY_EMAIL=ci@example.com CLOUDFLARE_TUNNEL_TOKEN=validate GF_ADMIN_PASSWORD=validate

compose() { docker compose -p "$PROJECT" -f "$COMPOSE_FILE" "$@"; }
trap 'compose down --volumes --remove-orphans >/dev/null 2>&1 || true' EXIT

echo "==> docker compose config"
compose config -q

echo "==> caddy validate"
compose run --rm --no-deps caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile

echo "==> promtool check config"
compose run --rm --no-deps --entrypoint promtool prometheus check config /etc/prometheus/prometheus.yml

echo "Infra configs valid."
