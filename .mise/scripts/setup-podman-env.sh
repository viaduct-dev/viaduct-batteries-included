#!/usr/bin/env bash
# Set DOCKER_HOST for callers that source this compatibility helper.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DOCKER_HOST="$("$SCRIPT_DIR/get-podman-socket.sh")"
export DOCKER_HOST
echo "DOCKER_HOST: $DOCKER_HOST" >&2
