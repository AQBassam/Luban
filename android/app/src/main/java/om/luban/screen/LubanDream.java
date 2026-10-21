package om.luban.screen;

import android.service.dreams.DreamService;
import android.webkit.WebView;

/** The system screensaver: starts on the TV's own idle timer; any key wakes the TV. */
public class LubanDream extends DreamService {
    private WebView web;
    private final Runnable reload = () -> { if (web != null) web.reload(); };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        web = Screen.create(this);
        setContentView(web);
        Store.listen(reload);
    }

    @Override
    public void onDetachedFromWindow() {
        Store.unlisten(reload);
        if (web != null) { web.destroy(); web = null; }
        super.onDetachedFromWindow();
    }
}
