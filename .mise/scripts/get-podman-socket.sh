#!/usr/bin/env bash
# Print the Docker-compatible Podman socket used by the Supabase CLI.

set -u

case "$(uname -s)" in
    Linux)
        RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
        if [ ! -d "$RUNTIME_DIR" ] || [ ! -w "$RUNTIME_DIR" ]; then
            RUNTIME_DIR="${TMPDIR:-/tmp}/batteries-included-podman-$(id -u)"
        fi

        if [ "$(id -u)" -eq 0 ] &&
            [ "$(podman info --format '{{.Host.Security.Rootless}}' 2>/dev/null)" = "false" ]; then
            echo "unix:///run/podman/podman.sock"
        else
            echo "unix://$RUNTIME_DIR/podman/podman.sock"
        fi
        ;;
    Darwin)
        MACHINE_NAME="$(
            podman machine list --format "{{.Name}}" --noheading 2>/dev/null |
                grep -v "^$" |
                head -1
        )"
        MACHINE_NAME="${MACHINE_NAME%\*}"
        MACHINE_NAME="${MACHINE_NAME:-podman-machine-default}"
        SOCKET_PATH="$(
            podman machine inspect "$MACHINE_NAME" 2>/dev/null |
                grep -o '/[^"]*podman[^"]*api\.sock' |
                head -1
        )"
        SOCKET_PATH="${SOCKET_PATH:-/tmp/podman/${MACHINE_NAME}-api.sock}"
        echo "unix://$SOCKET_PATH"
        ;;
    *)
        echo "unix:///var/run/docker.sock"
        ;;
esac
