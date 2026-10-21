package om.luban.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Arrays;
import java.util.List;

/**
 * The favourites row over ADB, for the setup tool. Only the shell can send these (android.permission.DUMP).
 *
 *   am broadcast -n om.luban.home/.AdbReceiver -a om.luban.home.GET     favourites, then every app: package|label
 *   am broadcast -n om.luban.home/.AdbReceiver -a om.luban.home.SET --es apps com.a,com.b
 */
public class AdbReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        List<Apps.App> apps = Apps.load(c);
        if ("om.luban.home.SET".equals(i.getAction())) {
            String s = i.getStringExtra("apps");
            if (s == null) { setResultData("error: --es apps com.a,com.b"); return; }
            Favourites.set(c, Arrays.asList(s.split(",")));
            HomeActivity.favouritesChanged();
        }
        StringBuilder b = new StringBuilder("favourites: ").append(String.join(",", Favourites.get(c, apps))).append('\n');
        for (Apps.App a : apps) b.append(a.pkg).append('|').append(a.label).append('\n');
        setResultData(b.toString().trim());
    }
}
