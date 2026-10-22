package om.luban.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.SimpleDateFormat;
import android.icu.util.IslamicCalendar;
import android.icu.util.ULocale;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The home screen, a dock: clock, dates and the next prayer; a status line and buttons for inputs, the screensaver and
 * settings; one row of favourites ending in a tile for every app (AppsActivity). Each part lives in its own class; this
 * one only lays them out and keeps them current.
 */
public class HomeActivity extends Activity {
    private static java.lang.ref.WeakReference<HomeActivity> live = new java.lang.ref.WeakReference<>(null);
    private final Handler main = new Handler(Looper.getMainLooper());
    private Wallpaper wallpaper;
    private View scrim;
    private PrayerCard card;
    private TextView clock, ampm, dates, status;
    private LinearLayout favRow;
    private List<Apps.App> apps = new ArrayList<>();
    private boolean ar = true, arabicDigits = false, clock24 = false;
    private int hijriOffset = 0;
    /** the app rows are rebuilt only when something changed: rebuilding loads every app's banner (slow on small TVs) */
    private boolean rowsDirty = true;
    private int packagesSeq = -1;

    private final Runnable every30s = new Runnable() {
        @Override public void run() { card.tick(); main.postDelayed(this, 30_000); }
    };
    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { rowsDirty = true; buildRows(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.load(this);
        setContentView(build());
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        listen(packages, pf);
        live = new java.lang.ref.WeakReference<>(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        listen(time, tf);
        card.reload();
        readLanguage();
        updateClock();
        updateNetwork();
        android.content.pm.ChangedPackages changed = getPackageManager().getChangedPackages(Math.max(0, packagesSeq));
        if (changed != null) { packagesSeq = changed.getSequenceNumber(); rowsDirty |= !changed.getPackageNames().isEmpty(); }
        if (rowsDirty) buildRows();
        boolean photos = card.photos();
        scrim.setVisibility(photos ? View.VISIBLE : View.GONE);
        if (photos) wallpaper.resume(); else wallpaper.hide();
        main.postDelayed(every30s, 30_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(time);
        wallpaper.pause();
        main.removeCallbacks(every30s);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        if (live.get() == this) live.clear();
        super.onDestroy();
    }

    /** System broadcasts only (time, packages); not exported to other apps on Android 13+. */
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void listen(BroadcastReceiver r, IntentFilter f) {
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(r, f);
    }

    /** Back on the home screen goes nowhere; Home brings the focus back to the first favourite. */
    @Override
    public void onBackPressed() { }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (favRow != null && favRow.getChildCount() > 0) favRow.getChildAt(0).requestFocus();
    }

    /** Called by AdbReceiver after the favourites changed from ADB. */
    static void favouritesChanged() {
        HomeActivity a = live.get();
        if (a != null) a.main.post(() -> { a.rowsDirty = true; a.buildRows(); });
    }

    // ---------------------------------------------------------------- layout

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new SeaChart());
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        wallpaper = new Wallpaper(this, root);

        // over the photos (when the owner chose them): the deep sea, so the words stay readable
        scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            new int[] {0xF206192A, 0xB306192A, 0x6606192A, 0xA606192A}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        // a dock (960x540dp, any 1080p or 720p TV): the time at the top, a quiet status line and three buttons opposite,
        // one row of favourites at the bottom whose last tile opens every app; the middle is left to the sea chart
        int side = Ui.dp(this, 48), top = Ui.dp(this, 40);

        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        LinearLayout clockLine = new LinearLayout(this);
        clockLine.setOrientation(LinearLayout.HORIZONTAL);
        clockLine.setGravity(Gravity.BOTTOM);
        clockLine.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        clock = Ui.text(this, "", 64, Ui.medium(), Ui.INK);
        ampm = Ui.text(this, "", 20, Ui.medium(), Ui.LUBAN);
        ampm.setPadding(Ui.dp(this, 8), 0, 0, Ui.dp(this, 10));
        clockLine.addView(clock);
        clockLine.addView(ampm);
        time.addView(clockLine);
        // one line always: a long date steps its font down to 11 instead of breaking (and leaving "هـ" alone below)
        dates = Ui.text(this, "", 15, Ui.regular(), Ui.INK2);
        dates.setMaxLines(1);
        dates.setAutoSizeTextTypeUniformWithConfiguration(11, 15, 1, TypedValue.COMPLEX_UNIT_SP);
        int dh = (int) Math.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15, getResources().getDisplayMetrics()) * 1.4f);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 460), dh);
        dl.topMargin = Ui.dp(this, 6);
        time.addView(dates, dl);
        card = new PrayerCard(this);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.topMargin = Ui.dp(this, 10);
        time.addView(card.view, cl);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tl.setMarginStart(side);
        tl.topMargin = top;
        root.addView(time, tl);

        LinearLayout end = new LinearLayout(this);
        end.setOrientation(LinearLayout.HORIZONTAL);
        end.setGravity(Gravity.CENTER_VERTICAL);
        status = Ui.text(this, "", 13, Ui.regular(), Ui.INK3);   // city · network
        end.addView(status);
        end.addView(button(R.drawable.ic_inputs, "المداخل · Inputs", v -> inputs()));
        end.addView(button(R.drawable.ic_moon, "شاشة التوقف · Screensaver", v -> screensaver()));
        end.addView(button(R.drawable.ic_settings, "الإعدادات · Settings", v -> open(new Intent(Settings.ACTION_SETTINGS), null)));
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        el.setMarginEnd(side);
        el.topMargin = top;
        root.addView(end, el);

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        TextView label = Ui.text(this, "المفضلة · Favourites", 13, Ui.bold(), Ui.LUBAN_SOFT);
        label.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 2));
        dock.addView(label);
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setClipChildren(false);
        int p = Ui.dp(this, 8);   // room for the 7% lift of a focused tile
        sv.setPadding(p, p, p, p);
        favRow = new LinearLayout(this);
        favRow.setOrientation(LinearLayout.HORIZONTAL);
        favRow.setClipChildren(false);
        sv.addView(favRow);
        dock.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams kl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        kl.setMargins(side - p, 0, side - p, Ui.dp(this, 28));
        root.addView(dock, kl);
        return root;
    }

    private View button(int icon, String label, View.OnClickListener l) {
        View b = Tiles.roundButton(this, icon, label, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        lp.setMarginStart(Ui.dp(this, 10));
        b.setLayoutParams(lp);
        return b;
    }

    // ---------------------------------------------------------------- app rows

    private void buildRows() {
        rowsDirty = false;
        apps = Apps.load(this);
        View focused = getCurrentFocus();
        String focusedPkg = focused != null && focused.getTag() instanceof String ? (String) focused.getTag() : null;
        favRow.removeAllViews();
        List<String> fav = Favourites.get(this, apps);
        for (String p : fav) for (Apps.App a : apps) if (a.pkg.equals(p)) favRow.addView(dockTile(Tiles.app(this, a, apps, TILE_H, a.pkg, this::favouritesEdited)));
        favRow.addView(dockTile(Tiles.allApps(this, TILE_H)));
        View again = focusedPkg != null ? favRow.findViewWithTag(focusedPkg) : null;
        if (again != null) again.requestFocus();
        else if (focused == null || !focused.isAttachedToWindow()) favRow.getChildAt(0).requestFocus();
    }

    private static final int TILE_W = 120, TILE_H = 68;

    private View dockTile(View t) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, TILE_W), Ui.dp(this, TILE_H));
        lp.setMarginEnd(Ui.dp(this, 14));
        t.setLayoutParams(lp);
        return t;
    }

    private void favouritesEdited() { rowsDirty = true; buildRows(); }

    // ---------------------------------------------------------------- clock, network, inputs

    private void readLanguage() {
        // the screensaver's settings decide the language and digits of the whole home screen too
        try (android.database.Cursor c = getContentResolver().query(Uri.parse("content://om.luban.screen.config"), null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                org.json.JSONObject o = new org.json.JSONObject(c.getString(0));
                ar = !"en".equals(o.optString("lang"));
                arabicDigits = "arab".equals(o.optString("digits"));
                clock24 = o.optBoolean("clock24", false);
                hijriOffset = Math.max(-2, Math.min(2, o.optInt("hijriOffset", 0)));
            }
        } catch (Exception ignored) { }
    }

    private void updateClock() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int h = now.get(java.util.Calendar.HOUR_OF_DAY), m = now.get(java.util.Calendar.MINUTE);
        String hh = clock24 ? (h < 10 ? "0" : "") + h : String.valueOf(h % 12 == 0 ? 12 : h % 12);
        clock.setText(Ui.digits(hh + ":" + (m < 10 ? "0" : "") + m, arabicDigits));
        ampm.setText(clock24 ? "" : h < 12 ? (ar ? "ص" : "AM") : (ar ? "م" : "PM"));
        String nu = arabicDigits ? "arab" : "latn";
        ULocale greg = new ULocale((ar ? "ar_OM" : "en_GB") + "@numbers=" + nu);
        ULocale hij = new ULocale((ar ? "ar_OM" : "en_GB") + "@calendar=islamic-umalqura;numbers=" + nu);
        String g = new SimpleDateFormat("EEEE d MMMM y", greg).format(new Date());
        SimpleDateFormat hf = new SimpleDateFormat("d MMMM y", hij);
        IslamicCalendar ic = new IslamicCalendar(hij);
        ic.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        hf.setCalendar(ic);
        dates.setText(g + "  •  " + hf.format(new Date(System.currentTimeMillis() + hijriOffset * 86_400_000L)) + (ar ? " هـ" : " AH"));
    }

    private void updateNetwork() {
        String s = ar ? "غير متصل" : "Offline";
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (nc != null) {
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) s = ar ? "سلكي" : "Ethernet";
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s = ar ? "واي فاي" : "Wi-Fi";
            else s = ar ? "متصل" : "Online";
        }
        String city = card.city(ar);
        status.setText(city.isEmpty() ? s : city + " · " + s);
    }

    private void inputs() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<TvInputInfo> list = new ArrayList<>();
        if (tim != null) for (TvInputInfo i : tim.getTvInputList()) if (i.isPassthroughInput()) list.add(i);
        if (list.isEmpty()) { open(new Intent(Settings.ACTION_SETTINGS), null); return; }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) {
            CharSequence custom = list.get(i).loadCustomLabel(this);
            names[i] = String.valueOf(custom != null ? custom : list.get(i).loadLabel(this));
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(ar ? "المداخل" : "Inputs")
            .setItems(names, (d, w) -> open(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(list.get(w).getId())), null))
            .show();
    }

    private void screensaver() {
        Intent dream = new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator");
        Intent settings = getPackageManager().getLaunchIntentForPackage("om.luban.screen");
        open(dream, settings);
    }

    private void open(Intent first, Intent fallback) {
        try { startActivity(first.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) {
            if (fallback != null) try { startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
        }
    }
}
