#!/usr/bin/env python3
"""Set up a TV with Luban over the network (ADB), from any computer on the same Wi-Fi. No adb binary needed.

    pip install adb-shell cryptography           # once
    python3 tools/luban.py find                   # which TV on this network has ADB open
    python3 tools/luban.py setup --host 192.168.1.50
        connect (the TV asks "Allow USB debugging?" once) → install both apps (signature checked) → screensaver on
        → Luban as home (stock home disabled only after Luban's is proven to work) → ads/telemetry off (reversible)
        → offers to turn ADB off again at the end
    python3 tools/luban.py settings --city salalah --lang ar --panel prayers --home-card on
    python3 tools/luban.py favourites com.google.android.youtube.tv,com.netflix.ninja
    python3 tools/luban.py status | update | reset | adb-off

Every step is safe to run again. State per TV in ~/.luban/.
"""
import argparse
import base64
import hashlib
import ipaddress
import json
import os
import re
import socket
import struct
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME_DIR = Path(os.environ.get("LUBAN_HOME", Path.home() / ".luban"))
REPO = "AQBassam/Luban"
APKS = {"screen": ("luban-screen.apk", "om.luban.screen"), "home": ("luban-home.apk", "om.luban.home")}
STOCK_HOMES = {
    "com.google.android.apps.tv.launcherx": "com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity",
    "com.google.android.tvlauncher": "com.google.android.tvlauncher/.MainActivity",
    "com.google.android.leanbacklauncher": "com.google.android.leanbacklauncher/.MainActivity",
}
SETUP_APP = "com.google.android.tungsten.setupwraith"
UAD = ("https://raw.githubusercontent.com/Universal-Debloater-Alliance/"
       "universal-android-debloater-next-generation/main/resources/assets/uad_lists.json")
DEADMAN = """#!/system/bin/sh
# Luban safety switch, runs on the TV. If /data/local/tmp/luban_home_ok does not appear in time, the stock home comes
# back by itself. Started only for the seconds between switching home and proving the new home works.
i=0
while [ $i -lt "$2" ]; do [ -f /data/local/tmp/luban_home_ok ] && exit 0; sleep 1; i=$((i+1)); done
pm enable "${1%%/*}"; pm enable com.google.android.tungsten.setupwraith
cmd role add-role-holder --user 0 android.app.role.HOME "${1%%/*}"
cmd package set-home-activity --user 0 "$1"
input keyevent KEYCODE_HOME
"""


class Stop(Exception):
    """A step that must not go on; the message says why and what to do."""


def say(msg):
    print(msg, flush=True)


# ---------------------------------------------------------------------------- APK signature pin

def apk_signer_sha256(data):
    """SHA-256 of the first signer's certificate in the APK Signature Scheme v2/v3 block (what Android checks)."""
    eocd = data.rfind(b"PK\x05\x06", max(0, len(data) - 65557))
    if eocd < 0:
        raise Stop("not an APK (no zip end record)")
    cd_offset = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[cd_offset - 16:cd_offset] != b"APK Sig Block 42":
        raise Stop("the APK has no v2/v3 signature")
    size = struct.unpack_from("<Q", data, cd_offset - 24)[0]
    pos, end = cd_offset - size - 8 + 8, cd_offset - 24
    blocks = {}
    while pos < end:
        ln, bid = struct.unpack_from("<QI", data, pos)
        blocks[bid] = data[pos + 12:pos + 8 + ln]
        pos += 8 + ln
    block = blocks.get(0xF05368C0) or blocks.get(0x7109871A)   # v3, else v2
    if block is None:
        raise Stop("the APK has no v2/v3 signer")

    def seq(buf, p):   # length-prefixed (uint32) value at p -> (value, next p)
        n = struct.unpack_from("<I", buf, p)[0]
        return buf[p + 4:p + 4 + n], p + 4 + n

    signers, _ = seq(block, 0)
    signer, _ = seq(signers, 0)
    signed, _ = seq(signer, 0)
    _digests, p = seq(signed, 0)
    certs, _ = seq(signed, p)
    cert, _ = seq(certs, 0)
    return hashlib.sha256(cert).hexdigest()


def pinned_cert():
    return (ROOT / "keys/apk-cert.sha256").read_text().split()[0].replace(":", "").lower()


