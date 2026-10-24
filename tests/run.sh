#!/usr/bin/env bash
# Every check in one go. Needs: python3 with cryptography, node with playwright (npm i playwright@1.56.1 &&
# npx playwright install chromium), a JDK (javac). CI runs exactly this.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== dist/ is built from web/ (run python3 web/build.py after editing)"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cp -r dist "$T/before"
python3 web/build.py > /dev/null
if ! diff -r -q "$T/before" dist; then echo "FAIL dist/ was stale: commit the rebuilt files"; exit 1; fi
echo "PASS dist/ is up to date"

echo "== static checks";          python3 tests/check.py
echo "== data server";            python3 tests/test_server.py 2>&1 | tail -3
echo "== setup tool";             python3 tests/test_setup.py 2>&1 | tail -3
echo "== signed updates";         bash tests/verifier.sh
echo "== prayer times, Java = JS"; bash tests/prayer_parity.sh
echo "== the screen in a browser"; node tests/page.test.mjs
echo "== the promo site";         node tests/site.test.mjs
