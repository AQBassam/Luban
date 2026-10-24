package om.luban.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The prayer card's dial, a small twin of the screensaver's: a 24-hour compass with noon at the top, today's prayers as
 * marks on the rim, the day lived since Fajr in resin, and a lit grain of frankincense where "now" is.
 * Time runs clockwise in both languages, as on the screensaver: sunrise on the left, sunset on the right.
 */
final class Dial extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG),
        mark = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG), grain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private double[] times;   // fajr, sunrise, dhuhr, asr, maghrib, isha in minutes (Maghrib/Isha may be past 1440)
    private double now;
    private int next = -1;

    Dial(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        arc.setStyle(Paint.Style.STROKE);
        mark.setStrokeCap(Paint.Cap.ROUND);
        grain.setColor(0xFFF7E7C4);
    }

    void set(double[] times, double now, int next, boolean rtl) {
        this.times = times; this.now = now; this.next = next;   // rtl: the dial runs clockwise in both languages
        invalidate();
    }

    /** Canvas degrees (0 = 3 o'clock, clockwise) for a minute of the day: noon at the top, time running clockwise. */
    private float deg(double min) {
        return (float) (-90 + (min - 720) / 1440 * 360);
    }

    private float x(double min, float r) { return getWidth() / 2f + r * (float) Math.cos(Math.toRadians(deg(min))); }
    private float y(double min, float r) { return getHeight() / 2f + r * (float) Math.sin(Math.toRadians(deg(min))); }

    /** The sweep from t0 to t1, in the direction time runs. */
    private void sweep(Canvas c, double t0, double t1, Paint p) {
        float span = (float) ((t1 - t0) / 1440 * 360);
        c.drawArc(box, deg(t0), span, false, p);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (times == null) return;
        float s = Math.min(getWidth(), getHeight()), r = s * .40f, band = s * .07f;
        box.set(getWidth() / 2f - r, getHeight() / 2f - r, getWidth() / 2f + r, getHeight() / 2f + r);
        ring.setStrokeWidth(Math.max(1, s / 160));
        ring.setColor(0x80E2A94F);
        c.drawCircle(getWidth() / 2f, getHeight() / 2f, r + band / 2, ring);
        mark.setStrokeWidth(Math.max(1, s / 200));
        mark.setColor(0x99E2A94F);
        for (int hr = 0; hr < 24; hr++) c.drawLine(x(hr * 60, r + band / 2), y(hr * 60, r + band / 2), x(hr * 60, r + band / 2 - s * .04f), y(hr * 60, r + band / 2 - s * .04f), mark);
        arc.setStrokeWidth(band);
        arc.setColor(0x33E2A94F);
        sweep(c, times[1], times[4], arc);   // daylight
        if (now > times[0]) { arc.setColor(0x8CE2A94F); sweep(c, times[0], Math.min(now, times[0] + 1439), arc); }
        mark.setStrokeWidth(s / 70);
        for (int i = 0; i < 6; i++) {
            mark.setColor(i == next ? Ui.FOAM : Ui.INK);
            c.drawLine(x(times[i], r + band / 2 + s * .02f), y(times[i], r + band / 2 + s * .02f), x(times[i], r - band / 2 - s * .03f), y(times[i], r - band / 2 - s * .03f), mark);
        }
        float ex = x(now, r), ey = y(now, r), g = s * .14f;
        glow.setShader(new RadialGradient(ex, ey, g, new int[] {0xF2FFB547, 0x73E2762A, 0x00E2762A}, new float[] {0, .35f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(ex, ey, g, glow);
        c.drawCircle(ex, ey, s * .045f, grain);
    }
}
