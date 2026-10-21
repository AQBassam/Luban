#!/usr/bin/env bash
# Run a command; if it fails, publish its FAIL/error lines and its last lines as one annotation on the run, so the
# reason is visible from the run's summary and the API without opening the full log.
#   .github/annotate.sh "Tests" tests/run.sh
title="$1"; shift
log=$(mktemp)
set +e
"$@" 2>&1 | tee "$log"
rc=${PIPESTATUS[0]}
set -e
if [ "$rc" -ne 0 ]; then
  body=$( { grep -E 'FAIL|!!|[Ee]rror|Exception|Traceback|What went wrong|e: |w: ' "$log" | head -40; echo '--- last lines ---'; tail -25 "$log"; } \
          | cut -c1-300 | sed -e 's/%/%25/g' | awk 'BEGIN{ORS="%0A"} {print}')
  echo "::error title=${title} failed::${body}"
fi
exit "$rc"
