# Setting up a TV with Luban: instructions for an AI agent

You are on a computer on the same Wi-Fi as the owner's Android TV / Google TV, in a clone of this repository. Speak
to the owner in their language (Arabic by default), keep commands in English. Work in this order and stop at any
`!!` line the tool prints: read it to the owner and do what it says, never work around it.

## Before touching the TV

1. Ask the owner to: Settings → System → About → press OK on **Build** seven times → back → Developer options →
   turn on **USB debugging** (and **Network debugging** if listed). The TV must stay on.
2. Ask them to allow you to run commands in this folder for the whole task.
3. Check this computer is on the home network, not a cloud sandbox: `python3 tools/luban.py find`. If it says this
   computer is not on a home network, stop and tell the owner to run you on their own computer.

## Setup

```bash
pip install adb-shell cryptography
python3 tools/luban.py setup
```

- When it prints "connecting… choose Allow", tell the owner: on the TV, «Allow USB debugging?» → **Allow**, tick
  **Always allow**. The first connection can take up to two minutes.
- `--no-home` keeps the TV's own home screen (if the owner prefers it, or if the tool reports the firmware pins its
  home). `--no-debloat` leaves ads/telemetry packages alone.
- If a step fails with a connection problem, run the same command again: every step is safe to repeat.
- If it stops because an app "was signed with a different key", explain that an older or unofficial copy of Luban
  is installed. Use `--force-reinstall` only if the owner agrees to lose that app's settings.
- At the end it asks whether to turn ADB off. Recommend **yes**: it closes the door the setup used. Do not answer for
  the owner.

## After setup

The TV shows a QR code (Luban's settings screen). Ask the owner to scan it with their phone and choose their city,
calculation method (the country default is usually right), teams, and the card beside the clock. Everything else is
optional.

If the owner wants you to set things instead (ADB must still be on):

```bash
python3 tools/luban.py cities                       # the city ids
python3 tools/luban.py settings --city nizwa --panel prayers --home-card on
python3 tools/luban.py favourites                   # list apps, then:
python3 tools/luban.py favourites com.google.android.youtube.tv,com.netflix.ninja
```

## Undo

`python3 tools/luban.py reset` puts the TV back as it came.

Never disable packages beyond `tools/lists/safe.txt`, never uninstall system apps, never edit `tools/lists/keep.txt`
to make a step pass.