def download(url, limit=80_000_000):
    req = urllib.request.Request(url, headers={"User-Agent": "luban-setup"})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read(limit + 1)
    if len(data) > limit:
        raise Stop(f"{url}: larger than expected")
    return data


def fetch_apk(which, tag):
    name, pkg = APKS[which]
    base = f"https://github.com/{REPO}/releases/download/{tag}/"
    say(f"   downloading {name} ({tag})")
    try:
        apk, sums = download(base + name), download(base + "SHA256SUMS", 100_000).decode()
    except Stop:
        raise
    except Exception as e:
        raise Stop(f"could not download {base}{name}: {e}")
    want = dict(reversed(l.split()) for l in sums.splitlines() if l.strip()).get(name)
    if want != hashlib.sha256(apk).hexdigest():
        raise Stop(f"{name}: does not match SHA256SUMS (corrupt download?)")
    cert = apk_signer_sha256(apk)
    if cert != pinned_cert():
        raise Stop(f"{name}: signed by an unknown key ({cert[:16]}…), not Luban's ({pinned_cert()[:16]}…). Refusing to install.")
    return apk


# ---------------------------------------------------------------------------- the TV

class Tv:
    """ADB over TCP with retries: Wi-Fi drops are normal, a command that fails is retried on a fresh connection."""

    def __init__(self, host, port=5555):
        self.host, self.port, self.dev = host, port, None

    def connect(self):
        from adb_shell.adb_device import AdbDeviceTcp
        from adb_shell.auth.keygen import keygen
        from adb_shell.auth.sign_pythonrsa import PythonRSASigner
        HOME_DIR.mkdir(parents=True, exist_ok=True)
        key = HOME_DIR / "adbkey"
        if not key.exists():
            keygen(str(key))
            os.chmod(key, 0o600)
        signer = PythonRSASigner(Path(str(key) + ".pub").read_text(), key.read_text())
        self.dev = AdbDeviceTcp(self.host, self.port, default_transport_timeout_s=30)
        say(f"   connecting to {self.host}… if the TV asks «Allow USB debugging?», choose Allow (and «Always allow»)")
        self.dev.connect(rsa_keys=[signer], auth_timeout_s=120)
        return self

    def shell(self, cmd, timeout=60, tries=3):
        for attempt in range(tries):
            try:
                if self.dev is None:
                    self.connect()
                return self.dev.shell(cmd, read_timeout_s=timeout) or ""
            except Exception as e:  # noqa: BLE001
                if attempt == tries - 1:
                    raise Stop(f"lost the TV ({e}); run the same command again")
                say(f"   (connection problem, retrying: {e.__class__.__name__})")
                self.dev = None
                time.sleep(3)

    def push_bytes(self, data, remote):
        tmp = HOME_DIR / ("push-" + Path(remote).name)
        tmp.write_bytes(data)
        try:
            for attempt in range(3):
                try:
                    if self.dev is None:
                        self.connect()
                    self.dev.push(str(tmp), remote, read_timeout_s=300)
                    return
                except Exception as e:  # noqa: BLE001
                    if attempt == 2:
                        raise Stop(f"upload failed ({e}); run the same command again")
                    self.dev = None
                    time.sleep(3)
        finally:
            tmp.unlink(missing_ok=True)

    def packages(self, flag=""):
        return {l.split(":", 1)[1].strip() for l in self.shell(f"pm list packages {flag}").splitlines() if l.startswith("package:")}

    def resumed(self):
        out = self.shell("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity'")
        return out

    def home_resolves_to(self):
        return self.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").strip().splitlines()[-1:] or [""]

    def broadcast(self, component, action, extras=""):
        out = self.shell(f"am broadcast -n {component} -a {action} {extras}")
        m = re.search(r'data="(.*)"\s*$', out, re.S)
        return m.group(1) if m else out


# ---------------------------------------------------------------------------- steps

def state_file(tv):
    serial = re.sub(r"[^A-Za-z0-9_.-]", "", tv.shell("getprop ro.serialno").strip()) or tv.host
    return HOME_DIR / f"state-{serial}.json"


