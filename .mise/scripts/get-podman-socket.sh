#!/usr/bin/env bash
# Get Podman socket path for mise environment variable
# This script outputs only the socket path (no export statement)

set -e

# Function to detect the active Podman machine
detect_podman_machine() {
    local active_machine=$(podman machine list --format "{{.Name}}" --noheading 2>/dev/null | grep -v "^$" | head -1)

    if [ -z "$active_machine" ]; then
        active_machine=$(podman system connection list --format "{{.Name}}" 2>/dev/null | grep "default" | head -1 | sed 's/\*//g' | xargs)
    fi

    if [ -z "$active_machine" ]; then
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
        socket_path=$(podman machine inspect "$machine_name" 2>/dev/null | grep -o '/[^"]*\.sock' | grep -i podman | grep -i api | head -1)
    fi

    if [ -z "$socket_path" ]; then
        # Check common socket locations directly
        if [ -S "/tmp/podman/${machine_name}-api.sock" ]; then
            socket_path="/tmp/podman/${machine_name}-api.sock"
        elif [ -S "/tmp/podman/podman-machine-default-api.sock" ]; then
            socket_path="/tmp/podman/podman-machine-default-api.sock"
        elif [ -S "/run/user/$(id -u)/podman/podman.sock" ]; then
            socket_path="/run/user/$(id -u)/podman/podman.sock"
        elif [ -S "/var/run/docker.sock" ]; then
            socket_path="/var/run/docker.sock"
        fi
    fi

    echo "$socket_path"
}

# Main execution
MACHINE_NAME=$(detect_podman_machine)
SOCKET_PATH=$(get_socket_path "$MACHINE_NAME")

if [ -z "$SOCKET_PATH" ]; then
    echo "unix:///tmp/podman/podman-machine-default-api.sock" # Fallback default
else
    echo "unix://${SOCKET_PATH}"
fi
