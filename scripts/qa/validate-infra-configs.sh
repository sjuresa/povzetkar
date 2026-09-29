#!/usr/bin/env bash
# Validates the production infra configs with the exact images pinned in docker-compose.prod.yml,
# so a broken prometheus.yml or an incompatible image bump fails in CI instead of
# crash-looping production (`restart: always`). Also validates the compose file itself.
# Usage (repo root): bash scripts/qa/validate-infra-configs.sh
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash on Windows would rewrite the in-container /etc/... paths

COMPOSE_FILE="backend/docker-compose.prod.yml"
# Own project name: never touches a running production stack (project "backend") on the same host.
PROJECT="povzetkar-infra-validate"

# Dummy values for every `${VAR:?}` in the compose file; none of them reaches a validated config.
export APP_VERSION=validate DB_NAME=validate DB_USER=validate DB_PASS=validate \
  YOUTUBE_API_KEY=validate GOOGLE_API_KEYS=validate GOOGLE_OIDC_CLIENT_ID=validate \
  GOOGLE_OIDC_CLIENT_SECRET=validate FRONTEND_URL=https://example.com TRANSCRIPT_PROXY_API_KEY=validate \
  CLOUDFLARE_TUNNEL_TOKEN=validate GF_ADMIN_PASSWORD=validate

compose() { docker compose -p "$PROJECT" -f "$COMPOSE_FILE" "$@"; }

echo "==> docker compose config"
compose config -q

# Plain `docker run` without a network, config via stdin: `compose run` would create the project network with
# the prod compose file's fixed subnet, which overlaps the running prod network on the same host (POV-214).
echo "==> promtool check config"
PROMETHEUS_IMAGE=$(compose config --images | grep '^prom/prometheus:')
docker run --rm -i --network none --entrypoint promtool "$PROMETHEUS_IMAGE" check config /dev/stdin \
  < backend/infra/monitoring/prometheus/prometheus.yml

echo "Infra configs valid."
