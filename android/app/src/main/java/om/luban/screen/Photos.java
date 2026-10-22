package om.luban.screen;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background photos: the first few ship in the APK, the rest are downloaded from the repository once and kept.
 * One lock per file name, so a slow download never holds up the others.
 */
final class Photos {
    private static final int KEEP = 16;
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private Photos() {}

    static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{6,20}\\.jpg");
    }

    /** @return a stream of the photo, or null */
    static InputStream open(Context c, String name) throws IOException {
        if (!validName(name)) return null;
        try {
            return c.getAssets().open("photos/" + name);
        } catch (IOException notBundled) {
            File f = cached(c, name);
            return f == null ? null : new java.io.FileInputStream(f);
        }
    }

    /** The downloaded copy of a photo not in the APK (fetched once if need be), or null: what ConfigProvider hands out. */
    static File file(Context c, String name) throws IOException {
        return validName(name) ? cached(c, name) : null;
    }

    private static File cached(Context c, String name) throws IOException {
        File dir = new File(c.getFilesDir(), "photos"), f = new File(dir, name);
        Object lock = LOCKS.computeIfAbsent(name, k -> new Object());
        synchronized (lock) {
            if (f.isFile() && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return f; }
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            byte[] data = Net.get("https://raw.githubusercontent.com/" + BuildConfig.REPO + "/main/photos/" + name, 3 * 1024 * 1024);
            if (data.length < 3 || (data[0] & 0xff) != 0xFF || (data[1] & 0xff) != 0xD8) return null;   // JPEG only
            File tmp = new File(dir, name + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(data); }
            if (!tmp.renameTo(f)) { tmp.delete(); return null; }
        }
        prune(dir);
        return f;
    }

    private static void prune(File dir) {
        File[] all = dir.listFiles((d, n) -> n.endsWith(".jpg"));
        if (all == null || all.length <= KEEP) return;
        Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = KEEP; i < all.length; i++) //noinspection ResultOfMethodCallIgnored
            all[i].delete();
    }
}
