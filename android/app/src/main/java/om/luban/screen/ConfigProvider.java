package om.luban.screen;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * Read-only, for the home screen app only (signature permission: the same signing key). One row, one column "json":
 * what the home screen's prayer card needs, with the city already resolved. And photos/<name>: a background photo the
 * home screen does not carry, from the screensaver's downloaded copies, so both show all of them without the APK growing.
 */
public class ConfigProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (!"r".equals(mode) || seg.size() != 2 || !"photos".equals(seg.get(0))) throw new FileNotFoundException("photos/<name> only");
        try {
            File f = Photos.file(getContext(), seg.get(1));
            if (f != null) return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException ignored) {
            // no network yet: the home screen keeps the photo it has
        }
        throw new FileNotFoundException(seg.get(1));
    }

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
            // how many household tasks are open, for the badge on the home screen's button; the tasks themselves stay here
            org.json.JSONArray tasks = s.optJSONArray("tasks");
            int open = 0;
            if (tasks != null) for (int i = 0; i < tasks.length(); i++) {
                JSONObject t = tasks.optJSONObject(i);
                if (t != null && !t.optBoolean("done", false)) open++;
            }
            o.put("openTasks", open);
            c.addRow(new Object[] {o.toString()});
        } catch (Exception ignored) { }
        return c;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
