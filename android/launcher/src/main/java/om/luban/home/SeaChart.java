package om.luban.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The home screen's background, the same sea chart as the screensaver: the Indian Ocean at depth, a navigator's rose
 * of 32 rhumb lines running off the screen, and engraved waves rising from the bottom. Drawn once per size.
 */
final class SeaChart extends Drawable {
    private final Paint fill = new Paint(), line = new Paint(Paint.ANTI_ALIAS_FLAG), wave = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path waves = new Path();
    private final float fx, fy;   // where the rose is, as fractions of the width and height

    SeaChart() { this(.28f, .45f); }

    SeaChart(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
        line.setStyle(Paint.Style.STROKE);
        wave.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        fill.setShader(new RadialGradient(b.width() * fx, b.height() * fy, Math.max(b.width(), b.height()) * .75f,
            Ui.SEA, Ui.DEEP, Shader.TileMode.CLAMP));
        waves.reset();
        float step = b.width() / 16f, amp = b.height() / 160f;
        for (float y = b.height() * .72f; y < b.height(); y += amp * 4.5f) {
            waves.moveTo(0, y);
            int k = 0;
            for (float x = 0; x < b.width(); x += step / 2, k++) waves.rQuadTo(step / 4, k % 2 == 0 ? -amp : amp, step / 2, 0);
        }
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, fill);
        float cx = b.width() * fx, cy = b.height() * fy, len = b.width() + b.height();
        line.setStrokeWidth(Math.max(1, b.width() / 1600f));
        for (int i = 0; i < 32; i++) {
            double a = i / 32.0 * 2 * Math.PI;
            line.setColor(i % 4 == 0 ? 0x2BE2A94F : 0x14E2A94F);
            c.drawLine(cx, cy, cx + len * (float) Math.sin(a), cy - len * (float) Math.cos(a), line);
        }
        wave.setStrokeWidth(Math.max(1, b.width() / 1300f));
        wave.setColor(0x148FE0D6);
        c.drawPath(waves, wave);
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
