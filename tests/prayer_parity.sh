#!/usr/bin/env bash
# The home screen (Java) and the screensaver (JS) must show the same times: 900 cases, every method, both Asr rules,
# Ramadan, every listed city and a few far-north and far-south places, across a whole year.
set -euo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
node -e '
const P = require("./web/js/prayer.js");
const C = require("./content/cities.json");
const places = C.cities.map(c => [c.lat, c.lon, c.tz || C.countries[c.c].tz]).concat([[64.13,-21.9,"Atlantic/Reykjavik"],[-45.87,170.5,"Pacific/Auckland"],[59.91,10.75,"Europe/Oslo"]]);
const methods = Object.keys(P.METHODS);
let i = 0; const lines = [], want = [];
for (let n = 0; n < 900; n++) {
  const [lat, lon, tz] = places[n % places.length], method = methods[n % methods.length];
  const d = new Date(Date.UTC(2026, 0, 1) + (n * 7 % 365) * 86400000);
  const asr = n % 3 ? "standard" : "hanafi", ram = n % 5 === 0 ? 1 : 0;
  lines.push([d.getUTCFullYear(), d.getUTCMonth()+1, d.getUTCDate(), lat, lon, tz, method, asr, ram].join(" "));
  const r = P.compute({y:d.getUTCFullYear(), m:d.getUTCMonth()+1, d:d.getUTCDate()}, {lat, lon, tz}, {method, asr, ramadan: !!ram});
  want.push(P.KEYS.map(k => P.hhmm(r[k])).join(" "));
}
require("fs").writeFileSync(process.argv[1] + "/cases.txt", lines.join("\n") + "\n");
require("fs").writeFileSync(process.argv[1] + "/js.txt", want.join("\n") + "\n");
' "$T"
javac -d "$T/cls" android/launcher/src/main/java/om/luban/home/PrayerTimes.java 2>&1 | grep -v JAVA_TOOL || true
java -cp "$T/cls" om.luban.home.PrayerTimes < "$T/cases.txt" > "$T/java.txt" 2> >(grep -v JAVA_TOOL >&2)
if diff -q "$T/js.txt" "$T/java.txt" > /dev/null; then
  echo "PASS prayer times: Java and JS agree on $(wc -l < "$T/js.txt") cases"
else
  echo "FAIL prayer times differ:"; diff <(paste -d'|' "$T/cases.txt" "$T/js.txt") <(paste -d'|' "$T/cases.txt" "$T/java.txt") | head -20; exit 1
fi
