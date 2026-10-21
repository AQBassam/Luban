package om.luban.screen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

/**
 * Settings over ADB for the setup tool (tools/luban.py). Only the shell can send these: the manifest requires
 * android.permission.DUMP, which no app on the TV holds.
 *
 *   am broadcast -n om.luban.screen/.AdbReceiver -a om.luban.screen.GET
 *   am broadcast -n om.luban.screen/.AdbReceiver -a om.luban.screen.SET --es b64 <base64 of the settings JSON>
 *   am broadcast -n om.luban.screen/.AdbReceiver -a om.luban.screen.UPDATE
 */
public class AdbReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = String.valueOf(i.getAction());
        if ("om.luban.screen.GET".equals(a)) {
            setResultData(Store.settingsJson(c));
        } else if ("om.luban.screen.SET".equals(a)) {
            String b64 = i.getStringExtra("b64");
            String err;
            try {
                err = b64 == null ? "missing b64" : Store.save(c, new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                err = "not base64";
            }
            setResultData(err == null ? "saved" : "error: " + err);
        } else if ("om.luban.screen.UPDATE".equals(a)) {
            final PendingResult pending = goAsync();
            final Context app = c.getApplicationContext();
            new Thread(() -> { pending.setResultData("update: " + Updates.check(app, true)); pending.finish(); }).start();
        } else {
            setResultData("error: unknown action");
        }
    }
}
