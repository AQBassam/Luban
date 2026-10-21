package om.luban.screen;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebView;

/** Full-screen preview of the screensaver; any key closes it. */
public class PreviewActivity extends Activity {
    private WebView web;
    private final Runnable reload = () -> { if (web != null) web.reload(); };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = Screen.create(this);
        setContentView(web);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.hide(WindowInsets.Type.systemBars());
        }
        Store.listen(reload);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        Store.unlisten(reload);
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
