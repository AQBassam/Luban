package om.luban.home;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Java twin of web/js/prayer.js (same formulas, same methods, same rounding). Pure Java: tests/prayer_parity.sh
 * compares the two on hundreds of dates and places, so the home screen and the screensaver always agree.
 */
public final class PrayerTimes {
    public static final String[] KEYS = {"fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"};

    /** fajr angle, isha angle (NaN when isha is minutes), isha minutes, Ramadan isha minutes, maghrib angle (NaN = sunset) */
    private static final Map<String, double[]> METHODS = new HashMap<>();
    /** minutes the authority itself adds to each of KEYS */
    private static final Map<String, double[]> ADJUST = new HashMap<>();
    static {
        double N = Double.NaN;
        METHODS.put("oman", new double[] {18, 17, 0, 0, N});
        METHODS.put("gulf", new double[] {19.5, N, 90, 0, N});
        METHODS.put("mwl", new double[] {18, 17, 0, 0, N});
        METHODS.put("makkah", new double[] {18.5, N, 90, 120, N});
        METHODS.put("egypt", new double[] {19.5, 17.5, 0, 0, N});
        METHODS.put("karachi", new double[] {18, 18, 0, 0, N});
        METHODS.put("isna", new double[] {15, 15, 0, 0, N});
        METHODS.put("kuwait", new double[] {18, 17.5, 0, 0, N});
        METHODS.put("qatar", new double[] {18, N, 90, 0, N});
        METHODS.put("dubai", new double[] {18.2, 18.2, 0, 0, N});
        METHODS.put("singapore", new double[] {20, 18, 0, 0, N});
        METHODS.put("turkey", new double[] {18, 17, 0, 0, N});
        METHODS.put("france", new double[] {12, 12, 0, 0, N});
        METHODS.put("tehran", new double[] {17.7, 14, 0, 0, 4.5});
        // Oman: the Ministry of Awqaf's 5-minute precaution on every prayer after Fajr (same as the JS `adjust`)
        ADJUST.put("oman", new double[] {0, 0, 5, 5, 5, 5});
    }

    private PrayerTimes() {}

    private static double sin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double cos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double tan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double wrap(double x, double m) { return ((x % m) + m) % m; }

    /** {declination, equation of time} */
    private static double[] sun(double jd) {
        double d = jd - 2451545.0;
        double g = wrap(357.529 + 0.98560028 * d, 360);
        double q = wrap(280.459 + 0.98564736 * d, 360);
        double lambda = wrap(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360);
        double eps = 23.439 - 0.00000036 * d;
        double ra = wrap(Math.toDegrees(Math.atan2(cos(eps) * sin(lambda), cos(lambda))) / 15, 24);
        return new double[] {Math.toDegrees(Math.asin(sin(eps) * sin(lambda))), q / 15 - ra};
    }

    /** Offset of the zone from UTC in hours at noon UTC of that date, rounded to the minute like the JS. */
    static double zoneOffsetHours(String tz, int y, int m, int d) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(y, m - 1, d, 12, 0, 0);
        return Math.round(TimeZone.getTimeZone(tz).getOffset(c.getTimeInMillis()) / 60000.0) / 60.0;
    }

    /** Minutes after local midnight for each of KEYS (fractional; the caller rounds). */
    public static double[] compute(int y, int m, int d, double lat, double lon, String tz, String method,
                                   boolean hanafi, double[] offsets, boolean ramadan) {
        if (!METHODS.containsKey(method)) method = "oman";
        double[] me = METHODS.get(method);
        double[] adj = ADJUST.get(method);
        double tzh = zoneOffsetHours(tz, y, m, d);
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(y, m - 1, d, 0, 0, 0);
        double jd0 = c.getTimeInMillis() / 86400000.0 + 2440587.5 - lon / 360;

        java.util.function.DoubleUnaryOperator transit = h -> wrap(12 - sun(jd0 + h / 24)[1], 24);
        java.util.function.DoubleBinaryOperator hourAngle = (angle, h) -> {
            double dec = sun(jd0 + h / 24)[0];
            double x = (-sin(angle) - sin(dec) * sin(lat)) / (cos(dec) * cos(lat));
            return x < -1 || x > 1 ? Double.NaN : Math.toDegrees(Math.acos(x)) / 15;
        };
        java.util.function.DoubleBinaryOperator before = (angle, guess) -> {
            double t = transit.applyAsDouble(guess) - hourAngle.applyAsDouble(angle, guess);
            return transit.applyAsDouble(t) - hourAngle.applyAsDouble(angle, t);
        };
        java.util.function.DoubleBinaryOperator after = (angle, guess) -> {
            double t = transit.applyAsDouble(guess) + hourAngle.applyAsDouble(angle, guess);
            return transit.applyAsDouble(t) + hourAngle.applyAsDouble(angle, t);
        };
        java.util.function.DoubleUnaryOperator asrAngle = h -> {
            double dec = sun(jd0 + h / 24)[0];
            return -Math.toDegrees(Math.atan(1 / ((hanafi ? 2 : 1) + tan(Math.abs(lat - dec)))));
        };

        double SUN = 0.833;
        double dhuhr = transit.applyAsDouble(12);
        double sunrise = before.applyAsDouble(SUN, 6);
        double maghrib = Double.isNaN(me[4]) ? after.applyAsDouble(SUN, 18) : after.applyAsDouble(me[4], 18);
        double sunset = after.applyAsDouble(SUN, 18);
        double fajr = before.applyAsDouble(me[0], 5);
        double asr = after.applyAsDouble(asrAngle.applyAsDouble(13), 13);
        asr = after.applyAsDouble(asrAngle.applyAsDouble(asr), asr);
        boolean ishaMinutes = Double.isNaN(me[1]);
        double isha = ishaMinutes ? maghrib + ((ramadan && me[3] > 0) ? me[3] : me[2]) / 60 : after.applyAsDouble(me[1], 18);

        double night = wrap(sunrise - sunset, 24);
        if (Double.isNaN(fajr) || wrap(sunrise - fajr, 24) > night * me[0] / 60) fajr = sunrise - night * me[0] / 60;
        if (!ishaMinutes && (Double.isNaN(isha) || wrap(isha - sunset, 24) > night * me[1] / 60)) isha = sunset + night * me[1] / 60;

        double[] t = {fajr, sunrise, dhuhr, asr, maghrib, isha};
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) {
            double local = t[i] + tzh - lon / 15;
            out[i] = wrap(local * 60 + (adj != null ? adj[i] : 0) + (offsets != null ? offsets[i] : 0), 1440);
        }
        return out;
    }

    public static String hhmm(double min) {
        long r = Math.round(min) % 1440;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", r / 60, r % 60);
    }

    /** For tests/prayer_parity.sh: one line per case on stdin -> the six times. */
    public static void main(String[] a) throws Exception {
        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
        for (String line; (line = in.readLine()) != null; ) {
            String[] f = line.trim().split(" ");
            if (f.length < 9) continue;
            double[] r = compute(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), Double.parseDouble(f[3]),
                Double.parseDouble(f[4]), f[5], f[6], "hanafi".equals(f[7]), null, "1".equals(f[8]));
            StringBuilder b = new StringBuilder();
            for (double x : r) b.append(hhmm(x)).append(' ');
            System.out.println(b.toString().trim());
        }
    }
}
