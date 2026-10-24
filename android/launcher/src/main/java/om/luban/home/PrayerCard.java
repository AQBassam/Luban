package om.luban.home;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.icu.util.IslamicCalendar;
import android.icu.util.ULocale;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Today's prayers under the date, in a glass panel: the six times in a row, the ones gone faded, the next one marked in
 * foam with a line under it, and under the row a countdown to its adhan (with the iqama's time). Once the adhan has come
 * the mark turns resin and the countdown is to the iqama; after the iqama the next prayer takes over. Off until it is
 * switched on in the screensaver's phone settings; the city, method and iqama minutes come from there (ConfigProvider,
 * signature permission).
 */
final class PrayerCard {
    private static final Uri CONFIG = Uri.parse("content://om.luban.screen.config");
    private static final String[] AR = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final String[] EN = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final int RULE = 0x24F1EBDD;
    private final Context c;
    final LinearLayout view;
    private final LinearLayout[] cells = new LinearLayout[6];
    private final TextView[] names = new TextView[6], times = new TextView[6];
    private final View[] marks = new View[6], rules = new View[5];
    private final GradientDrawable dot = new GradientDrawable();
    private final TextView count;
    private JSONObject cfg;
    private String computedFor = "";
    private double[] today, tomorrow;

    PrayerCard(Context c) {
        this.c = c;
        view = new LinearLayout(c);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(Ui.glass(c, 18));
        view.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 10));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 6; i++) {
            if (i > 0) {
                // a thin rule between two times (hidden beside the marked one, whose own background sets it apart)
                rules[i - 1] = new View(c);
                rules[i - 1].setBackgroundColor(RULE);
                row.addView(rules[i - 1], new LinearLayout.LayoutParams(Math.max(1, Ui.dp(c, 1)), Ui.dp(c, 26)));
            }
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
            names[i] = Ui.text(c, "", 12, Ui.medium(), Ui.INK2);
            cell.addView(names[i]);
            times[i] = Ui.text(c, "", 17, Ui.medium(), Ui.INK);
            times[i].setMaxLines(1);
            times[i].setAutoSizeTextTypeUniformWithConfiguration(11, 17, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            times[i].setGravity(Gravity.CENTER);
            cell.addView(times[i], new LinearLayout.LayoutParams(-1, Ui.dp(c, 26)));
            marks[i] = new View(c);   // the line under the marked time
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 2));
            ml.topMargin = Ui.dp(c, 3);
            cell.addView(marks[i], ml);
            cells[i] = cell;
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
            cl.setMargins(Ui.dp(c, 2), 0, Ui.dp(c, 2), 0);
            row.addView(cell, cl);
        }
        view.addView(row, new LinearLayout.LayoutParams(-1, -2));

        View line = new View(c);
        line.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(c, 1)));
        ll.setMargins(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8));
        view.addView(line, ll);

        LinearLayout foot = new LinearLayout(c);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPaddingRelative(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        dot.setShape(GradientDrawable.OVAL);
        View d = new View(c);
        d.setBackground(dot);
        foot.addView(d, new LinearLayout.LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7)));
        count = Ui.text(c, "", 15, Ui.regular(), Ui.INK);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.setMarginStart(Ui.dp(c, 9));
        foot.addView(count, tl);
        view.addView(foot);
        view.setVisibility(View.GONE);
    }

    /** The owner chose Omani photos behind the home screen instead of the sea chart. */
    boolean photos() { return cfg != null && "photos".equals(cfg.optString("bg")); }

    /** Re-read the settings (on resume). */
    void reload() {
        cfg = null;
        try (Cursor cur = c.getContentResolver().query(CONFIG, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) cfg = new JSONObject(cur.getString(0));
        } catch (Exception ignored) {
            // the screensaver app is missing, older, or signed with another key: no card
        }
        computedFor = "";
        tick();
    }

    /** Every 30 seconds while the home screen is visible. */
    void tick() {
        if (cfg == null || !cfg.optBoolean("homeCard", false)) { view.setVisibility(View.GONE); return; }
        view.setVisibility(View.VISIBLE);
        boolean ar = !"en".equals(cfg.optString("lang")), arab = "arab".equals(cfg.optString("digits"));
        TimeZone tz = TimeZone.getTimeZone(cfg.optString("tz", "Asia/Muscat"));
        Calendar now = Calendar.getInstance(tz);
        int y = now.get(Calendar.YEAR), m = now.get(Calendar.MONTH) + 1, d = now.get(Calendar.DAY_OF_MONTH);
        String key = y + "-" + m + "-" + d;
        if (!key.equals(computedFor)) {
            today = compute(y, m, d, tz, 0);
            Calendar t = (Calendar) now.clone();
            t.add(Calendar.DAY_OF_MONTH, 1);
            tomorrow = compute(t.get(Calendar.YEAR), t.get(Calendar.MONTH) + 1, t.get(Calendar.DAY_OF_MONTH), tz, 1);
            computedFor = key;
        }
        double mins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60.0;
        boolean friday = now.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
        // far north in summer Maghrib or Isha can fall after midnight: keep them after Dhuhr when comparing
        double[] order = today.clone();
        for (int i = 4; i < 6; i++) if (order[i] < order[2]) order[i] += 1440;

        // the adhan that has come last, if its iqama is still to come: the countdown is to the iqama
        int hot = -1;
        boolean iqama = false, tomorrowFajr = false;
        double target = 0;
        for (int i = 5; i >= 0; i--) {
            if (i == 1 || order[i] > mins) continue;
            int wait = iqamaMinutes(i, friday);
            if (wait > 0 && mins < order[i] + wait) { hot = i; iqama = true; target = order[i] + wait; }
            break;
        }
        if (hot < 0) {
            for (int i = 0; i < 6; i++) if (order[i] > mins) { hot = i; target = order[i]; break; }
            if (hot < 0) { hot = 0; tomorrowFajr = true; target = tomorrow[0] + 1440; }
        }
        int mark = iqama ? Ui.LUBAN : Ui.FOAM;

        for (int i = 0; i < 6; i++) {
            boolean on = i == hot, gone = tomorrowFajr ? i != 0 : i < hot;
            names[i].setText(name(i, friday, ar));
            times[i].setText(time(i == 0 && tomorrowFajr ? tomorrow[0] : today[i], ar, arab));
            names[i].setTextColor(on ? mark : Ui.INK2);
            times[i].setTextColor(on ? mark : Ui.INK);
            cells[i].setAlpha(gone ? .45f : 1f);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(c, 12));
            bg.setColor(on ? (mark & 0x00FFFFFF) | 0x1A000000 : 0);
            cells[i].setBackground(bg);
            marks[i].setBackgroundColor(mark);
            marks[i].setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        }
        for (int i = 0; i < 5; i++) rules[i].setVisibility(i == hot || i + 1 == hot ? View.INVISIBLE : View.VISIBLE);

        // the line under the row: to the adhan (with the iqama's time, which is not in the row), or to the iqama
        String n = name(hot, friday, ar), left = Ui.digits(left((long) Math.ceil(target - mins), ar), arab);
        SpannableStringBuilder s = new SpannableStringBuilder();
        int wait = iqamaMinutes(hot, friday);
        if (iqama) {
            s.append(ar ? "إقامة صلاة " : "");
            bold(s, n, mark);
            s.append(ar ? " " + left : " iqama " + left);
        } else if (hot == 1) {
            bold(s, n, mark);
            s.append(" ").append(left);
        } else {
            s.append(ar ? "أذان " : "");
            bold(s, n, mark);
            s.append(ar ? " " + left : " adhan " + left);
            if (wait > 0) {
                int at = s.length();
                double adhan = tomorrowFajr ? tomorrow[0] : today[hot];
                s.append(ar ? "  ·  الإقامة " : "  ·  iqama ").append(Ui.digits(clock(adhan + wait, ar), arab));
                s.setSpan(new ForegroundColorSpan(Ui.INK3), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new RelativeSizeSpan(.87f), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        count.setText(s);
        dot.setColor(mark);
    }

    /** Minutes from adhan to iqama for a prayer; none at sunrise or for Jumu'ah. */
    private int iqamaMinutes(int i, boolean friday) {
        if (i == 1 || (i == 2 && friday)) return 0;
        JSONObject q = cfg.optJSONObject("iqama");
        return q == null ? 0 : Math.max(0, Math.min(60, q.optInt(PrayerTimes.KEYS[i], 0)));
    }

    private static String name(int i, boolean friday, boolean ar) {
        return i == 2 && friday ? (ar ? "الجمعة" : "Jumu'ah") : (ar ? AR : EN)[i];
    }

    private static void bold(SpannableStringBuilder s, String text, int colour) {
        int at = s.length();
        s.append(text);
        s.setSpan(new ForegroundColorSpan(colour), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new StyleSpan(Typeface.BOLD), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** "بعد 25 دقيقة", "بعد 7 دقائق", "بعد 3 س 42 د" / "in 25 min", "in 3 h 42 min". */
    private static String left(long minutes, boolean ar) {
        long h = minutes / 60, mm = minutes % 60;
        if (!ar) return "in " + (h > 0 ? h + " h " + mm + " min" : mm + " min");
        if (h > 0) return "بعد " + h + " س " + mm + " د";
        return "بعد " + (mm == 1 ? "دقيقة" : mm == 2 ? "دقيقتين" : mm <= 10 ? mm + " دقائق" : mm + " دقيقة");
    }

    /** The time, its ص / م (AM / PM) smaller after it: on its left in Arabic, on its right in English. */
    private CharSequence time(double min, boolean ar, boolean arab) {
        String t = Ui.digits(clock(min, ar), arab);
        int sp = t.indexOf(' ');
        SpannableStringBuilder s = new SpannableStringBuilder(t);
        if (sp > 0) {
            s.setSpan(new RelativeSizeSpan(.62f), sp, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(Ui.INK2), sp, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    private String clock(double min, boolean ar) {
        long r = Math.round(min) % 1440, h = r / 60, m = r % 60;
        if (cfg.optBoolean("clock24", false)) return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m);
        long h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + String.format(java.util.Locale.ROOT, "%02d", m) + " " + (h < 12 ? (ar ? "ص" : "AM") : (ar ? "م" : "PM"));
    }

    private double[] compute(int y, int m, int d, TimeZone tz, int dayOffset) {
        JSONObject o = cfg.optJSONObject("offsets");
        double[] off = new double[6];
        for (int i = 0; i < 6; i++) off[i] = o == null ? 0 : Math.max(-30, Math.min(30, o.optInt(PrayerTimes.KEYS[i], 0)));
        return PrayerTimes.compute(y, m, d, cfg.optDouble("lat", 23.59), cfg.optDouble("lon", 58.38), tz.getID(),
            cfg.optString("method", "oman"), "hanafi".equals(cfg.optString("asr")), off, ramadan(tz, dayOffset));
    }

    private boolean ramadan(TimeZone tz, int dayOffset) {
        IslamicCalendar h = new IslamicCalendar(android.icu.util.TimeZone.getTimeZone(tz.getID()), ULocale.ROOT);
        h.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        h.add(IslamicCalendar.DAY_OF_MONTH, dayOffset + cfg.optInt("hijriOffset", 0));
        return h.get(IslamicCalendar.MONTH) == IslamicCalendar.RAMADAN;
    }
}
