package om.luban.screen;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * The WebView that shows the screen. The page is served from https://appassets.androidplatform.net (a real https
 * origin, not a local file), so it has no access to the device's files and no special network rights: it can only reach
 * sources that allow it (CORS), plus the short allowlist in Net.proxyAllowed() through /net.
 */
final class Screen {
    static final String HOST = "appassets.androidplatform.net";
    static final String URL = "https://" + HOST + "/screen/index.html?app=1";

    private Screen() {}

    @SuppressLint("SetJavaScriptEnabled")
    static WebView create(Context c) {
        final Context app = c.getApplicationContext();
        WebView w = new WebView(c);
        w.setBackgroundColor(0xFF0B1013);
        w.setFocusable(false);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setOffscreenPreRaster(true);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/screen/", path -> page(app, path))
            .addPathHandler("/photos/", path -> {
                try {
                    InputStream in = Photos.open(app, path);
                    return in == null ? null : new WebResourceResponse("image/jpeg", null, in);
                } catch (Exception e) {
                    return null;
                }
            })
            .build();

        w.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost()) && "https".equals(u.getScheme())) {
                    if ("/settings.json".equals(u.getPath())) {
                        return text("application/json", Store.settingsJson(app));
                    }
                    if ("/net".equals(u.getPath()) && "GET".equals(r.getMethod())) {
                        String target = u.getQueryParameter("u");
                        if (target == null || !Net.proxyAllowed(target)) return status(403);
                        try {
                            return new WebResourceResponse("application/json", "utf-8",
                                new ByteArrayInputStream(Net.get(target, 2 * 1024 * 1024)));
                        } catch (Exception e) {
                            return status(502);
                        }
                    }
                }
                return loader.shouldInterceptRequest(u);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return true;   // the screen never navigates anywhere
            }
        });
        w.loadUrl(URL);
        UpdateJob.schedule(app);
        return w;
    }

    /** index.html and settings.html from a verified update when there is one, everything else from the APK. */
    static WebResourceResponse page(Context c, String path) {
        if (!path.matches("[A-Za-z0-9_-]+\\.html|fonts/[A-Za-z0-9_-]+\\.woff2")) return null;
        try {
            File dir = Updates.activeDir(c);
            InputStream in = dir != null && path.endsWith(".html") && new File(dir, path).isFile()
                ? new FileInputStream(new File(dir, path))
                : c.getAssets().open("screen/" + path);
            String mime = path.endsWith(".html") ? "text/html" : "font/woff2";
            return new WebResourceResponse(mime, path.endsWith(".html") ? "utf-8" : null, 200, "OK",
                Collections.singletonMap("Cache-Control", "no-store"), in);
        } catch (Exception e) {
            return null;
        }
    }

    static WebResourceResponse text(String mime, String body) {
        return new WebResourceResponse(mime, "utf-8", new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    static WebResourceResponse status(int code) {
        return new WebResourceResponse("text/plain", "utf-8", code, code == 403 ? "Forbidden" : "Bad Gateway",
            Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }
}
