package om.luban.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The favourites row: package names in order. Starts with the common streaming apps that are installed. */
final class Favourites {
    static final int MAX = 10;
    private static final List<String> SUGGESTED = Arrays.asList(
        "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
        "com.shahid.tv", "com.osn.go", "com.starzplay.tv", "com.google.android.youtube.tvmusic", "com.spotify.tv.android",
        "om.luban.screen");

    private Favourites() {}

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("home", Context.MODE_PRIVATE); }

    static List<String> get(Context c, List<Apps.App> installed) {
        String saved = sp(c).getString("favourites", null);
        List<String> out = new ArrayList<>();
        if (saved != null) {
            for (String p : saved.split(",")) if (!p.isEmpty()) out.add(p);
        } else {
            for (String p : SUGGESTED) for (Apps.App a : installed) if (a.pkg.equals(p) && out.size() < 6) out.add(p);
            for (Apps.App a : installed) if (out.size() < 5 && a.tv && !out.contains(a.pkg)) out.add(a.pkg);
        }
        return out;
    }

    static void set(Context c, List<String> pkgs) {
        List<String> clean = new ArrayList<>();
        for (String p : pkgs) if (p.matches("[A-Za-z0-9_.]{2,200}") && !clean.contains(p) && clean.size() < MAX) clean.add(p);
        sp(c).edit().putString("favourites", String.join(",", clean)).apply();
    }

    static void toggle(Context c, List<Apps.App> installed, String pkg) {
        List<String> f = get(c, installed);
        if (!f.remove(pkg) && f.size() < MAX) f.add(pkg);
        set(c, f);
    }

    static void move(Context c, List<Apps.App> installed, String pkg, int by) {
        List<String> f = get(c, installed);
        int i = f.indexOf(pkg), j = i + by;
        if (i < 0 || j < 0 || j >= f.size()) return;
        f.remove(i);
        f.add(j, pkg);
        set(c, f);
    }
}
