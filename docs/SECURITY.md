# Security model

A TV app that updates itself, runs a web page and opens a port on the home network has to be careful. These are the
rules Luban keeps, and where each is enforced.

## 1. Screen updates are signed, not just downloaded

The screen page changes more often than the apps, so it updates over the air. A bundle is used only when:

- its `manifest.json` is signed with the ECDSA P-256 key whose public half is built into the APK
  (`keys/screen-update.pub.pem`; the private half exists only as the `LUBAN_UPDATE_KEY` CI secret);
- `bundle.zip` is exactly the bytes the manifest names (SHA-256 and size);
- its version is newer than what the TV runs (no rollback to an older, signed but buggy page);
- it holds only the two expected file names (no paths, no zip-slip).

Code: `Verifier.java` (pure Java, tested with `tests/verifier.sh` against `tools/sign.py`, including under an Arabic
locale). A push to the repository therefore cannot reach any TV unless CI signed it, and CI signs only after every
test passed.

**Staged rollout:** a new bundle goes to `screen-canary` first: TVs whose owner picked the canary channel, plus one
TV in ten (by a stable per-install number). `promote.yml` moves it to `screen-stable` once it is 48 hours old and has
not been replaced; it checks daily, so in practice after 48 to 72 hours. Age is the only test: Luban collects nothing
from TVs, so problems are found by owners on the canary channel, and a bad bundle is stopped by publishing a fixed one
(a newer version replaces it everywhere) or by not promoting it.

## 2. The page has no special rights

The page is served from `https://appassets.androidplatform.net/` through `WebViewAssetLoader`, not from `file://`:

- no file access, no content access, no universal access from file URLs;
- no JavaScript bridge into the app;
- it cannot navigate anywhere (`shouldOverrideUrlLoading` refuses everything);
- it can only reach sources that allow it (CORS), plus `/net`, a proxy limited to two hosts and four URL shapes
  (`Net.proxyAllowed`), GET only, 2 MB cap;
- `usesCleartextTraffic="false"` and a network security config: HTTPS only.

The page never turns data into markup: the DOM is built with `textContent` only (`tests/check.py` forbids
`innerHTML`, `eval`; `tests/page.test.mjs` feeds a hostile team name and checks no element is created).

## 3. Phone settings are paired, not open

The settings page is served on the local network **only while the TV's settings screen is open**. Every path begins
with a random 128-bit token shown in the QR code: another device on the Wi-Fi cannot read or change anything without
seeing the TV. `POST` needs `Content-Type: application/json`, which a cross-site form cannot send. Bodies are capped
at 32 KB and must parse as a JSON object; every reader passes them through `sanitize()` in `web/js/settings.js`.

## 4. ADB is used, then closed

- ADB receivers in both apps require `android.permission.DUMP`: only the ADB shell can send them.
- `tools/luban.py` installs only APKs whose signing certificate matches `keys/apk-cert.sha256` (it parses the
  APK Signature Scheme v2/v3 block itself; CI checks its reading against `apksigner` on every build), and whose
  SHA-256 matches the release's `SHA256SUMS`.
- When Android refuses an update because the installed app has a different key, the tool **stops**. It never
  uninstalls to get around that protection unless asked with `--force-reinstall`.
- At the end of `setup` it offers to turn ADB off (`adb_enabled 0`), so the computer's key no longer opens a shell on
  the TV.

## 5. Changing Home cannot strand the TV

`luban.py home` gives Luban's home the HOME role while the stock home is still enabled, presses Home, and checks
Luban's home is on screen. Only then does it disable the stock home, and it checks again. Any failure puts the stock
home back at once. A small safety script on the TV covers the seconds in between. `tests/test_setup.py` checks the
order and both rollbacks against a fake TV.

Some firmware keeps its own home ahead of the one Android picks, and the only way left is to disable the TV's
packages (the setup wizard may own the Wi-Fi network). Luban does not do that for you. `luban.py homekey on` instead
turns on an accessibility service in Luban's home that reads **key presses only** (`canRetrieveWindowContent` is
false and it asks for no events): a short press of Home opens Luban's home, a long press opens the TV's own home,
and if Luban's home will not open the TV's own does. It disables no package and changes no role. `homekey off`,
`reset`, or turning it off in the TV's accessibility settings puts everything back. It stays off until you turn it on.

## 6. CI

- Tests gate the build; the build gates publishing; publishing needs the signing secrets.
- Every action is pinned to a commit SHA (`tests/check.py` enforces it).
- The keystore is deleted right after the build step; releases are made with `gh` (no third-party release action).
- The Gradle distribution is pinned by checksum.

## Reporting

Open a private security advisory on the repository.
