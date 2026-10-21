package om.luban.screen;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.TimeZone;

/** The chosen city resolved against content/cities.json, the same rules as resolvePlace() in web/js/settings.js. */
final class Place {
    String id = "muscat", ar = "مسقط", en = "Muscat", tz = "Asia/Muscat", method = "oman", gov = "muscat";
    double lat = 23.59, lon = 58.38;

    static Place resolve(Context c, JSONObject s) {
        Place p = new Place();
        try {
            JSONObject cities = new JSONObject(asset(c, "content/cities.json"));
            String want = s.optString("city", "muscat");
            String method = s.optString("method", "");
            JSONObject custom = s.optJSONObject("place");
            if ("custom".equals(want) && custom != null && validTz(custom.optString("tz")) && Math.abs(custom.optDouble("lat", 99)) <= 66
                && Math.abs(custom.optDouble("lon", 999)) <= 180) {
                p.id = "custom";
                p.ar = custom.optString("ar", custom.optString("en", ""));
                p.en = custom.optString("en", p.ar);
                p.lat = custom.getDouble("lat");
                p.lon = custom.getDouble("lon");
                p.tz = custom.getString("tz");
                p.gov = "";
                p.method = method.isEmpty() ? "mwl" : method;
                return p;
            }
            JSONArray list = cities.getJSONArray("cities");
            JSONObject hit = null;
            for (int i = 0; i < list.length(); i++) {
                JSONObject x = list.getJSONObject(i);
                if (x.getString("id").equals(want)) hit = x;
            }
            if (hit == null) return p;
            JSONObject country = cities.getJSONObject("countries").optJSONObject(hit.getString("c"));
            p.id = hit.getString("id");
            p.ar = hit.getString("ar");
            p.en = hit.getString("en");
            p.lat = hit.getDouble("lat");
            p.lon = hit.getDouble("lon");
            p.gov = hit.optString("gov", "");
            p.tz = hit.optString("tz", country != null ? country.optString("tz", "UTC") : "UTC");
            p.method = !method.isEmpty() ? method : country != null ? country.optString("method", "mwl") : "mwl";
        } catch (Exception ignored) {
            // the default (Muscat) stands
        }
        return p;
    }

    private static boolean validTz(String tz) {
        for (String id : TimeZone.getAvailableIDs()) if (id.equals(tz)) return true;
        return false;
    }

    static String asset(Context c, String path) throws java.io.IOException {
        try (InputStream in = c.getAssets().open(path)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }
}