def load_state(tv):
    f = state_file(tv)
    return json.loads(f.read_text()) if f.exists() else {"disabled": [], "stock_home": None}


def save_state(tv, st):
    state_file(tv).write_text(json.dumps(st, indent=1))


def install(tv, which, tag="apk-latest", force_reinstall=False):
    name, pkg = APKS[which]
    apk = fetch_apk(which, tag)
    remote = f"/data/local/tmp/{name}"
    tv.push_bytes(apk, remote)
    out = tv.shell(f"pm install -r {remote}", timeout=300)
    if "Success" not in out and re.search(r"UPDATE_INCOMPATIBLE|signatures do not match", out):
        # Android refuses an update signed with another key. That is a protection, not an obstacle: never removed silently.
        if not force_reinstall:
            tv.shell(f"rm -f {remote}")
            raise Stop(f"{pkg} on this TV was signed with a different key than this release. Android refuses to mix "
                       f"them, and so do we. If you trust this release and accept losing that app's settings, run "
                       f"again with --force-reinstall.")
        say(f"   --force-reinstall: removing the old {pkg} (its settings are lost)")
        tv.shell(f"pm uninstall {pkg}")
        out = tv.shell(f"pm install -r {remote}", timeout=300)
    tv.shell(f"rm -f {remote}")
    if "Success" not in out:
        raise Stop(f"installing {name} failed: {out.strip()}")
    say(f"   ✓ {pkg} installed")


def screensaver_on(tv):
    for cmd in ("settings put secure screensaver_enabled 1",
                "settings put secure screensaver_components om.luban.screen/.LubanDream",
                "settings put secure screensaver_default_component om.luban.screen/.LubanDream"):
        tv.shell(cmd)
    say("   ✓ Luban is the screensaver")


NOT_A_HOME = re.compile(r"ResolverActivity|FallbackHome|om\.luban\.home|com\.android\.settings")


def stock_home(tv):
    """The TV's own home, whatever the maker: what Android opens for Home right now (Google TV, Android TV, Xiaomi
    PatchWall, Philips, Sony, a box's custom launcher…). Falls back to the known Google homes, then to the list of
    every installed home."""
    now = tv.home_resolves_to()[0].strip()
    if "/" in now and not NOT_A_HOME.search(now):
        return now
    have = tv.packages()
    for pkg, comp in STOCK_HOMES.items():
        if pkg in have:
            return comp
    homes = [l.strip() for l in tv.shell("cmd package query-activities --brief -a android.intent.action.MAIN "
                                          "-c android.intent.category.HOME").splitlines() if "/" in l]
    homes = [h for h in homes if not NOT_A_HOME.search(h)]
    if homes:
        return homes[0]
    raise Stop("could not tell which home screen this TV uses; keeping it as it is (run setup with --no-home)")


def wifi_owned_by_setup(tv):
    """True when a saved Wi-Fi network was created by the setup wizard (dumpsys wifi: creatorName / cname)."""
    out = tv.shell("dumpsys wifi | grep -oE '(cname|creatorName|mCreatorName)=[^ ,]*'")
    return SETUP_APP in out


