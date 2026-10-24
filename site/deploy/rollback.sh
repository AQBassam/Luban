#!/usr/bin/env bash
# Returns "current" to the previous release (or to the one you name):
#   sudo bash deploy/rollback.sh                        # previous
#   sudo bash deploy/rollback.sh 20261025-150000-v1.60  # a specific one (see: ls -1t /var/www/lubantv/releases)
set -euo pipefail
SITE_ROOT="${SITE_ROOT:-/var/www/lubantv}"
if [ $# -ge 1 ]; then TARGET="$SITE_ROOT/releases/$1"; else TARGET="$(cat "$SITE_ROOT/.previous" 2>/dev/null || true)"; fi
[ -n "$TARGET" ] && [ -f "$TARGET/index.html" ] || { echo "لا يوجد إصدار سابق صالح. المتاح:"; ls -1t "$SITE_ROOT/releases"; exit 1; }
NOW="$(readlink -f "$SITE_ROOT/current")"
ln -sfn "$TARGET" "$SITE_ROOT/current.tmp"
mv -Tf "$SITE_ROOT/current.tmp" "$SITE_ROOT/current"
echo "$NOW" > "$SITE_ROOT/.previous"
[ "${SKIP_NGINX:-0}" = 1 ] || systemctl reload nginx
echo "current → $TARGET"
grep -o '"version": *"[^"]*"' "$TARGET/version.json"
