package om.luban.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The flag of Oman in miniature (1:2): a red band at the hoist, a quarter of the length, then white, red and green
 * stripes of equal height (colours: FOTW, Pantone 186 / 347). The emblem is left out at this size. Never mirrored.
 */
final class OmanFlag extends Drawable {
    static final int RED = 0xFFC8102E, WHITE = 0xFFFFFFFF, GREEN = 0xFF009A44;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float w = b.width(), h = b.height(), x = b.left, y = b.top, hoist = w / 4f;
        p.setColor(WHITE); c.drawRect(x + hoist, y, x + w, y + h / 3f, p);
        p.setColor(RED);   c.drawRect(x + hoist, y + h / 3f, x + w, y + 2 * h / 3f, p);
        p.setColor(GREEN); c.drawRect(x + hoist, y + 2 * h / 3f, x + w, y + h, p);
        p.setColor(RED);   c.drawRect(x, y, x + hoist, y + h, p);
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
