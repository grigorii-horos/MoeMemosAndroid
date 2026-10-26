#!/usr/bin/env bash
# Builds Coffer's embeddable editor and copies it into the app's assets
# (spec ../coffer/docs/superpowers/specs/2026-10-27-moememos-coffer-editor-design.md).
# Usage: scripts/sync-coffer-editor.sh [path to the coffer checkout, default ../coffer]
set -euo pipefail
COFFER="${1:-$(dirname "$0")/../../coffer}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$HERE/app/src/main/assets/coffer-editor"
( cd "$COFFER" && npm run -s build:embed-editor -w @coffer-org/web )
rm -rf "$OUT"
mkdir -p "$OUT"
# The page and its chunks, then the host singletons it resolves through its importmap.
cp -r "$COFFER/packages/web/dist-embed/embed" "$COFFER/packages/web/dist-embed/assets" "$OUT/"
cp "$COFFER/packages/web/dist-embed/version.json" "$OUT/"
cp -r "$COFFER/packages/web/dist-vendor" "$OUT/vendor"
echo "coffer-editor: $(cat "$OUT/version.json" | tr -d '\n ')"
