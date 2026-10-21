package om.luban.screen;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import org.json.JSONObject;

/**
 * Read-only, for the home screen app only (signature permission: the same signing key). One row, one column "json":
 * what the home screen's prayer card needs, with the city already resolved.
 */
public class ConfigProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[] {"json"});
        try {
            JSONObject s = Store.settings(getContext());
            Place p = Place.resolve(getContext(), s);
            JSONObject o = new JSONObject();
            o.put("homeCard", s.optBoolean("homeCard", false));
            o.put("lang", "en".equals(s.optString("lang")) ? "en" : "ar");
            o.put("digits", "arab".equals(s.optString("digits")) ? "arab" : "latn");
            o.put("clock24", s.optBoolean("clock24", false));
            o.put("cityAr", p.ar).put("cityEn", p.en).put("lat", p.lat).put("lon", p.lon).put("tz", p.tz);
            o.put("method", p.method).put("asr", "hanafi".equals(s.optString("asr")) ? "hanafi" : "standard");
            o.put("offsets", s.optJSONObject("offsets") != null ? s.optJSONObject("offsets") : new JSONObject());
            o.put("iqama", s.optJSONObject("iqama") != null ? s.optJSONObject("iqama") : new JSONObject());
            o.put("bg", "photos".equals(s.optString("bg")) ? "photos" : "chart");
            o.put("hijriOffset", Math.max(-2, Math.min(2, s.optInt("hijriOffset", 0))));
            c.addRow(new Object[] {o.toString()});
        } catch (Exception ignored) { }
        return c;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
