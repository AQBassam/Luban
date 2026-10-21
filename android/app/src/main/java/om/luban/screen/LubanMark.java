package om.luban.screen;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Luban's mark: the rim of the navigators' compass, the part of the day already lived in resin, and a lit grain of
 * frankincense where "now" is. The same drawing as the app icon and the screensaver's dial.
 */
final class LubanMark extends Drawable {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), lived = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG), grain = new Paint(Paint.ANTI_ALIAS_FLAG);

    LubanMark() {
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0x59F1EBDD);
        lived.setStyle(Paint.Style.STROKE);
        lived.setColor(0xFFE2A94F);
        lived.setStrokeCap(Paint.Cap.ROUND);
        grain.setColor(0xFFF7E7C4);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()), cx = b.exactCenterX(), cy = b.exactCenterY(), r = s * .38f;
        ring.setStrokeWidth(s * .03f);
        lived.setStrokeWidth(s * .09f);
        c.drawCircle(cx, cy, r, ring);
        // from Fajr (lower right in Arabic reading) round through noon to now (upper left)
        c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 45, -225, false, lived);
        float ex = cx + r * (float) Math.cos(Math.toRadians(-180)), ey = cy + r * (float) Math.sin(Math.toRadians(-180));
        glow.setShader(new RadialGradient(ex, ey, s * .16f, new int[] {0xF2FFB547, 0x73E2762A, 0x00E2762A}, new float[] {0, .35f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(ex, ey, s * .16f, glow);
        c.drawCircle(ex, ey, s * .07f, grain);
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
