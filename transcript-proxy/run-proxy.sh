#!/bin/sh
# Launcher for the transcript proxy on a self-hosted host (POV-196).
#
# The venv and this script MUST live on an exec-capable path; on a noexec mount (e.g. /tmp on
# some systems) the compiled pydantic_core .so fails to map ("failed to map segment from
# shared object"). requirements.txt is pinned to a Python 3.8-compatible set.
#
# Meant to be invoked by a boot-time task of the host. Started once at boot; there is no
# restart-on-crash supervision yet (deferred — see improvements.md). If the proxy stops
# responding, check it is running and/or point TRANSCRIPT_PROXY_URL at the fallback host.

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

# Run the app. --host 0.0.0.0 is required for VPN (and LAN) reachability; do not publish the
# port to the internet. Plain HTTP is fine when a VPN already encrypts the backend<->proxy
# path. Logs (including the per-branch exception logging in main.py) are appended to proxy.log.
exec "$APP_DIR/.venv/bin/uvicorn" main:app \
    --host 0.0.0.0 \
    --port 8000 \
    >> "$APP_DIR/proxy.log" 2>&1
