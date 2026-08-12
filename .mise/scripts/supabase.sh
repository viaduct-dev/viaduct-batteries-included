#!/usr/bin/env bash

set -euo pipefail

export SUPABASE_TELEMETRY_DISABLED="${SUPABASE_TELEMETRY_DISABLED:-1}"

SUPABASE_GO="${SUPABASE_GO_BINARY:-$HOME/.local/share/supabase/supabase-go}"
if [ -x "$SUPABASE_GO" ]; then
    exec "$SUPABASE_GO" "$@"
fi

SUPABASE_WRAPPER="$(command -v supabase 2>/dev/null || true)"
if [ -z "$SUPABASE_WRAPPER" ]; then
    echo "ERROR: Supabase CLI is not installed. Run 'mise install' first." >&2
    exit 1
fi

if "$SUPABASE_WRAPPER" --version >/dev/null 2>&1; then
    exec "$SUPABASE_WRAPPER" "$@"
fi

# Some mise UBI releases install only a launcher. Bootstrap the pinned Go CLI.
SUPABASE_VERSION="${SUPABASE_VERSION:-$(basename "$(dirname "$SUPABASE_WRAPPER")")}"
SUPABASE_VERSION="${SUPABASE_VERSION#v}"
case "$SUPABASE_VERSION" in
    *[!0-9.]* | "")
        echo "ERROR: Cannot determine the Supabase CLI version from $SUPABASE_WRAPPER." >&2
        exit 1
        ;;
esac

case "$(uname -s)" in
    Linux) PLATFORM="linux" ;;
    Darwin) PLATFORM="darwin" ;;
    *)
        echo "ERROR: Unsupported platform for Supabase CLI bootstrap: $(uname -s)" >&2
        exit 1
        ;;
esac

case "$(uname -m)" in
    x86_64 | amd64) ARCH="amd64" ;;
    arm64 | aarch64) ARCH="arm64" ;;
    *)
        echo "ERROR: Unsupported architecture for Supabase CLI bootstrap: $(uname -m)" >&2
        exit 1
        ;;
esac

INSTALL_DIR="$(dirname "$SUPABASE_GO")"
ARCHIVE="$(mktemp)"
trap 'rm -f "$ARCHIVE"' EXIT
mkdir -p "$INSTALL_DIR"
echo "Installing Supabase CLI v$SUPABASE_VERSION..." >&2
curl --fail --location --silent --show-error \
    "https://github.com/supabase/cli/releases/download/v$SUPABASE_VERSION/supabase_${SUPABASE_VERSION}_${PLATFORM}_${ARCH}.tar.gz" \
    --output "$ARCHIVE"
tar -xzf "$ARCHIVE" -C "$INSTALL_DIR"
rm -f "$ARCHIVE"
trap - EXIT

if [ -x "$SUPABASE_GO" ]; then
    exec "$SUPABASE_GO" "$@"
fi

echo "ERROR: Supabase CLI archive did not install $SUPABASE_GO." >&2
exit 1
