package om.luban.home;

import android.content.Context;
import android.database.Cursor;
import android.icu.util.IslamicCalendar;
import android.icu.util.ULocale;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * The next prayer, one line under the date: a small dial, the prayer and its time, the time left (today's five times stay on
 * the screensaver). Off until it is switched on in the screensaver's phone settings; the city and method come from there
 * (ConfigProvider, signature permission).
 */
final class PrayerCard {
    private static final Uri CONFIG = Uri.parse("content://om.luban.screen.config");
    private static final String[] AR = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final String[] EN = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private final Context c;
    final LinearLayout view;
    private final TextView next, left;
    private final Dial dial;
    private JSONObject cfg;
    private String computedFor = "";
    private double[] today, tomorrow;

    PrayerCard(Context c) {
        this.c = c;
        view = new LinearLayout(c);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);

        dial = new Dial(c);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40));
        dl.setMarginEnd(Ui.dp(c, 10));
        view.addView(dial, dl);
        next = Ui.text(c, "", 18, Ui.bold(), Ui.FOAM);
        view.addView(next);
        left = Ui.text(c, "", 15, Ui.regular(), Ui.INK2);
        left.setPadding(Ui.dp(c, 8), 0, 0, 0);
        view.addView(left);
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
        int idx = -1;
        for (int i = 0; i < 6; i++) if (i != 1 && order[i] > mins) { idx = i; break; }
        boolean afterIsha = idx < 0;
        double at = afterIsha ? tomorrow[0] : order[idx];
        double leftMin = afterIsha ? tomorrow[0] + 1440 - mins : at - mins;
        if (afterIsha) idx = 0;
        String name = idx == 2 && friday ? (ar ? "الجمعة" : "Jumu'ah") : (ar ? AR : EN)[idx];

        next.setText(Ui.digits(name + " " + fmt(at, ar), arab));
        long l = (long) Math.ceil(leftMin), h = l / 60, mm = l % 60;
        left.setText(Ui.digits("· " + (ar ? (h > 0 ? "بعد " + h + " س " + mm + " د" : "بعد " + mm + " دقيقة") : "in " + (h > 0 ? h + "h " : "") + mm + "m"), arab));
        dial.set(order, mins, idx, ar);
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

    private String fmt(double min, boolean ar) {
        long r = Math.round(min) % 1440, h = r / 60, m = r % 60;
        if (cfg.optBoolean("clock24", false)) return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m);
        long h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + String.format(java.util.Locale.ROOT, "%02d", m) + " " + (h < 12 ? (ar ? "ص" : "AM") : (ar ? "م" : "PM"));
    }
}
