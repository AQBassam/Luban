"""tools/luban.py against a fake TV: the order of the home switch, rollback, no silent uninstall, the APK pin."""
import hashlib
import struct
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import luban  # noqa: E402

luban.time.sleep = lambda s: None


class FakeTv:
    """Records every command. `home_works` decides whether pressing Home shows Luban's home."""

    def __init__(self, home_works=True, works_after_disable=True, install_out="Success"):
        self.log, self.host = [], "10.0.0.9"
        self.home_works, self.works_after_disable, self.install_out = home_works, works_after_disable, install_out
        self.disabled, self.role = set(), "com.google.android.apps.tv.launcherx"

    def shell(self, cmd, timeout=60, tries=3):
        self.log.append(cmd)
        if cmd.startswith("pm list packages"):
            return "package:com.google.android.apps.tv.launcherx\npackage:com.tcl.bi\npackage:com.google.android.feedback\n"
        if cmd.startswith("pm disable-user"):
            self.disabled.add(cmd.split()[-1]); return f"Package {cmd.split()[-1]} new state: disabled-user"
        if cmd.startswith("pm enable"):
            self.disabled.discard(cmd.split()[-1]); return "new state: enabled"
        if "add-role-holder" in cmd:
            self.role = cmd.split()[-1]; return ""
        if cmd.startswith("dumpsys activity"):
            stock_off = "com.google.android.apps.tv.launcherx" in self.disabled
            ours = self.role == "om.luban.home" and self.home_works and (self.works_after_disable or not stock_off)
            return "mResumedActivity: om.luban.home/.HomeActivity" if ours else "mResumedActivity: com.google.android.apps.tv.launcherx/.Home"
        if cmd.startswith("cmd package resolve-activity"):
            return "priority=0\n" + (self.role + "/.X")
        if cmd.startswith("pm install"):
            return self.install_out
        if cmd.startswith("getprop ro.serialno"):
            return "TEST123"
        return ""

    def push_bytes(self, data, remote):
        self.log.append(f"push {remote}")

    packages = luban.Tv.packages
    resumed = luban.Tv.resumed
    home_resolves_to = luban.Tv.home_resolves_to


def first(log, prefix):
    return next(i for i, c in enumerate(log) if c.startswith(prefix))


class HomeSwitch(unittest.TestCase):
    def setUp(self):
        luban.HOME_DIR = Path(tempfile.mkdtemp())

    def test_stock_disabled_only_after_luban_opened(self):
        tv = FakeTv()
        luban.switch_home(tv)
        opened = next(i for i, c in enumerate(tv.log) if c.startswith("dumpsys activity"))
        self.assertLess(first(tv.log, "cmd role add-role-holder"), first(tv.log, "pm disable-user"))
        self.assertLess(opened, first(tv.log, "pm disable-user"), "verified before anything is disabled")
        self.assertIn("com.google.android.apps.tv.launcherx", tv.disabled)
        self.assertIn("touch /data/local/tmp/luban_home_ok", tv.log)

    def test_firmware_that_pins_home_is_left_untouched(self):
        tv = FakeTv(home_works=False)
        with self.assertRaises(luban.Stop):
            luban.switch_home(tv)
        self.assertFalse([c for c in tv.log if c.startswith("pm disable-user")], "nothing disabled")
        self.assertEqual(tv.role, "com.google.android.apps.tv.launcherx", "home role given back")

    def test_rollback_when_it_breaks_after_disabling(self):
        tv = FakeTv(works_after_disable=False)
        with self.assertRaises(luban.Stop):
            luban.switch_home(tv)
        self.assertNotIn("com.google.android.apps.tv.launcherx", tv.disabled, "stock home enabled again")
        self.assertEqual(tv.role, "com.google.android.apps.tv.launcherx")


class Install(unittest.TestCase):
    def test_signature_clash_never_uninstalls(self):
        luban.HOME_DIR = Path(tempfile.mkdtemp())
        tv = FakeTv(install_out="Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match]")
        with mock.patch.object(luban, "fetch_apk", return_value=b"apk"):
            with self.assertRaises(luban.Stop) as e:
                luban.install(tv, "screen")
        self.assertIn("--force-reinstall", str(e.exception))
        self.assertFalse([c for c in tv.log if c.startswith("pm uninstall")])


def fake_apk(cert):
    """A minimal zip with an APK Signing Block holding one v2 signer with this certificate."""
    lp = lambda b: struct.pack("<I", len(b)) + b
    signed = lp(b"") + lp(lp(cert)) + lp(b"")
    signer = lp(signed) + lp(b"") + lp(b"")
    value = lp(lp(signer))
    pair = struct.pack("<QI", 4 + len(value), 0x7109871A) + value
    size = len(pair) + 8 + 16
    block = struct.pack("<Q", size) + pair + struct.pack("<Q", size) + b"APK Sig Block 42"
    body = b"PK\x03\x04" + b"\0" * 26
    cd_offset = len(body) + len(block)
    eocd = b"PK\x05\x06" + b"\0" * 12 + struct.pack("<I", cd_offset) + b"\0\0"
    return body + block + eocd


class ApkPin(unittest.TestCase):
    def test_reads_the_signer_certificate(self):
        cert = b"\x30\x82fake-der-certificate"
        self.assertEqual(luban.apk_signer_sha256(fake_apk(cert)), hashlib.sha256(cert).hexdigest())

    def test_unsigned_is_refused(self):
        with self.assertRaises(luban.Stop):
            luban.apk_signer_sha256(b"PK\x03\x04" + b"\0" * 40 + b"PK\x05\x06" + b"\0" * 18)

    def test_pin_file_is_a_sha256(self):
        self.assertRegex(luban.pinned_cert(), r"^[0-9a-f]{64}$")


class Lists(unittest.TestCase):
    def test_keep_wins_and_no_overlap(self):
        self.assertFalse(set(luban.read_list("safe.txt")) & set(luban.read_list("keep.txt")))


if __name__ == "__main__":
    unittest.main(verbosity=1)
