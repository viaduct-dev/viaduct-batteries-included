#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OS_NAME="$(uname -s)"

fail() {
    echo "ERROR: $*" >&2
    echo "Run 'mise run diagnose-podman' for detailed diagnostics." >&2
    exit 1
}

api_ready() {
    [ -S "$SOCKET_PATH" ] &&
        curl --silent --fail --max-time 2 --unix-socket "$SOCKET_PATH" \
            http://localhost/_ping >/dev/null 2>&1
}

if ! command -v podman >/dev/null 2>&1; then
    fail "Podman is not installed. Run 'mise install' first."
fi
if ! command -v curl >/dev/null 2>&1; then
    fail "curl is required to verify the Podman API socket."
fi
if [ "$(id -u)" -eq 0 ] && [ -n "${SUDO_USER:-}" ]; then
    fail "Do not run mise with sudo. Podman, mise tools, and Supabase data are user-scoped."
fi

case "$OS_NAME" in
    Darwin)
        if ! podman info >/dev/null 2>&1; then
            if ! podman machine list --format "{{.Name}}" 2>/dev/null | grep -q .; then
                echo "Initializing the Podman machine..."
                podman machine init
            fi

            echo "Starting the Podman machine..."
            podman machine start || podman info >/dev/null 2>&1
        fi
        ;;
    Linux)
        if ! podman info >/dev/null 2>&1; then
            echo "Podman is installed but cannot start containers:" >&2
            podman info >&2 || true
            if grep -qi microsoft /proc/version 2>/dev/null; then
                echo "This is WSL. Verify that WSL 2 is in use and Podman rootless prerequisites are installed." >&2
            fi
            fail "Podman is not usable."
        fi
        ;;
    *)
        fail "Unsupported operating system: $OS_NAME"
        ;;
esac

DOCKER_HOST="$("$SCRIPT_DIR/get-podman-socket.sh")"
SOCKET_PATH="${DOCKER_HOST#unix://}"

if [ "$SOCKET_PATH" = "$DOCKER_HOST" ]; then
    fail "Only Unix Podman sockets are supported, got: $DOCKER_HOST"
fi

if ! api_ready && [ "$OS_NAME" = "Linux" ]; then
    mkdir -p "$(dirname "$SOCKET_PATH")"
    chmod 700 "$(dirname "$SOCKET_PATH")"

    if command -v systemctl >/dev/null 2>&1; then
        systemctl --user start podman.socket >/dev/null 2>&1 || true
    fi

    if ! api_ready; then
        STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/batteries-included"
        LOG_FILE="$STATE_DIR/podman-system-service.log"
        mkdir -p "$STATE_DIR"
        if [ -S "$SOCKET_PATH" ]; then
            echo "Removing an unresponsive Podman API socket..."
            rm -f "$SOCKET_PATH"
        fi
        echo "Starting the Podman API service..."
        nohup podman system service --time=0 "$DOCKER_HOST" \
            >"$LOG_FILE" 2>&1 </dev/null &
    fi
fi

for _ in $(seq 1 40); do
    api_ready && break
    sleep 0.25
done

if ! api_ready; then
    fail "Podman is running, but its Docker-compatible API is not responding at $SOCKET_PATH."
fi

echo "Podman API ready: $DOCKER_HOST"
