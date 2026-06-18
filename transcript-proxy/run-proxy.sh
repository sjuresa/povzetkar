#!/bin/sh
# Launcher for the transcript proxy on the Synology DS220j (POV-196).
#
# Runtime: the box's built-in Python 3.8 (/bin/python3) — NOT SynoCommunity python312,
# which is not installed and not needed. The venv and this script MUST live on an
# exec-capable path under /volume1; /tmp is mounted noexec and the compiled pydantic_core
# .so will fail to map there ("failed to map segment from shared object").
#
# Invoked by a DSM Task Scheduler "Boot-up" triggered task (user: root). Started once at
# boot; there is no restart-on-crash supervision yet (deferred — see improvements.md). If
# the proxy stops responding, check it is running and/or flip TRANSCRIPT_PROXY_URL back to
# Railway (see RUNBOOK).

set -eu

# Resolve the directory this script lives in, so paths are not tied to one user's home.
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$APP_DIR"

# Load secrets (API_KEY) from a gitignored, chmod 600 .env file next to this script.
# main.py raises RuntimeError at startup if API_KEY is unset.
if [ -f "$APP_DIR/.env" ]; then
    # shellcheck disable=SC1091
    . "$APP_DIR/.env"
    export API_KEY
fi

# Run the app. --host 0.0.0.0 is required for tailnet (and LAN) reachability; there is no
# public port-forward, so exposure is limited to the tailnet + home LAN. Plain HTTP is fine:
# Tailscale (WireGuard) already encrypts the OCI<->Synology path. Logs (including the
# per-branch exception logging in main.py) are appended to proxy.log.
exec "$APP_DIR/.venv/bin/uvicorn" main:app \
    --host 0.0.0.0 \
    --port 8000 \
    >> "$APP_DIR/proxy.log" 2>&1
