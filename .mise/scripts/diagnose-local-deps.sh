#!/usr/bin/env bash

set +e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROJECT_ID="batteries-included"
DB_CONTAINER="supabase_db_$PROJECT_ID"
GATEWAY_CONTAINER="supabase_kong_$PROJECT_ID"
OS_NAME="$(uname -s)"
IS_WSL=false
grep -qi microsoft /proc/version 2>/dev/null && IS_WSL=true

section() {
    echo
    echo "=== $1 ==="
}

podman_api() {
    podman --remote --url "$DETECTED_DOCKER_HOST" "$@"
}

container_diagnostics() {
    local container="$1"

    echo
    echo "--- Container state: $container ---"
    podman_api inspect --format \
        'status={{.State.Status}} exitCode={{.State.ExitCode}} oomKilled={{.State.OOMKilled}} pid={{.State.Pid}} startedAt={{.State.StartedAt}} finishedAt={{.State.FinishedAt}} error={{.State.Error}} restartCount={{.RestartCount}}' \
        "$container" 2>&1
    echo
    echo "--- Last 80 log lines: $container ---"
    podman_api logs --timestamps --tail 80 "$container" 2>&1
}

echo "=== Batteries Included Local Dependency Diagnostics ==="
echo "OS: $OS_NAME"
echo "WSL: $IS_WSL"
echo "Kernel: $(uname -r)"
echo "User: $(id -un) (uid $(id -u))"
if [ "$(id -u)" -eq 0 ] && [ -n "${SUDO_USER:-}" ]; then
    echo "WARNING: Running through sudo; rerun mise as $SUDO_USER."
fi
if [ "$IS_WSL" = true ]; then
    echo "WSL distribution: ${WSL_DISTRO_NAME:-<unknown>}"
fi

section "Tools"
if command -v mise >/dev/null 2>&1; then
    echo "mise: $(mise --version 2>&1 | head -1)"
else
    echo "mise: NOT FOUND"
fi
if command -v podman >/dev/null 2>&1; then
    echo "podman: $(podman --version 2>&1)"
else
    echo "podman: NOT FOUND"
fi
if command -v supabase >/dev/null 2>&1; then
    echo "supabase wrapper: $(command -v supabase)"
else
    echo "supabase wrapper: NOT FOUND"
fi
if [ -x "$HOME/.local/share/supabase/supabase-go" ]; then
    echo "supabase-go: $HOME/.local/share/supabase/supabase-go"
else
    echo "supabase-go: NOT BOOTSTRAPPED"
fi

section "Podman Runtime"
if podman info >/dev/null 2>&1; then
    echo "Podman engine: reachable"
    podman info --format \
        'rootless={{.Host.Security.Rootless}} graphDriver={{.Store.GraphDriverName}}' 2>/dev/null
else
    echo "Podman engine: UNREACHABLE"
    podman info 2>&1 | tail -20
fi

if [ "$OS_NAME" = "Darwin" ]; then
    echo
    podman machine list 2>&1
elif [ "$OS_NAME" = "Linux" ]; then
    echo "XDG_RUNTIME_DIR: ${XDG_RUNTIME_DIR:-<unset>}"
    if command -v systemctl >/dev/null 2>&1; then
        echo "systemd system: $(systemctl is-system-running 2>&1)"
        echo "podman.socket: $(systemctl --user is-active podman.socket 2>&1)"
    else
        echo "systemd: NOT FOUND"
    fi
fi

section "Docker API Socket"
DETECTED_DOCKER_HOST="$("$SCRIPT_DIR/get-podman-socket.sh" 2>&1)"
echo "Configured DOCKER_HOST: ${DOCKER_HOST:-<unset>}"
echo "Detected DOCKER_HOST: $DETECTED_DOCKER_HOST"
SOCKET_PATH="${DETECTED_DOCKER_HOST#unix://}"
if [ "$SOCKET_PATH" != "$DETECTED_DOCKER_HOST" ] && [ -S "$SOCKET_PATH" ]; then
    echo "Socket: present"
    if curl --silent --fail --max-time 2 --unix-socket "$SOCKET_PATH" \
        http://localhost/_ping >/dev/null 2>&1; then
        echo "Docker API: responding"
        API_READY=true
    else
        echo "Docker API: NOT RESPONDING"
        API_READY=false
    fi
