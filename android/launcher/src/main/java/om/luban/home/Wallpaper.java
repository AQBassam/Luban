package om.luban.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.InputStream;

/** Omani photos behind the home screen, a new one every 15 minutes, crossfaded. Decoded off the main thread. */
final class Wallpaper {
    private static final long EVERY_MS = 15 * 60 * 1000L;
    private final Context c;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ImageView front, back;
    private String[] names = new String[0];
    private int index = -1;
    private long shownAt;
    private final Runnable next = this::next;

    Wallpaper(Context c, FrameLayout root) {
        this.c = c;
        front = image(root);
        back = image(root);
        try {
            String[] list = c.getAssets().list("wallpapers");
            if (list != null) names = list;
        } catch (Exception ignored) { }
        index = c.getSharedPreferences("home", Context.MODE_PRIVATE).getInt("wallpaper", -1);
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

    private void next() {
        main.removeCallbacks(next);
        main.postDelayed(next, EVERY_MS);
        if (names.length == 0) return;
        index = (index + 1) % names.length;
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putInt("wallpaper", index).apply();
        shownAt = SystemClock.elapsedRealtime();
        final String name = names[index];
        new Thread(() -> {
            Bitmap bm = null;
            try (InputStream in = c.getAssets().open("wallpapers/" + name)) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                bm = BitmapFactory.decodeStream(in, null, o);
            } catch (Exception ignored) { }
            final Bitmap b = bm;
            main.post(() -> show(b));
        }).start();
    }

    private void show(Bitmap bm) {
        if (bm == null) return;
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
