package om.luban.home;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/** Colours, fonts and the focus look shared by every part of the home screen. */
final class Ui {
    static final int LUBAN = 0xFFE3B062, LUBAN_SOFT = 0xFFF2D6A2, INK = 0xFFF7F4EE, INK2 = 0xC7F7F4EE, INK3 = 0x8CF7F4EE;
    static final int GLASS = 0x8C0E1418, LINE = 0x24F7F4EE;
    private static Typeface regular, medium, bold, heavy;

    private Ui() {}

    static void load(Context c) {
        if (regular != null) return;
        regular = font(c, "Regular");
        medium = font(c, "Medium");
        bold = font(c, "Bold");
        heavy = font(c, "ExtraBold");
    }

    private static Typeface font(Context c, String w) {
        try { return Typeface.createFromAsset(c.getAssets(), "native/Tajawal-" + w + ".ttf"); }
        catch (Exception e) { return Typeface.DEFAULT; }
    }

    static Typeface regular() { return regular; }
    static Typeface medium() { return medium; }
    static Typeface bold() { return bold; }
    static Typeface heavy() { return heavy; }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static TextView text(Context c, String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static GradientDrawable glass(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(GLASS);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 1), LINE);
        return g;
    }

    /** Focused: amber ring and a small lift. Every focusable thing on the home screen uses it. */
    static void focusable(View v, GradientDrawable bg) {
        Context c = v.getContext();
        v.setFocusable(true);
        v.setFocusableInTouchMode(false);
        v.setBackground(bg);
        v.setOnFocusChangeListener((view, has) -> {
            bg.setStroke(dp(c, has ? 3 : 1), has ? LUBAN : LINE);
            view.animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f).translationZ(has ? dp(c, 8) : 0).setDuration(140).start();
        });
    }

    /** 0-9 or ٠-٩, as the screensaver's setting says. */
    static String digits(String s, boolean arabic) {
        if (!arabic) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('٠' + (ch - '0')) : ch);
        return b.toString();
    }
}