else
    echo "Socket: MISSING"
    API_READY=false
fi

if [ "$API_READY" != true ] && [ "$OS_NAME" = "Linux" ]; then
    SERVICE_LOG="${XDG_STATE_HOME:-$HOME/.local/state}/batteries-included/podman-system-service.log"
    if [ -f "$SERVICE_LOG" ]; then
        echo
        echo "--- Last 30 lines: $SERVICE_LOG ---"
        tail -30 "$SERVICE_LOG"
    fi
fi

section "Supabase Containers"
HAS_SUPABASE_CONTAINERS=false
DB_STATE="missing"
GATEWAY_STATE="missing"
if [ "$API_READY" = true ]; then
    CONTAINERS="$(podman_api ps -a --filter "label=com.supabase.cli.project=$PROJECT_ID" \
        --format '{{.Names}}\t{{.Status}}\t{{.Image}}' 2>&1)"
    if [ -n "$CONTAINERS" ]; then
        HAS_SUPABASE_CONTAINERS=true
        printf '%b\n' "$CONTAINERS"
    else
        echo "No batteries-included Supabase containers found."
    fi

    DB_STATE="$(podman_api inspect --format '{{.State.Status}}' "$DB_CONTAINER" 2>/dev/null || echo missing)"
    GATEWAY_STATE="$(podman_api inspect --format '{{.State.Status}}' "$GATEWAY_CONTAINER" 2>/dev/null || echo missing)"

    if [ "$DB_STATE" != "running" ] && [ "$DB_STATE" != "missing" ]; then
        container_diagnostics "$DB_CONTAINER"
    fi
    if [ "$GATEWAY_STATE" != "running" ] && [ "$GATEWAY_STATE" != "missing" ]; then
        container_diagnostics "$GATEWAY_CONTAINER"
    fi
else
    echo "Skipped because the Podman engine is unreachable."
fi

section "Supabase Status"
if [ "$API_READY" = true ]; then
    export DOCKER_HOST="$DETECTED_DOCKER_HOST"
    "$SCRIPT_DIR/supabase.sh" status --workdir "$PROJECT_ROOT" 2>&1
    SUPABASE_STATUS=$?
    if [ "$SUPABASE_STATUS" -eq 0 ] &&
        { [ "$DB_STATE" != "running" ] || [ "$GATEWAY_STATE" != "running" ]; }; then
        echo
        echo "Supabase CLI reported running, but core containers are not running:"
        echo "  database: $DB_STATE"
        echo "  gateway: $GATEWAY_STATE"
        SUPABASE_STATUS=1
    fi
else
    echo "Skipped because the Docker-compatible API is unavailable."
    SUPABASE_STATUS=1
fi

section "Suggested Next Step"
if ! podman info >/dev/null 2>&1; then
    echo "Run: mise run podman-start"
elif [ "$API_READY" != true ]; then
    if [ "$OS_NAME" = "Darwin" ]; then
        echo "Restart Podman's API forwarding, then retry:"
        echo "  podman machine stop"
        echo "  podman machine start"
        echo "  mise run supabase-start"
    else
        echo "Run: mise run podman-start"
    fi
elif [ "$SUPABASE_STATUS" -ne 0 ] && [ "$HAS_SUPABASE_CONTAINERS" = true ]; then
    echo "Recover stale containers while preserving database volumes:"
    echo "  mise run supabase-start"
elif [ "$SUPABASE_STATUS" -ne 0 ]; then
    echo "Run: mise run supabase-start"
else
    echo "Local dependencies are healthy."
fi

exit "$SUPABASE_STATUS"
