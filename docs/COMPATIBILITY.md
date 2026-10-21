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
| Home screen | Some firmware keeps its own home. The setup tool detects it, puts the TV's home back, and stops; the screensaver still works. The home screen of any maker (Google, Xiaomi PatchWall, Philips, a box's own) is found and restored the same way |
| The setup wizard (setupwraith) | On many Google TV sets (Sony, Philips, Skyworth, Xiaomi, TCL) it owns the Wi-Fi network saved during first setup, and Android deletes an app's networks when the app is turned off: the TV would go offline for good. It also holds the Cast keys. The setup tool leaves it on, and turns it off only when it is what takes Home, never while it owns the network (then it stops and says how to rejoin the network so it belongs to the system). Field reports: [cleanyourtv's troubleshooting notes](https://github.com/Gathin23/cleanyourtv/blob/main/docs/TROUBLESHOOTING.md) |
| Ads and telemetry off | Lists exist for Google and TCL packages; on other makers nothing is turned off |
| Inputs (HDMI) button | Only on TVs; boxes have no inputs |
| Old system web engine | The screen avoids newer JavaScript, and carries its own Umm al-Qura table for engines without that calendar. Keeping "Android System WebView" updated from the Play Store is still recommended |

## Tested

Every build is tested automatically (screen logic in Chromium, setup tool against a simulated TV, signature and
prayer-time checks). **It has not yet been run on real TV hardware**: the first installs are the real test. Please
report what you see, with the TV's make, model and Android version.
