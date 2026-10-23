# Which TVs · الأجهزة المتوافقة

Luban needs **Android TV or Google TV, Android 8.0 or newer**. The maker does not matter; the operating system does.

## Targeted (not yet tested on hardware)

Any TV or box running Android TV / Google TV 8.0+, for example: Sony Bravia, TCL, Philips and Hisense models sold
*with Android/Google TV*, Xiaomi Mi TV and Mi Box, Sharp, Toshiba and Panasonic Android models, Nvidia Shield,
Chromecast with Google TV, Google TV Streamer, and most Android TV boxes.

## Does not work

| Device | Why |
|---|---|
| Samsung | Tizen, not Android |
| LG | webOS, not Android |
| Hisense VIDAA models | VIDAA, not Android (Hisense also sells Google TV models: those work) |
| Roku TV, Apple TV | not Android |
| Amazon Fire TV | Fire OS is built on Android and the apps install, but Fire OS does not let another app be the screensaver or the home screen |
| Android 7 and older | below the minimum (the security code needs Android 8) |

## What depends on the TV

| Part | Note |
|---|---|
| Screensaver | Google TV's own menu lists only Google's screensaver; `tools/luban.py` selects Luban with one ADB setting, which Android TV and Google TV both honour |
| Home screen | Some firmware keeps its own home (the Skyworth SWTV-22TGS-4K does: see Tested). The setup tool detects it, puts the TV's home back, and stops; the screensaver still works. The home screen of any maker (Google, Xiaomi PatchWall, Philips, a box's own) is found and restored the same way |
| The setup wizard (setupwraith) | On many Google TV sets (Sony, Philips, Skyworth, Xiaomi, TCL) it owns the Wi-Fi network saved during first setup, and Android deletes an app's networks when the app is turned off: the TV would go offline for good. It also holds the Cast keys. The setup tool leaves it on, and turns it off only when it is what takes Home, never while it owns the network (then it stops and says how to rejoin the network so it belongs to the system). Field reports: [cleanyourtv's troubleshooting notes](https://github.com/Gathin23/cleanyourtv/blob/main/docs/TROUBLESHOOTING.md) |
| Ads and telemetry off | Lists exist for Google and TCL packages; on other makers nothing is turned off |
| Inputs (HDMI) button | Only on TVs; boxes have no inputs |
| Old system web engine | The screen avoids newer JavaScript, and carries its own Umm al-Qura table for engines without that calendar. Keeping "Android System WebView" updated from the Play Store is still recommended |

## Tested

Every build is tested automatically (screen logic in Chromium, setup tool against a simulated TV, signature and
prayer-time checks). Real hardware so far is one TV. Please report what you see, with the TV's make, model and
Android version.

### Skyworth SWTV-22TGS-4K (21 October 2026)

Google TV on Android 11 (API 30), build `RTM7.230903.114`, MediaTek mt5889, 2825 MB RAM, Android System WebView
155.0.8059.43, connected by Ethernet. Installed with `tools/luban.py setup` (apps from `apk-latest`; `om.luban.home`
was 1.22).

| Part | Result |
|---|---|
| `check` | Read-only, changed nothing. The setup wizard (setupwraith) is present and does not own the network |
| Install, signature and certificate checks | Works |
| Screensaver | Works. Dial, ember, El Messiri font, prayer times, Hijri date, weather, markets and the match card render as designed (compared a photo of the TV with an ADB screenshot) |
| Smoke | Works. In daytime with default settings the threads clearly fade almost away and glow again (the two layers breathe over 9 s). It is frozen by design while night dimming is on (from 2 h after Isha to 30 min before Fajr) |
| Phone settings page (QR) | Opens and saves settings. Team search works: Real Madrid found, with its recent and next matches |
| Prayer times | Sohar matches the Ministry's calendar app to the minute (Fajr, Dhuhr, Asr, Maghrib, Isha) |
| Match card | A Latin home name (Como 1907) pulled the score to the wrong side in Arabic; fixed after this test, the strip under the clock was already right |
| Home screen | **Does not switch.** See below |
| Luban home, opened by hand (22 October, v1.37) | Works: the anchored layout (greeting, clock, dates and prayer top right; five pills top left; one favourites row ending in an "All apps" tile), the all-apps page with six columns, the focus frame is no longer clipped at the row's ends, and focus stops at both ends. Fits 960×540 dp |
| Household tasks | Work on the TV: the pill opens the phone page at `#tasks`, tasks show as a card page on the screensaver. Tried by writing settings over ADB and restoring them afterwards |
| Photos | 52 photos from 17 places; the screensaver and the home screen show the same four each day. Home carries eight of them and reads the rest through the screen app's `content://` provider (not yet seen on the TV: the first such day is 24 October) |
| Ads/telemetry off | Works, run on its own (`debloat`) after the home step failed. 6 of the 17 listed packages are on this TV and were turned off: `feedback`, `partnersetup`, `onetimeinitializer`, `play.games`, `autoinstalls.config.google.gtvpai`, and Google's screensaver `dreamx` |
| Animation speed | Works: animations at 0.5x, cached-app freezer on |
| Remote and Input button | Work after the above (Home, navigation, Input) |

**Home screen.** `setup` stopped at step 3 and put everything back, as designed: Luban's home never opened when Home was
pressed, nothing was disabled. Checked on the TV by hand, each step undone straight away:

- Android accepts Luban as the HOME role holder (`cmd role add-role-holder`), and `cmd package set-home-activity`
  answers `Success` and moves the role to Luban.
- `resolve-activity` for the Home intent still answers Google's `launcherx` (priority 2; Luban's home is priority 0),
  and pressing Home starts `launcherx` explicitly. The cause is not confirmed; this firmware seems to rank its own
  home above the role and the preferred activity.
- The only way left is turning `launcherx` off, which the tool will not do before Luban's home is proven. It was not
  tried: the next home in line is setupwraith's `RecoveryActivity` (priority 1, above Luban's), not Luban's.

On this TV the screensaver is the supported part. Luban's home can still be opened by hand as an app.

The route that needs no package turned off is an accessibility service that catches the Home key (what Couchy Launcher recommends for Google TV): `luban.py homekey on`, in Luban's home since v1.39. A short press opens Luban's home, a long press opens `launcherx`. Not yet tried on this TV with the remote.