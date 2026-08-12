#!/usr/bin/env bash
# Print the Docker-compatible Podman socket used by the Supabase CLI.

set -u

as_unix_uri() {
    case "${1:-}" in
        unix://*)
            printf '%s\n' "$1"
            ;;
        /*)
            printf 'unix://%s\n' "$1"
            ;;
        *)
            return 1
            ;;
    esac
}

configured_connection_uri() {
    local uri

    # CONTAINER_HOST is Podman's explicit connection override.
    if uri="$(as_unix_uri "${CONTAINER_HOST:-}")"; then
        printf '%s\n' "$uri"
        return 0
    fi

    # Prefer the URI Podman has marked as its default when it is a local socket.
    uri="$(
        podman system connection list \
            --format '{{if .Default}}{{.URI}}{{end}}' 2>/dev/null |
            sed -n '/./{p;q;}'
    )"
    as_unix_uri "$uri"
}

native_linux_socket_uri() {
    local is_remote socket

    # On native Linux this asks Podman for the actual configured service path.
    # Remote clients report a path on the server, which is not a local socket.
    is_remote="$(podman info --format '{{.Host.ServiceIsRemote}}' 2>/dev/null)"
    [ "$is_remote" = "false" ] || return 1
    socket="$(podman info --format '{{.Host.RemoteSocket.Path}}' 2>/dev/null)"
    as_unix_uri "$socket"
}

if CONNECTION_URI="$(configured_connection_uri)"; then
    printf '%s\n' "$CONNECTION_URI"
    exit 0
fi

case "$(uname -s)" in
    Linux)
        if CONNECTION_URI="$(native_linux_socket_uri)"; then
            printf '%s\n' "$CONNECTION_URI"
            exit 0
        fi

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
            podman machine inspect "$MACHINE_NAME" \
                --format '{{.ConnectionInfo.PodmanSocket.Path}}' 2>/dev/null
        )"
        SOCKET_PATH="${SOCKET_PATH:-/tmp/podman/${MACHINE_NAME}-api.sock}"
        echo "unix://$SOCKET_PATH"
        ;;
    *)
        echo "unix:///var/run/docker.sock"
        ;;
esac
