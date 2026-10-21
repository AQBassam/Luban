package om.luban.screen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** HTTPS GETs with size limits. proxy() is what the page may ask for: a short list of hosts and paths, nothing else. */
final class Net {
    private Net() {}

    /** Sources the page reads that send no CORS headers, so it cannot call them itself. */
    private static final Set<String> PROXY_HOSTS = new HashSet<>(Arrays.asList("query1.finance.yahoo.com"));
    private static final Pattern PROXY_PATH = Pattern.compile("/v8/finance/chart/[A-Za-z0-9^=.%-]{1,20}");

    static boolean proxyAllowed(String url) {
        try {
            URL u = new URL(url);
            return "https".equals(u.getProtocol()) && u.getPort() == -1 && PROXY_HOSTS.contains(u.getHost())
                && PROXY_PATH.matcher(u.getPath()).matches();
        } catch (Exception e) {
            return false;
        }
    }

    static byte[] get(String url, int maxBytes) throws IOException {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new IOException("https only");
        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        con.setInstanceFollowRedirects(true);   // GitHub release assets redirect to their CDN, still https
        con.setRequestProperty("User-Agent", "Luban/" + BuildConfig.VERSION_NAME);
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            if (!"https".equals(con.getURL().getProtocol())) throw new IOException("redirected off https");
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > maxBytes) throw new IOException("too large");
                }
                return out.toByteArray();
            }
        } finally {
            con.disconnect();
        }
    }
}
