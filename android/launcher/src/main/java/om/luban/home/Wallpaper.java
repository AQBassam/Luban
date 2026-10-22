package om.luban.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.TimeZone;

/**
 * When the owner chose photos (phone settings → Background): Omani photos behind the home screen, the screensaver's four of
 * the day, a new one every 15 minutes, crossfaded. Decoded off the main thread.
 */
final class Wallpaper {
    private static final long EVERY_MS = 15 * 60 * 1000L;
    private final Context c;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ImageView front, back;
    private String[] names = new String[0];
    private JSONArray credits = new JSONArray();   // content/photos.json: where each photo is, who took it, its licence
    private String current;
    private int index = -1;
    private long shownAt;
    private final Runnable next = this::next;
    /** Runs when the photo on screen changes, or when the photos give way to the sea chart. */
    Runnable onChange = () -> { };

    Wallpaper(Context c, FrameLayout root) {
        this.c = c;
        front = image(root);
        back = image(root);
        try {
            String[] list = c.getAssets().list("wallpapers");
            if (list != null) names = list;
        } catch (Exception ignored) { }
        try (InputStream in = c.getAssets().open("content/photos.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            credits = new JSONArray(out.toString("UTF-8"));
        } catch (Exception ignored) { }
        index = c.getSharedPreferences("home", Context.MODE_PRIVATE).getInt("wallpaper", -1);
    }

    /** The photo on screen: its place, its photographer and licence (the licence asks for both); "" with the sea chart. */
    String caption(boolean ar) {
        if (current == null) return "";
        for (int i = 0; i < credits.length(); i++) {
            JSONObject p = credits.optJSONObject(i);
            if (p != null && current.equals(p.optString("f"))) {
                String by = p.optString("by"), lic = p.optString("licence");
                return p.optString(ar ? "place_ar" : "place_en") + " · " + (ar ? "تصوير " : "Photo ") + by + (lic.isEmpty() ? "" : ", " + lic);
            }
        }
        return "";
    }

    private ImageView image(FrameLayout root) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(v, 0, new FrameLayout.LayoutParams(-1, -1));
        return v;
    }

    void resume() {
        if (shownAt == 0 || SystemClock.elapsedRealtime() - shownAt >= EVERY_MS) next();
        else main.postDelayed(next, EVERY_MS - (SystemClock.elapsedRealtime() - shownAt));
    }

    void pause() { main.removeCallbacks(next); }

    /** The sea chart instead: no photos decoded at all. */
    void hide() {
        pause();
        front.setImageDrawable(null);
        back.setImageDrawable(null);
        shownAt = 0;
        if (current != null) { current = null; onChange.run(); }
    }

    private void next() {
        main.removeCallbacks(next);
        main.postDelayed(next, EVERY_MS);
        if (names.length == 0 && credits.length() == 0) return;
        index = (index + 1) & 3;
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putInt("wallpaper", index).apply();
        shownAt = SystemClock.elapsedRealtime();
        final String name = today(index), spare = names.length == 0 ? null : names[index % names.length];
        new Thread(() -> {
            String shown = name;
            Bitmap bm = decode(name);
            if (bm == null && spare != null) { shown = spare; bm = decode(spare); }   // not downloaded yet: one this APK carries
            final Bitmap b = bm;
            final String s = shown;
            main.post(() -> show(b, s));
        }).start();
    }

    /**
     * One of today's four photos, the screensaver's own (app.js applyPhoto: day × 4 + turn, over content/photos.json): those
     * not in this APK are then ones the screensaver has already downloaded, so a new day brings new photos at no extra cost.
     */
    private String today(int turn) {
        int n = credits.length();
        if (n == 0) return names[turn % names.length];
        long now = System.currentTimeMillis(), day = Math.floorDiv(now + TimeZone.getDefault().getOffset(now), 86_400_000L);
        JSONObject p = credits.optJSONObject((int) ((day * 4 + turn) % n));
        return p == null ? names[turn % Math.max(1, names.length)] : p.optString("f");
    }

    /** From this APK if it carries the photo, else the screensaver's copy (ConfigProvider, signature permission). */
    private Bitmap decode(String name) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream in = c.getAssets().open("wallpapers/" + name)) {
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception notBundled) {
            try (InputStream in = c.getContentResolver().openInputStream(Uri.parse("content://om.luban.screen.config/photos/" + name))) {
                return in == null ? null : BitmapFactory.decodeStream(in, null, o);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private void show(Bitmap bm, String name) {
        if (bm == null) return;
        current = name;
        onChange.run();
        ImageView old = front;
        front = back;
        back = old;
        front.setImageBitmap(bm);
        front.setAlpha(0f);
        front.animate().alpha(1f).setDuration(1800).withEndAction(() -> back.setImageDrawable(null)).start();
        // keep the photos under everything else
        FrameLayout root = (FrameLayout) front.getParent();
        root.removeView(front);
        root.addView(front, 1);
    }
}