def wait_for(tv, what, seconds=24):
    for _ in range(seconds // 3):
        tv.shell("input keyevent KEYCODE_HOME")
        time.sleep(3)
        if what in tv.resumed():
            return True
    return False


def switch_home(tv):
    """Luban's home becomes Home. The stock home is disabled only after Luban's has opened, and put back at once if
    anything goes wrong. A safety switch on the TV covers the few seconds in between."""
    st = load_state(tv)
    stock = st.get("stock_home") or stock_home(tv)
    st["stock_home"] = stock
    save_state(tv, st)
    stock_pkg = stock.split("/")[0]

    def back_to_stock(reason):
        for cmd in (f"pm enable {stock_pkg}", f"pm enable {SETUP_APP}",
                    f"cmd role add-role-holder --user 0 android.app.role.HOME {stock_pkg}",
                    f"cmd package set-home-activity --user 0 {stock}", "touch /data/local/tmp/luban_home_ok",
                    "input keyevent KEYCODE_HOME"):
            tv.shell(cmd)
        raise Stop(reason + " The TV's own home is back; nothing else changed.")

    tv.push_bytes(DEADMAN.encode(), "/data/local/tmp/luban_deadman.sh")
    tv.shell("rm -f /data/local/tmp/luban_home_ok")
    tv.shell(f"nohup sh /data/local/tmp/luban_deadman.sh {stock} 180 > /dev/null 2>&1 &")

    # 1. make Luban the home while the stock one is still installed and enabled
    tv.shell("cmd role add-role-holder --user 0 android.app.role.HOME om.luban.home")
    tv.shell("cmd package set-home-activity --user 0 om.luban.home/.HomeActivity")
    if not wait_for(tv, "om.luban.home"):
        back_to_stock("Luban's home did not open when Home was pressed (this firmware may pin its own home).")

    # 2. proven to work: now the stock home may step aside. The setup wizard (setupwraith) is left alone unless it
    #    is what takes Home instead: on many Google TV sets it owns the Wi-Fi network saved during first setup (Android
    #    deletes an app's saved networks when the app is disabled, so the TV goes offline for good) and it holds the
    #    Cast credentials (phones then call the TV "untrusted"). Field notes: github.com/Gathin23/cleanyourtv,
    #    docs/TROUBLESHOOTING.md.
    tv.shell(f"pm disable-user --user 0 {stock_pkg}")
    if not wait_for(tv, "om.luban.home"):
        if SETUP_APP in tv.packages() and SETUP_APP in tv.resumed():
            if wifi_owned_by_setup(tv):
                back_to_stock("The TV's setup app takes Home, and it also owns the saved Wi-Fi network: turning it off "
                              "would take the TV offline for good. On the TV: Settings → Network → forget the network and "
                              "join it again (it then belongs to the system), or use Ethernet; then run `home` again.")
            say("   ! The setup app (setupwraith) keeps taking Home; turning it off. On some Google TV sets casting from a"
                " phone then says \"untrusted device\"; `reset` turns it back on.")
            tv.shell(f"pm disable-user --user 0 {SETUP_APP}")
            st["setup_off"] = True
            save_state(tv, st)
            if not wait_for(tv, "om.luban.home"):
                back_to_stock("Home stopped opening Luban after the stock home was turned off.")
        else:
            back_to_stock("Home stopped opening Luban after the stock home was turned off.")
    if "om.luban.home" not in tv.home_resolves_to()[0]:
        back_to_stock("Android does not list Luban as the home app.")
    tv.shell("touch /data/local/tmp/luban_home_ok")
    say("   ✓ Luban is Home (the stock home is off, not removed: `reset` brings it back)")


def uad_ratings():
    try:
        return json.loads(download(UAD, 30_000_000))
    except Exception as e:  # noqa: BLE001
        raise Stop(f"could not read UAD-ng's ratings ({e}); debloat needs them to stay safe. Try again later.")


def read_list(name):
    return [l.split()[0] for l in (ROOT / "tools/lists" / name).read_text().splitlines() if l.strip() and not l.lstrip().startswith("#")]


def debloat(tv):
    keep = set(read_list("keep.txt"))
    wanted = [p for p in read_list("safe.txt") if p not in keep]
    ratings = uad_ratings()
    unsafe = [p for p in wanted if ratings.get(p, {}).get("removal") == "Unsafe"]
    if unsafe:
        raise Stop(f"UAD-ng now rates these Unsafe: {', '.join(unsafe)}. Take them out of tools/lists/safe.txt first.")
    present = tv.packages()
    st = load_state(tv)
    done = []
    for p in wanted:
        if p in present:
            out = tv.shell(f"pm disable-user --user 0 {p}")
            if "new state: disabled" in out:
                done.append(p)
    st["disabled"] = sorted(set(st["disabled"]) | set(done))
    save_state(tv, st)
    say(f"   ✓ {len(done)} ads/telemetry packages off (reversible): {', '.join(done) or 'none on this TV'}")


def tweaks(tv, on=True):
    v = "0.5" if on else "1.0"
    for k in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        tv.shell(f"settings put global {k} {v}")
    tv.shell(f"settings put global cached_apps_freezer {'enabled' if on else 'device_default'}")
    say(f"   ✓ animations at {v}x, cached-app freezer {'on' if on else 'default'}")


def settings(tv, args):
    raw = tv.broadcast("om.luban.screen/.AdbReceiver", "om.luban.screen.GET")
    try:
        s = json.loads(raw or "{}")
    except json.JSONDecodeError:
        raise Stop(f"could not read the settings: {raw[:200]}")
    cities = json.loads((ROOT / "content/cities.json").read_text(encoding="utf-8"))
    if args.city:
        if args.city not in {c["id"] for c in cities["cities"]}:
            raise Stop(f"unknown city {args.city!r}; see `luban.py cities`")
        s["city"] = args.city
    for k in ("lang", "panel", "method", "digits"):
        if getattr(args, k):
            s[k] = getattr(args, k)
    if args.home_card:
        s["homeCard"] = args.home_card == "on"
    if args.teams is not None:
        s["teams"] = [{"id": t.split(":")[0], "en": (t.split(":", 1) + [""])[1], "ar": ""} for t in args.teams.split(",") if t]
    if args.api is not None:
        s["api"] = args.api
    b64 = base64.b64encode(json.dumps(s, ensure_ascii=False).encode()).decode()
    out = tv.broadcast("om.luban.screen/.AdbReceiver", "om.luban.screen.SET", f"--es b64 {b64}")
    if out.strip() != "saved":
        raise Stop(f"not saved: {out}")
    say(json.dumps(s, ensure_ascii=False, indent=1))


def reset(tv):
    st = load_state(tv)
    stock = st.get("stock_home")
    for p in sorted(set(st.get("disabled", [])) | set(read_list("safe.txt"))):
        tv.shell(f"pm enable {p}")
    if stock:
        pkg = stock.split("/")[0]
        for cmd in (f"pm enable {pkg}", f"pm enable {SETUP_APP}", f"cmd role add-role-holder --user 0 android.app.role.HOME {pkg}",
                    f"cmd package set-home-activity --user 0 {stock}", "input keyevent KEYCODE_HOME"):
            tv.shell(cmd)
        if not wait_for(tv, pkg):
            raise Stop("the stock home did not come up: press Home on the remote, then run reset again. Nothing was removed yet.")
    for cmd in ("settings delete secure screensaver_components", "settings delete secure screensaver_default_component"):
        tv.shell(cmd)
    tweaks(tv, on=False)
    for _, pkg in APKS.values():
        tv.shell(f"pm uninstall {pkg}")
    state_file(tv).unlink(missing_ok=True)
    say("   ✓ back to how it came: our apps removed, everything we turned off is on again")


def adb_off(tv):
    say("   turning ADB (USB and network debugging) off; this ends the connection")
    try:
        tv.shell("settings put global adb_wifi_enabled 0; settings put global adb_enabled 0", tries=1, timeout=10)
    except Stop:
        pass   # the connection closing is the expected result
    say("   ✓ ADB is off. To use this tool again: Developer options → USB debugging on.")


def find():
    """TVs on this computer's networks with ADB open (port 5555)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("192.0.2.1", 9))   # no packet is sent: this only picks the local address of the default route
        me = s.getsockname()[0]
    except OSError:
        raise Stop("this computer has no network")
    finally:
        s.close()
    if not ipaddress.ip_address(me).is_private:
        raise Stop(f"this computer ({me}) is not on a home network: run this on a computer on the TV's Wi-Fi")
    net = ipaddress.ip_network(me + "/24", strict=False)

    def probe(ip):
        with socket.socket() as c:
            c.settimeout(0.6)
            return str(ip) if c.connect_ex((str(ip), 5555)) == 0 else None

    with ThreadPoolExecutor(64) as ex:
        hits = [h for h in ex.map(probe, net.hosts()) if h]
    if not hits:
        raise Stop(f"no TV with ADB open on {net}: is USB debugging on, the TV awake, and on the same Wi-Fi (not a guest network)?")
    for h in hits:
        say(h)
    return hits


def main(argv=None):
    ap = argparse.ArgumentParser(description="Set up a TV with Luban.")
    ap.add_argument("--host", default=os.environ.get("LUBAN_TV"))
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("find")
    sub.add_parser("cities")
    p = sub.add_parser("setup")
    p.add_argument("--no-home", action="store_true", help="keep the TV's own home screen")
    p.add_argument("--no-debloat", action="store_true")
    p.add_argument("--keep-adb", action="store_true", help="do not offer to turn ADB off at the end")
    p.add_argument("--tag", default="apk-latest")
    p.add_argument("--force-reinstall", action="store_true")
    p = sub.add_parser("install")
    p.add_argument("which", choices=["screen", "home", "all"])
    p.add_argument("--tag", default="apk-latest")
    p.add_argument("--force-reinstall", action="store_true")
    for name in ("home", "debloat", "status", "update", "reset", "adb-off"):
        sub.add_parser(name)
    p = sub.add_parser("settings")
    p.add_argument("--city"); p.add_argument("--lang", choices=["ar", "en"]); p.add_argument("--digits", choices=["latn", "arab"])
    p.add_argument("--panel", choices=["verse", "prayers", "forecast", "matches", "shows", "none"]); p.add_argument("--method")
    p.add_argument("--home-card", choices=["on", "off"]); p.add_argument("--teams", help="id:English name,..."); p.add_argument("--api")
    p = sub.add_parser("favourites")
    p.add_argument("apps", nargs="?", help="com.a,com.b (omit to list)")
    a = ap.parse_args(argv)

    try:
        if a.cmd == "find":
            find(); return 0
        if a.cmd == "cities":
            c = json.loads((ROOT / "content/cities.json").read_text(encoding="utf-8"))
            for x in c["cities"]:
                say(f"{x['id']:<14} {x['en']} · {x['ar']} ({c['countries'][x['c']]['en']})")
            return 0
        host = a.host or (find()[0])
        tv = Tv(host).connect()
        HOME_DIR.mkdir(parents=True, exist_ok=True)
        if a.cmd == "setup":
            say("▶ 1/5 apps"); install(tv, "screen", a.tag, a.force_reinstall); install(tv, "home", a.tag, a.force_reinstall)
            say("▶ 2/5 screensaver"); screensaver_on(tv)
            if not a.no_home:
                say("▶ 3/5 home screen"); switch_home(tv)
            if not a.no_debloat:
                say("▶ 4/5 ads and telemetry off"); debloat(tv)
            say("▶ 5/5 speed"); tweaks(tv)
            tv.shell("am start -n om.luban.screen/.SettingsActivity")
            say("\nDone. The TV now shows a QR code: scan it with your phone to choose your city and teams.")
            if not a.keep_adb:
                ans = input("Turn ADB off now? It is safer; turn it on again only to use this tool. [Y/n] ").strip().lower()
                if ans in ("", "y", "yes", "نعم"):
                    adb_off(tv)
        elif a.cmd == "install":
            for w in (["screen", "home"] if a.which == "all" else [a.which]):
                install(tv, w, a.tag, a.force_reinstall)
        elif a.cmd == "home":
            switch_home(tv)
        elif a.cmd == "debloat":
            debloat(tv)
        elif a.cmd == "settings":
            settings(tv, a)
        elif a.cmd == "favourites":
            if a.apps and not re.fullmatch(r"[A-Za-z0-9_.]+(,[A-Za-z0-9_.]+)*", a.apps):
                raise Stop("favourites: package names separated by commas, e.g. com.google.android.youtube.tv,com.netflix.ninja")
            extra = f"--es apps {a.apps}" if a.apps else ""
            say(tv.broadcast("om.luban.home/.AdbReceiver", "om.luban.home.SET" if a.apps else "om.luban.home.GET", extra))
        elif a.cmd == "update":
            say(tv.broadcast("om.luban.screen/.AdbReceiver", "om.luban.screen.UPDATE"))
        elif a.cmd == "status":
            for pkg in ("om.luban.screen", "om.luban.home"):
                v = re.search(r"versionName=(\S+)", tv.shell(f"dumpsys package {pkg}"))
                say(f"{pkg}: {v.group(1) if v else 'not installed'}")
            say("screensaver: " + tv.shell("settings get secure screensaver_components").strip())
            say("home: " + tv.home_resolves_to()[0])
            say("disabled by Luban: " + ", ".join(load_state(tv).get("disabled", [])))
        elif a.cmd == "reset":
            reset(tv)
        elif a.cmd == "adb-off":
            adb_off(tv)
        return 0
    except Stop as e:
        say(f"!! {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
