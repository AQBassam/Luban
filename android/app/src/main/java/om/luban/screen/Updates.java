package om.luban.screen;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Signed over-the-air updates of the screen page. A bundle is used only when Verifier accepts it: signed with the key
 * built into this APK, byte-for-byte the signed bundle, and newer than both the APK's own page and the last update.
 * Canary: one TV in ten (by a stable per-install number), or any TV whose owner picked it, takes screen-canary first.
 */
@android.annotation.SuppressLint("ApplySharedPref")
final class Updates {
    static final long INTERVAL_MS = 6L * 3600 * 1000;
    private static final String[] FILES = {"index.html", "settings.html"};

    private Updates() {}

    /** The version of the page in use: the APK's own, or a newer verified update. */
    static int activeVersion(Context c) {
        int v = Store.raw(c).getInt("bundleVersion", 0);
        return v > BuildConfig.SCREEN_VERSION && new File(dir(c, v), "index.html").isFile() ? v : BuildConfig.SCREEN_VERSION;
    }

    /** Where the page's files come from: a verified update folder, or null for the APK's assets. */
    static File activeDir(Context c) {
        int v = activeVersion(c);
        return v > BuildConfig.SCREEN_VERSION ? dir(c, v) : null;
    }

    private static File dir(Context c, int version) { return new File(c.getFilesDir(), "screen-" + version); }

    static String channel(Context c) {
        JSONObject s = Store.settings(c);
        if ("canary".equals(s.optString("channel"))) return "canary";
        // a random number drawn once per install, not a device identifier
        SharedPreferences sp = Store.raw(c);
        int bucket = sp.getInt("bucket", -1);
        if (bucket < 0) {
            bucket = new java.security.SecureRandom().nextInt(10);
            sp.edit().putInt("bucket", bucket).apply();
        }
        return bucket == 0 ? "canary" : "stable";
    }

    /** Blocking; off the main thread. Returns a short status for the settings screen. */
    static synchronized String check(Context c, boolean force) {
        SharedPreferences sp = Store.raw(c);
        long now = System.currentTimeMillis();
        if (!force && now - sp.getLong("lastCheck", 0) < INTERVAL_MS) return "checked recently";
        String base = "https://github.com/" + BuildConfig.REPO + "/releases/download/screen-" + channel(c) + "/";
        try {
            byte[] manifest = Net.get(base + "manifest.json", 4096);
            byte[] sig = Net.get(base + "manifest.sig", 4096);
            int running = activeVersion(c);
            Verifier.Manifest m = Verifier.verify(manifest, sig, BuildConfig.UPDATE_KEY);
            sp.edit().putLong("lastCheck", now).apply();
            if (m.version <= running) return "up to date (" + running + ")";
            byte[] bundle = Net.get(base + "bundle.zip", 4 * 1024 * 1024);
            Verifier.check(manifest, sig, bundle, BuildConfig.UPDATE_KEY, running);
            install(c, m.version, bundle);
            return "updated to " + m.version;
        } catch (SecurityException e) {
            return "refused: " + e.getMessage();
        } catch (Net.HttpError e) {
            // 404: nothing published on this channel yet (stable exists only after a canary has run for 48 hours)
            return e.code == 404 ? "no update published on the " + channel(c) + " channel yet" : "update server answered HTTP " + e.code;
        } catch (Exception e) {
            return "no connection";
        }
    }

    private static void install(Context c, int version, byte[] bundle) throws IOException {
        File tmp = new File(c.getFilesDir(), "screen-tmp"), out = dir(c, version);
        deleteTree(tmp);
        if (!tmp.mkdirs()) throw new IOException("mkdir");
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(bundle))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String name = e.getName();
                boolean known = false;
                for (String f : FILES) known |= f.equals(name);
                if (!known) continue;   // only the expected names, never a path
                try (FileOutputStream fo = new FileOutputStream(new File(tmp, name))) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = z.read(buf)) > 0) fo.write(buf, 0, n);
                }
            }
        }
        if (!new File(tmp, "index.html").isFile()) throw new IOException("bundle without index.html");
        deleteTree(out);
        if (!tmp.renameTo(out)) throw new IOException("rename");
        int old = Store.raw(c).getInt("bundleVersion", 0);
        Store.raw(c).edit().putInt("bundleVersion", version).commit();   // commit, not apply: the switch must be on disk before the old folder goes
        if (old > 0 && old != version) deleteTree(dir(c, old));
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
