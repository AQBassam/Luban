package om.luban.screen;

import android.content.Context;
import android.webkit.WebResourceResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The phone settings page, served on the local network only while the TV's settings screen is open.
 * Every path starts with a random 128-bit token shown in the QR code on the TV: a device that has not seen the screen
 * cannot read or change anything. POST needs Content-Type: application/json, which a plain cross-site form cannot send.
 */
final class PhoneServer {
    static final int PORT = 8737;
    private final Context c;
    private final String token;
    private ServerSocket server;
    private ExecutorService pool;
    private Map<String, String> arNames;

    PhoneServer(Context c) {
        this.c = c.getApplicationContext();
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(java.util.Locale.ROOT, "%02x", x & 0xff));
        token = s.toString();
    }

    /** @return the address to put in the QR code, or null when there is no network */
    String start() throws IOException {
        String ip = lanAddress();
        if (ip == null) return null;
        try { server = new ServerSocket(PORT); } catch (IOException busy) { server = new ServerSocket(0); }
        pool = Executors.newFixedThreadPool(4);
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket s = server.accept();
                    pool.execute(() -> handle(s));
                } catch (IOException e) {
                    return;
                }
            }
        }, "luban-phone");
        t.setDaemon(true);
        t.start();
        return "http://" + ip + ":" + server.getLocalPort() + "/" + token + "/";
    }

    void stop() {
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        if (pool != null) pool.shutdownNow();
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(10000);
            InputStream in = sock.getInputStream();
            String line = readLine(in);
            if (line == null) return;
            String[] req = line.split(" ");
            if (req.length < 3) return;
            Map<String, String> headers = new HashMap<>();
            for (String h; (h = readLine(in)) != null && !h.isEmpty(); ) {
                int i = h.indexOf(':');
                if (i > 0) headers.put(h.substring(0, i).trim().toLowerCase(Locale.ROOT), h.substring(i + 1).trim());
                if (headers.size() > 40) return;
            }
            String method = req[0], target = req[1];
            String path = target, query = "";
            int q = target.indexOf('?');
            if (q >= 0) { path = target.substring(0, q); query = target.substring(q + 1); }
            String prefix = "/" + token + "/";
            OutputStream out = sock.getOutputStream();
            if (!path.startsWith(prefix)) { send(out, 404, "text/plain", "not found".getBytes()); return; }
            String rest = path.substring(prefix.length());

            if ("POST".equals(method) && "settings.json".equals(rest)) {
                String type = headers.getOrDefault("content-type", "");
                int len = parseInt(headers.get("content-length"));
                if (!type.startsWith("application/json") || len <= 0 || len > Store.MAX_BYTES) {
                    send(out, 400, "text/plain", "bad request".getBytes());
                    return;
                }
                byte[] body = readN(in, len);
                String err = Store.save(c, new String(body, StandardCharsets.UTF_8));
                if (err != null) send(out, 400, "text/plain", err.getBytes());
                else send(out, 200, "application/json", Store.settingsJson(c).getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!"GET".equals(method)) { send(out, 405, "text/plain", "method".getBytes()); return; }
            if (rest.isEmpty() || "settings.html".equals(rest)) rest = "settings.html";
            if ("settings.json".equals(rest)) {
                send(out, 200, "application/json", Store.settingsJson(c).getBytes(StandardCharsets.UTF_8));
            } else if ("teams".equals(rest)) {
                send(out, 200, "application/json", teams(param(query, "q")).getBytes(StandardCharsets.UTF_8));
            } else {
                WebResourceResponse r = Screen.page(c, rest);
                if (r == null) { send(out, 404, "text/plain", "not found".getBytes()); return; }
                try (InputStream data = r.getData()) {
                    send(out, 200, r.getMimeType() + ("text/html".equals(r.getMimeType()) ? "; charset=utf-8" : ""), readAll(data));
                }
            }
        } catch (Exception ignored) {
            // a dropped phone connection is not the TV's problem
        }
    }

    /** Team search: the data server when one is set, else TheSportsDB directly. Football only, Arabic names added. */
    private String teams(String q) {
        JSONArray out = new JSONArray();
        if (q == null || !q.matches("[\\p{L}\\p{N} .'-]{2,40}")) return out.toString();
        try {
            String api = Store.settings(c).optString("api", "");
            if (api.matches("https://[A-Za-z0-9.-]+(:\\d{2,5})?(/[A-Za-z0-9._~/-]*)?")) {
                JSONArray a = new JSONArray(new String(Net.get(api.replaceAll("/+$", "") + "/v1/teams?q=" + enc(q), 200_000), StandardCharsets.UTF_8));
                for (int i = 0; i < a.length(); i++) out.put(withArabic(a.getJSONObject(i)));
                return out.toString();
            }
            JSONObject j = new JSONObject(new String(Net.get("https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t=" + enc(q), 2_000_000), StandardCharsets.UTF_8));
            JSONArray list = j.optJSONArray("teams");
            for (int i = 0; list != null && i < list.length() && out.length() < 10; i++) {
                JSONObject t = list.getJSONObject(i);
                if (!"Soccer".equals(t.optString("strSport"))) continue;
                out.put(withArabic(new JSONObject().put("id", t.optString("idTeam")).put("en", t.optString("strTeam"))
                    .put("league", t.optString("strLeague"))));
            }
        } catch (Exception ignored) { }
        return out.toString();
    }

    private JSONObject withArabic(JSONObject t) throws Exception {
        if (arNames == null) {
            Map<String, String> m = new HashMap<>();
            JSONObject o = new JSONObject(Place.asset(c, "content/teams-ar.json"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                if (!k.startsWith("_")) m.put(norm(k), o.getString(k));
            }
            arNames = m;
        }
        if (t.optString("ar").isEmpty()) t.put("ar", arNames.getOrDefault(norm(t.optString("en")), ""));
        return t;
    }

    /** Same as norm() in web/js/app.js: case, spaces and the words Al, FC, SC, Club do not matter. */
    static String norm(String s) {
        return (" " + s.toLowerCase(Locale.ROOT) + " ").replaceAll("\\b(al|fc|sc|club|saudi club)\\b", "").replaceAll("[^a-z0-9]", "");
    }

    static String lanAddress() {
        try {
            String best = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || !a.isSiteLocalAddress()) continue;
                    String name = ni.getName();
                    if (name.startsWith("wlan") || name.startsWith("eth")) return a.getHostAddress();
                    if (best == null) best = a.getHostAddress();
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "Error") + "\r\nContent-Type: " + type
            + "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff"
            + "\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\n') break;
            if (ch != '\r') b.write(ch);
            if (b.size() > 8192) return null;
        }
        return ch == -1 && b.size() == 0 ? null : b.toString("UTF-8");
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(b, off, n - off);
            if (r < 0) throw new IOException("short body");
            off += r;
        }
        return b;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }

    private static String param(String query, String name) {
        for (String kv : query.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(name)) {
                try { return URLDecoder.decode(kv.substring(i + 1), "UTF-8"); } catch (Exception e) { return null; }
            }
        }
        return null;
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
}
