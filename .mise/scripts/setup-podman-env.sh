#!/usr/bin/env bash
# Robust Podman environment setup for mise tasks
# This script detects the active Podman machine and sets DOCKER_HOST appropriately

set -e

# Function to detect the active Podman machine
detect_podman_machine() {
    # Try to find the currently active machine (marked with *)
    local active_machine=$(podman machine list --format "{{.Name}}" --noheading 2>/dev/null | grep -v "^$" | head -1)

    if [ -z "$active_machine" ]; then
        # Fallback: try to get default connection
        active_machine=$(podman system connection list --format "{{.Name}}" 2>/dev/null | grep "default" | head -1 | sed 's/\*//g' | xargs)
    fi

    if [ -z "$active_machine" ]; then
        # Last resort: assume standard name
        active_machine="podman-machine-default"
    fi

    echo "$active_machine"
}

# Function to extract socket path from machine config
get_socket_path() {
    local machine_name="$1"
    local socket_path=""

    # Try to get socket from machine inspect
    socket_path=$(podman machine inspect "$machine_name" 2>/dev/null | grep -o '/tmp/podman/[^"]*api\.sock' | head -1)

    if [ -z "$socket_path" ]; then
        # Try alternate socket patterns (different Podman versions)
        socket_path=$(podman machine inspect "$machine_name" 2>/dev/null | grep -o '/[^"]*\.sock' | grep -i podman | grep -i api | head -1)
    fi

    if [ -z "$socket_path" ]; then
        # Check common socket locations directly
        if [ -S "/tmp/podman/${machine_name}-api.sock" ]; then
            socket_path="/tmp/podman/${machine_name}-api.sock"
        elif [ -S "/tmp/podman/podman-machine-default-api.sock" ]; then
            socket_path="/tmp/podman/podman-machine-default-api.sock"
        elif [ -S "/run/user/$(id -u)/podman/podman.sock" ]; then
            # Linux rootless socket
            socket_path="/run/user/$(id -u)/podman/podman.sock"
        elif [ -S "/var/run/docker.sock" ]; then
            # System-wide Docker socket (Podman compatibility)
            socket_path="/var/run/docker.sock"
        fi
    fi

    echo "$socket_path"
}

# Main execution
main() {
    # Detect machine name
    MACHINE_NAME=$(detect_podman_machine)

    # Get socket path
    SOCKET_PATH=$(get_socket_path "$MACHINE_NAME")

    if [ -z "$SOCKET_PATH" ]; then
        echo "ERROR: Could not detect Podman socket path" >&2
        echo "Machine: $MACHINE_NAME" >&2
        echo "Run 'mise run diagnose-podman' for more information" >&2
        exit 1
    fi

    # Verify socket exists
    if [ ! -S "$SOCKET_PATH" ]; then
        echo "WARNING: Socket path detected but not accessible: $SOCKET_PATH" >&2
        echo "Attempting to use anyway..." >&2
    fi

    # Export DOCKER_HOST
    export DOCKER_HOST="unix://${SOCKET_PATH}"

    # Verify connectivity
    if ! podman info >/dev/null 2>&1; then
        echo "WARNING: Podman connectivity test failed with DOCKER_HOST=$DOCKER_HOST" >&2
    fi

    echo "Using Podman machine: $MACHINE_NAME" >&2
    echo "DOCKER_HOST: $DOCKER_HOST" >&2
}

main
