package om.luban.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

/**
 * Makes the Home key open Luban's home on a TV whose firmware keeps its own home ahead of the one Android picks.
 * It reads key presses and nothing else: no window content, no events (see accessibility_service.xml). A short press
 * of Home opens Luban's home; a long press opens the TV's own home, so that one is always one press away. Any
 * failure leaves the key to the system, which is how it behaves with the service off. Turned on and off with
 * `tools/luban.py homekey on|off`, or in the TV's accessibility settings.
 */
public class HomeKeyService extends AccessibilityService {
    private static final long LONG_PRESS_MS = 600;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean longDone;
    private final Runnable longPress = () -> { longDone = true; stockHome(); };

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }

    @Override public void onInterrupt() { handler.removeCallbacks(longPress); }

    @Override
    public boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME || !ready()) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) { longDone = false; handler.postDelayed(longPress, LONG_PRESS_MS); }
            return true;
        }
        handler.removeCallbacks(longPress);
        if (!longDone && !lubanHome()) stockHome();   // Luban's home would not open: the TV's own does
        longDone = false;
        return true;
    }

    /** Not while the TV is still being set up, and only when Luban's home is installed. */
    private boolean ready() {
        if (Settings.Global.getInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) != 1) return false;
        try { getPackageManager().getPackageInfo(getPackageName(), 0); return true; } catch (PackageManager.NameNotFoundException x) { return false; }
    }

    private boolean lubanHome() {
        try {
            startActivity(new Intent().setClassName(getPackageName(), HomeActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception x) { return false; }
    }

    /** The TV's own home: the highest-priority home app that is not ours or Android's fallback. */
    private void stockHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> all = getPackageManager().queryIntentActivities(home, 0);
        ResolveInfo best = null;
        for (ResolveInfo r : all) {
            ActivityInfo a = r.activityInfo;
            if (a == null || getPackageName().equals(a.packageName) || a.name.contains("FallbackHome") || a.name.contains("Resolver")) continue;
            if (best == null || r.priority > best.priority) best = r;
        }
        if (best == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClassName(best.activityInfo.packageName, best.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }
}
