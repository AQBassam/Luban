package om.luban.screen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The settings, stored as one JSON object. The shape and every value are checked by sanitize() in web/js/settings.js,
 * which every reader runs; here we only refuse what is not a JSON object or is too large to be settings.
 */
final class Store {
    static final int MAX_BYTES = 32 * 1024;
    private static final Set<Runnable> LISTENERS = new CopyOnWriteArraySet<>();

    private Store() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("luban", Context.MODE_PRIVATE);
    }

    static String settingsJson(Context c) {
        return sp(c).getString("settings", "{}");
    }

    static JSONObject settings(Context c) {
        try { return new JSONObject(settingsJson(c)); } catch (Exception e) { return new JSONObject(); }
    }

    /** @return null when saved, else why not */
    static String save(Context c, String json) {
        if (json == null || json.getBytes().length > MAX_BYTES) return "too large";
        try {
            JSONObject o = new JSONObject(json);
            sp(c).edit().putString("settings", o.toString()).apply();
        } catch (Exception e) {
            return "not a JSON object";
        }
        new Handler(Looper.getMainLooper()).post(() -> { for (Runnable r : LISTENERS) r.run(); });
        return null;
    }

    /** Running screens reload when the settings change. */
    static void listen(Runnable r) { LISTENERS.add(r); }
    static void unlisten(Runnable r) { LISTENERS.remove(r); }

    static SharedPreferences raw(Context c) { return sp(c); }
}
