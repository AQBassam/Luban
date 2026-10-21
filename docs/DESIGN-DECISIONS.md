# Design decisions

Luban was written from scratch after a review of an earlier Saudi screensaver project (UrTV). No code was taken from
it; this list records which problems found in that review Luban was designed to avoid, and where each answer lives.

| # | Problem | Luban's answer | Where |
|---|---|---|---|
| 1 | The page updated itself from a branch with no signature, and ran with file and universal access | Signed bundles (ECDSA P-256), size and hash checked, no rollback; page served from an https asset origin with no file access and no JS bridge | `Verifier.java`, `Updates.java`, `Screen.java`, [SECURITY.md](SECURITY.md) |
| 2 | The installer uninstalled the app when Android refused an update signed with another key | Stops and explains; `--force-reinstall` only on request. APK signer pinned; downloads checked against `SHA256SUMS` | `tools/luban.py` `install()`, `keys/apk-cert.sha256` |
| 3 | The stock home was disabled before the new one was proven, guarded only by a script that dies on reboot | New home first, verified on screen, then the stock home disabled, verified again; rollback on any failure | `switch_home()`, `tests/test_setup.py` |
| 4 | ADB debugging stayed open after setup | `setup` ends by offering to turn ADB off; `adb-off` command | `adb_off()` |
| 5 | The time zone was fixed to Riyadh | Every city carries its zone (from its country or its own); custom places need a valid IANA zone | `content/cities.json`, `settings.js` |
| 6 | Fonts and photos were fetched from a hard-coded branch while the app matched another | The app serves its own fonts; photos by validated file name; one repository constant | `Screen.page()`, `Photos.java` |
| 7 | Releases did not wait for tests; pages were served straight from the branch | One workflow: test → build+lint → publish; screen bundles go canary → stable after 48 h | `.github/workflows/ci.yml`, `promote.yml` |
| 8 | Actions pinned by moving tags, a third-party release action with write access, the keystore left on disk | Actions pinned by SHA (enforced by a test), releases with `gh`, keystore deleted after the build step | `ci.yml`, `tests/check.py` |
| 9 | Public CORS proxies and one shared free API key on every TV | No public proxies: an allowlisted proxy inside the app, and an optional cached data server for all TVs | `Net.java`, `server/` |
| 10 | No Gradle wrapper | Wrapper with a pinned distribution checksum | `android/gradle/wrapper/` |
| 11 | No licence | MIT for the code; fonts OFL; photos credited under the Unsplash License | `LICENSE` |
| — | A boot action that never arrives (not direct-boot aware), unbounded downloads, one global download lock, debloat that limited Play Store in the background | None of these: no locked-boot action, every download capped, a lock per photo, no Play Store restriction (it would delay app security updates) | `BootReceiver.java`, `Net.java`, `Photos.java`, `tools/luban.py` |

## Ideas from the review, built in

- **Gulf and the world:** 14 methods, Hanafi Asr, minute offsets, iqama per prayer, Hijri ±2 days; Oman's 37
  wilayats across all 11 governorates, plus cities worldwide.
- **Phone settings by QR code**, paired by a secret in the code.
- **One data server** for every TV (Coolify-ready).
- **Markets chosen by the owner:** gold in OMR per gram, Brent, Bitcoin, S&P 500.
- **Home screen in small parts:** `HomeActivity` lays out `Wallpaper`, `PrayerCard`, `Favourites`, `Apps`, `Ui`.
- **A setup tool that needs no AI agent** (`tools/luban.py`), with the agent route kept as an option (`docs/AGENT.md`).

## Found while building

- `String.format("%d")` prints Arabic-Indic digits on a TV set to Arabic; for hex it would have broken the update
  hash check and the pairing token. Every `String.format` uses `Locale.ROOT`; `tests/check.py` enforces it and
  `tests/verifier.sh` runs the verifier under an Arabic locale.
- Long verses did not fit the card at every resolution: the card now steps its font down (at most 30%) and the
  browser test checks every verse and hadith at 1080p and 720p.
