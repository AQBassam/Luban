package om.luban.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The home screen: clock and dates, the prayer card, favourites, all apps, and buttons for inputs, network and settings.
 * Each part lives in its own class; this one only lays them out and keeps them current.
 */
public class HomeActivity extends Activity {
    private static HomeActivity live;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Wallpaper wallpaper;
    private PrayerCard card;
    private TextView clock, ampm, dates, network;
    private LinearLayout favRow, allRow;
    private List<Apps.App> apps = new ArrayList<>();
    private boolean ar = true, arabicDigits = false, clock24 = false;

    private final Runnable every30s = new Runnable() {
        @Override public void run() { card.tick(); main.postDelayed(this, 30_000); }
    };
    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { buildRows(); }
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
        live = this;
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
        buildRows();
        wallpaper.resume();
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
        if (live == this) live = null;
        super.onDestroy();
    }

    /** System broadcasts only (time, packages); not exported to other apps on Android 13+. */
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
        HomeActivity a = live;
        if (a != null) a.main.post(a::buildRows);
    }

    // ---------------------------------------------------------------- layout

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1013);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        wallpaper = new Wallpaper(this, root);

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            new int[] {0xF006090B, 0x9906090B, 0x3306090B, 0x8806090B}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 44);
        col.setPadding(pad, Ui.dp(this, 32), pad, Ui.dp(this, 28));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // top: clock and dates, buttons on the other side
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        LinearLayout clockLine = new LinearLayout(this);
        clockLine.setOrientation(LinearLayout.HORIZONTAL);
        clockLine.setGravity(Gravity.BOTTOM);
        clockLine.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        clock = Ui.text(this, "", 64, Ui.heavy(), Ui.INK);
        ampm = Ui.text(this, "", 20, Ui.medium(), Ui.INK2);
        ampm.setPadding(Ui.dp(this, 10), 0, 0, Ui.dp(this, 12));
        clockLine.addView(clock);
        clockLine.addView(ampm);
        time.addView(clockLine);
        View band = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[] {Ui.RED, Ui.INK, Ui.GREEN});
        g.setCornerRadius(Ui.dp(this, 2));
        band.setBackground(g);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 4));
        bl.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 10));
        time.addView(band, bl);
        dates = Ui.text(this, "", 17, Ui.regular(), Ui.INK2);
        time.addView(dates);
        top.addView(time, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        network = pill("", v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS), new Intent(Settings.ACTION_SETTINGS)));
        buttons.addView(network);
        buttons.addView(pill("المداخل · Inputs", v -> inputs()));
        buttons.addView(pill("شاشة التوقف", v -> screensaver()));
        buttons.addView(pill("الإعدادات", v -> open(new Intent(Settings.ACTION_SETTINGS), null)));
        top.addView(buttons);

        // spacer, then the prayer card, favourites and all apps at the bottom
        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));
        card = new PrayerCard(this);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.setMargins(0, 0, 0, Ui.dp(this, 22));
        col.addView(card.view, cl);

        col.addView(section("المفضلة · Favourites"));
        favRow = row(col);
        col.addView(section("كل التطبيقات · All apps"));
        allRow = row(col);
        return root;
    }

    private TextView section(String s) {
        TextView t = Ui.text(this, s, 15, Ui.bold(), Ui.LUBAN_SOFT);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), 0, Ui.dp(this, 10));
        return t;
    }

    private LinearLayout row(LinearLayout col) {
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setClipChildren(false);
        int p = Ui.dp(this, 10);
        sv.setPadding(p, p, p, p);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        sv.addView(r);
        col.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    private TextView pill(String s, View.OnClickListener l) {
        TextView t = Ui.text(this, s, 15, Ui.medium(), Ui.INK);
        int h = Ui.dp(this, 16), v = Ui.dp(this, 10);
        t.setPadding(h, v, h, v);
        Ui.focusable(t, Ui.glass(this, 99));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(Ui.dp(this, 10));
        t.setLayoutParams(lp);
        return t;
    }

    // ---------------------------------------------------------------- app rows

    private void buildRows() {
        apps = Apps.load(this);
        View focused = getCurrentFocus();
        String focusedPkg = focused != null && focused.getTag() instanceof String ? (String) focused.getTag() : null;
        favRow.removeAllViews();
        allRow.removeAllViews();
        List<String> fav = Favourites.get(this, apps);
        for (String p : fav) for (Apps.App a : apps) if (a.pkg.equals(p)) favRow.addView(tile(a, true));
        for (Apps.App a : apps) allRow.addView(tile(a, false));
        View again = focusedPkg != null ? favRow.findViewWithTag(focusedPkg) : null;
        if (again != null) again.requestFocus();
        else if (focused == null || !focused.isAttachedToWindow()) {
            if (favRow.getChildCount() > 0) favRow.getChildAt(0).requestFocus();
            else if (allRow.getChildCount() > 0) allRow.getChildAt(0).requestFocus();
        }
    }

    private View tile(Apps.App a, boolean favourite) {
        FrameLayout t = new FrameLayout(this);
        t.setTag(favourite ? a.pkg : "all:" + a.pkg);
        t.setClipToOutline(true);
        Ui.focusable(t, Ui.glass(this, 14));
        Drawable art = Apps.art(this, a);
        ImageView img = new ImageView(this);
        img.setImageDrawable(art);
        if (Apps.hasBanner(this, a)) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            t.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            ip.topMargin = Ui.dp(this, 16);
            t.addView(img, ip);
            TextView label = Ui.text(this, a.label, 13, Ui.medium(), Ui.INK);
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            lp.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 12));
            t.addView(label, lp);
        }
        t.setContentDescription(a.label);
        t.setOnClickListener(v -> Apps.open(this, a));
        t.setOnLongClickListener(v -> { menu(a); return true; });
        t.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { menu(a); return true; }
            return false;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, favourite ? 208 : 176), Ui.dp(this, favourite ? 117 : 99));
        lp.setMarginEnd(Ui.dp(this, 16));
        t.setLayoutParams(lp);
        return t;
    }

    /** Hold OK (or press Menu) on an app: add or remove from favourites, move it, or open its info. */
    private void menu(Apps.App a) {
        List<String> fav = Favourites.get(this, apps);
        boolean in = fav.contains(a.pkg);
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(in ? "إزالة من المفضلة · Remove from favourites" : "إضافة إلى المفضلة · Add to favourites");
        actions.add(() -> Favourites.toggle(this, apps, a.pkg));
        if (in) {
            items.add("تحريك لليمين · Move right");
            actions.add(() -> Favourites.move(this, apps, a.pkg, -1));
            items.add("تحريك لليسار · Move left");
            actions.add(() -> Favourites.move(this, apps, a.pkg, 1));
        }
        items.add("معلومات التطبيق · App info");
        actions.add(() -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)), null));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(a.label)
            .setItems(items.toArray(new String[0]), (d, which) -> { actions.get(which).run(); buildRows(); })
            .show();
    }

    // ---------------------------------------------------------------- clock, network, inputs

    private void readLanguage() {
        // the screensaver's settings decide the language and digits of the whole home screen too
        try (android.database.Cursor c = getContentResolver().query(Uri.parse("content://om.luban.screen.config"), null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                org.json.JSONObject o = new org.json.JSONObject(c.getString(0));
                ar = !"en".equals(o.optString("lang"));
                arabicDigits = "arab".equals(o.optString("digits"));
                clock24 = o.optBoolean("clock24", false);
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
        dates.setText(g + "  •  " + hf.format(new Date()) + (ar ? " هـ" : " AH"));
    }

    private void updateNetwork() {
        String s = ar ? "غير متصل" : "Offline";
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (nc != null) {
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) s = ar ? "سلكي · Ethernet" : "Ethernet";
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s = ar ? "واي فاي · Wi-Fi" : "Wi-Fi";
            else s = ar ? "متصل" : "Online";
        }
        network.setText(s);
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
