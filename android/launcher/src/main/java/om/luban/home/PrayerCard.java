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
 * The prayer card on the home screen: the dial, the next prayer, the time left, and today's five times. Off until it is switched
 * on in the screensaver's phone settings; the city and method come from there (ConfigProvider, signature permission).
 */
final class PrayerCard {
    private static final Uri CONFIG = Uri.parse("content://om.luban.screen.config");
    private static final String[] AR = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final String[] EN = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private final Context c;
    final LinearLayout view;
    private final TextView title, next, left;
    private final Dial dial;
    private final TextView[] small = new TextView[5];
    private JSONObject cfg;
    private String computedFor = "";
    private double[] today, tomorrow;

    PrayerCard(Context c) {
        this.c = c;
        view = new LinearLayout(c);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setBackground(Ui.glass(c, 22));
        int p = Ui.dp(c, 20);
        view.setPadding(p, Ui.dp(c, 10), p, Ui.dp(c, 10));

        dial = new Dial(c);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 80), Ui.dp(c, 80));
        dl.setMarginEnd(Ui.dp(c, 20));
        view.addView(dial, dl);

        LinearLayout main = new LinearLayout(c);
        main.setOrientation(LinearLayout.VERTICAL);
        title = Ui.text(c, "", 14, Ui.medium(), Ui.LUBAN_SOFT);
        next = Ui.text(c, "", 26, Ui.bold(), Ui.FOAM);
        left = Ui.text(c, "", 15, Ui.regular(), Ui.INK2);
        next.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 4));
        main.addView(title);
        main.addView(next);
        main.addView(left);
        view.addView(main);

        View sep = new View(c);
        sep.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(c, 1), -1);
        sl.setMargins(Ui.dp(c, 22), Ui.dp(c, 4), Ui.dp(c, 22), Ui.dp(c, 4));
        view.addView(sep, sl);

        for (int i = 0; i < 5; i++) {
            small[i] = Ui.text(c, "", 14, Ui.medium(), Ui.INK2);
            small[i].setGravity(Gravity.CENTER);
            small[i].setLineSpacing(Ui.dp(c, 4), 1f);
            view.addView(small[i], new LinearLayout.LayoutParams(Ui.dp(c, 92), -2));
        }
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
        String city = ar ? cfg.optString("cityAr") : cfg.optString("cityEn");

        title.setText((ar ? "الصلاة القادمة · " : "Next prayer · ") + city);
        next.setText(Ui.digits(name + "  " + fmt(at, ar), arab));
        long l = (long) Math.ceil(leftMin), h = l / 60, mm = l % 60;
        left.setText(Ui.digits(ar ? (h > 0 ? "بعد " + h + " س " + mm + " د" : "بعد " + mm + " دقيقة") : "in " + (h > 0 ? h + "h " : "") + mm + "m", arab));
        int[] five = {0, 2, 3, 4, 5};
        for (int i = 0; i < 5; i++) {
            int k = five[i];
            String n = k == 2 && friday ? (ar ? "الجمعة" : "Jumu'ah") : (ar ? AR : EN)[k];
            double shown = afterIsha && k == 0 ? tomorrow[0] : today[k];   // after Isha: tomorrow's Fajr
            small[i].setText(Ui.digits(n + "\n" + fmt(shown, ar), arab));
            small[i].setTextColor(k == idx ? Ui.FOAM : Ui.INK2);
        }
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
