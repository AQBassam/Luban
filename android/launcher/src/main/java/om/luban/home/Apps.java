package om.luban.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The apps a person can open: TV apps first (their banner), then phone-style apps (their icon). Home screens are left out. */
final class Apps {
    static final class App {
        final String pkg, label;
        final ComponentName component;
        final boolean tv;
        App(String pkg, String label, ComponentName component, boolean tv) {
            this.pkg = pkg; this.label = label; this.component = component; this.tv = tv;
        }
    }

    private Apps() {}

    static List<App> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> homes = new HashSet<>();
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
            homes.add(r.activityInfo.packageName);
        Map<String, App> out = new LinkedHashMap<>();
        add(pm, out, homes, Intent.CATEGORY_LEANBACK_LAUNCHER, true, c.getPackageName());
        add(pm, out, homes, Intent.CATEGORY_LAUNCHER, false, c.getPackageName());
        List<App> list = new ArrayList<>(out.values());
        Collator col = Collator.getInstance(new Locale("ar"));
        Collections.sort(list, (a, b) -> col.compare(a.label, b.label));
        return list;
    }

    private static void add(PackageManager pm, Map<String, App> out, Set<String> homes, String category, boolean tv, String self) {
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
            ActivityInfo a = r.activityInfo;
            if (a.packageName.equals(self) || out.containsKey(a.packageName)) continue;
            if (homes.contains(a.packageName) && !tv) continue;   // other home screens, unless they also offer a TV app
            out.put(a.packageName, new App(a.packageName, String.valueOf(r.loadLabel(pm)), new ComponentName(a.packageName, a.name), tv));
        }
    }

    /** The TV banner when the app has one, else its icon. */
    static Drawable art(Context c, App a) {
        PackageManager pm = c.getPackageManager();
        try {
            Drawable d = pm.getActivityBanner(a.component);
            if (d == null) d = pm.getApplicationBanner(a.pkg);
            if (d != null) return d;
            return pm.getActivityIcon(a.component);
        } catch (Exception e) {
            return pm.getDefaultActivityIcon();
        }
    }

    static boolean hasBanner(Context c, App a) {
        PackageManager pm = c.getPackageManager();
        try { return pm.getActivityBanner(a.component) != null || pm.getApplicationBanner(a.pkg) != null; }
        catch (Exception e) { return false; }
    }

    static void open(Context c, App a) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(a.tv ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
            .setComponent(a.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { c.startActivity(i); } catch (Exception ignored) { }
    }
}
