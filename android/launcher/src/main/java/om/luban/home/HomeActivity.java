package om.luban.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Rect;
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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
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
 * The home screen, a dock: a greeting, the clock, the dates and the next prayer; named buttons for the network, inputs,
 * the screensaver and settings, with the photo's caption; one row of favourites ending in a tile for every app
 * (AppsActivity). Each part lives in its own class; this one only lays them out and keeps them current.
 */
public class HomeActivity extends Activity {
    private static java.lang.ref.WeakReference<HomeActivity> live = new java.lang.ref.WeakReference<>(null);
    private final Handler main = new Handler(Looper.getMainLooper());
    private Wallpaper wallpaper;
    private View scrim;
    private PrayerCard card;
    private TextView greeting, clock, ampm, dates, caption, tasksLabel, networkLabel, inputsLabel, screensaverLabel, settingsLabel;
    private ImageView networkIcon;
    private LinearLayout clockLine;
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
        updateLabels();
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
        if (favRow != null && favRow.getChildCount() > 0) { favRow.getChildAt(0).requestFocus(); showFocused(); }
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
            new int[] {0xF206192A, 0xBF06192A, 0x8C06192A, 0xBF06192A}));   // deeper behind the words at the top
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        // a dock (960x540dp, any 1080p or 720p TV): the time at the top, the buttons opposite, one row of favourites at
        // the bottom whose last tile opens every app; the middle is left to the sea chart or the photo
        int side = Ui.dp(this, 48), top = Ui.dp(this, 40);

        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        greeting = Ui.text(this, "", 16, Ui.medium(), Ui.LUBAN_SOFT);   // good morning / good evening
        time.addView(greeting, new LinearLayout.LayoutParams(-2, -2));
        // the clock, then صباحًا / مساءً after it in reading order (to its left in Arabic, AM/PM to its right in English)
        clockLine = new LinearLayout(this);
        clockLine.setOrientation(LinearLayout.HORIZONTAL);
        clockLine.setGravity(Gravity.BOTTOM);
        clock = Ui.text(this, "", 64, Ui.medium(), Ui.INK);
        clock.setTextDirection(View.TEXT_DIRECTION_LTR);
        ampm = Ui.text(this, "", 18, Ui.medium(), Ui.LUBAN);
        ampm.setPaddingRelative(Ui.dp(this, 10), 0, 0, Ui.dp(this, 12));
        clockLine.addView(clock);
        clockLine.addView(ampm);
        // its own width only: the column's full width would push a left-to-right line to the far side
        time.addView(clockLine, new LinearLayout.LayoutParams(-2, -2));
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

        // five pills, an icon then its name: household tasks, network, inputs, screensaver, settings; the photo's caption below
        LinearLayout end = new LinearLayout(this);
        end.setOrientation(LinearLayout.VERTICAL);
        end.setGravity(Gravity.END);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        tasksLabel = pill(buttons, R.drawable.ic_tasks, v -> tasks());
        networkLabel = pill(buttons, R.drawable.ic_offline, v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS), new Intent(Settings.ACTION_SETTINGS)));
        networkIcon = (ImageView) ((LinearLayout) networkLabel.getParent()).getChildAt(0);
        inputsLabel = pill(buttons, R.drawable.ic_inputs, v -> inputs());
        screensaverLabel = pill(buttons, R.drawable.ic_moon, v -> screensaver());
        settingsLabel = pill(buttons, R.drawable.ic_settings, v -> open(new Intent(Settings.ACTION_SETTINGS), null));
        end.addView(buttons);
        caption = Ui.text(this, "", 12, Ui.regular(), Ui.INK3);   // the photo: place · photographer, licence
        caption.setPadding(0, Ui.dp(this, 10), 0, 0);
        end.addView(caption);
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        el.setMarginEnd(side);
        el.topMargin = top;
        root.addView(end, el);
        wallpaper.onChange = this::updateCaption;

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        TextView label = Ui.text(this, "المفضلة · Favourites", 13, Ui.bold(), Ui.LUBAN_SOFT);
        label.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 2));
        dock.addView(label);
        int p = Ui.dp(this, 8);   // room for the 7% lift of a focused tile
        HorizontalScrollView sv = new HorizontalScrollView(this) {
            // scroll a focused tile in with its lift too, so a tile at either end of the row is never cut at the edge
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                Rect wide = new Rect(r);
                wide.inset(-p, 0);
                return super.computeScrollDeltaToGetChildRectOnScreen(wide);
            }
        };
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setClipChildren(false);
        sv.setPadding(0, p, 0, p);
        favRow = new LinearLayout(this);
        favRow.setOrientation(LinearLayout.HORIZONTAL);
        favRow.setClipChildren(false);
        favRow.setPaddingRelative(p, 0, p, 0);   // inside the scrolled row, so both ends keep it
        favRow.setClipToPadding(false);          // ...and the lift may draw into it
        sv.addView(favRow);
        dock.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams kl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        kl.setMargins(side - p, 0, side - p, Ui.dp(this, 28));
        root.addView(dock, kl);
        return root;
    }

    /** A glass pill, its icon then its name, added to the row; returns the name, which follows the language. */
    private TextView pill(LinearLayout row, int icon, View.OnClickListener l) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPaddingRelative(Ui.dp(this, 14), 0, Ui.dp(this, 16), 0);
        Ui.focusable(p, Ui.glass(this, 99));
        p.setOnClickListener(l);
        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        i.setImageTintList(ColorStateList.valueOf(Ui.INK));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18));
        il.setMarginEnd(Ui.dp(this, 8));
        p.addView(i, il);
        TextView name = Ui.text(this, "", 14, Ui.medium(), Ui.INK);
        p.addView(name);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(this, 40));
        lp.setMarginStart(Ui.dp(this, 10));
        row.addView(p, lp);
        return name;
    }

    /** The buttons' names, and what they say to a screen reader, in the screensaver's language. */
    private void updateLabels() {
        name(tasksLabel, ar ? "مهام البيت" : "Tasks");
        name(inputsLabel, ar ? "المداخل" : "Inputs");
        name(screensaverLabel, ar ? "شاشة التوقف" : "Screensaver");
        name(settingsLabel, ar ? "الإعدادات" : "Settings");
    }

    private static void name(TextView label, String s) {
        label.setText(s);
        ((View) label.getParent()).setContentDescription(s);   // the pill it sits in
    }

    private void updateCaption() { caption.setText(wallpaper.caption(ar)); }

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
        // the row ends at both ends: Left past the last tile must not jump up to the buttons
        View first = favRow.getChildAt(0), last = favRow.getChildAt(favRow.getChildCount() - 1);
        for (View t : new View[] {first, last}) if (t.getId() == View.NO_ID) t.setId(View.generateViewId());
        first.setNextFocusRightId(first.getId());   // right to left: the first tile is the rightmost
        last.setNextFocusLeftId(last.getId());
        View again = focusedPkg != null ? favRow.findViewWithTag(focusedPkg) : null;
        if (again != null) again.requestFocus();
        else if (focused == null || !focused.isAttachedToWindow()) first.requestFocus();
        showFocused();
    }

    /** Once laid out, scroll the focused tile fully into view (a right-to-left row does not, when it was built unseen). */
    private void showFocused() {
        favRow.post(() -> {
            View f = getCurrentFocus();
            if (f != null && f.getParent() == favRow) f.requestRectangleOnScreen(new Rect(0, 0, f.getWidth(), f.getHeight()), true);
        });
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
        ampm.setText(clock24 ? "" : h < 12 ? (ar ? "صباحًا" : "AM") : (ar ? "مساءً" : "PM"));
        clockLine.setLayoutDirection(ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        greeting.setText(h < 12 ? (ar ? "صباح الخير" : "Good morning") : (ar ? "مساء الخير" : "Good evening"));
        // the day, the Hijri date, then the Gregorian: الخميس 11 جمادى الأولى 1448 هـ · 22 أكتوبر 2026 م
        String nu = arabicDigits ? "arab" : "latn";
        ULocale greg = new ULocale((ar ? "ar_OM" : "en_GB") + "@numbers=" + nu);
        ULocale hij = new ULocale((ar ? "ar_OM" : "en_GB") + "@calendar=islamic-umalqura;numbers=" + nu);
        Date today = new Date();
        String day = new SimpleDateFormat("EEEE", greg).format(today), g = new SimpleDateFormat("d MMMM y", greg).format(today);
        SimpleDateFormat hf = new SimpleDateFormat("d MMMM y", hij);
        IslamicCalendar ic = new IslamicCalendar(hij);
        ic.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        hf.setCalendar(ic);
        String hijri = hf.format(new Date(System.currentTimeMillis() + hijriOffset * 86_400_000L));
        // a resin bullet between the two dates, as on the screensaver
        SpannableStringBuilder d = new SpannableStringBuilder(day + " " + hijri + (ar ? " هـ" : " AH") + "  ");
        int dot = d.length();
        d.append("•");
        d.setSpan(new ForegroundColorSpan(Ui.LUBAN), dot, dot + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        d.setSpan(new RelativeSizeSpan(1.3f), dot, dot + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        d.append("  ").append(g).append(ar ? " م" : "");
        dates.setText(d);
    }

    /** The network button: its icon and name follow the connection (wired, Wi-Fi, or none). */
    private void updateNetwork() {
        String s = ar ? "غير متصل" : "Offline";
        int icon = R.drawable.ic_offline;
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (nc != null) {
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) { s = ar ? "سلكي" : "Ethernet"; icon = R.drawable.ic_ethernet; }
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { s = ar ? "واي فاي" : "Wi-Fi"; icon = R.drawable.ic_wifi; }
            else { s = ar ? "متصل" : "Online"; icon = R.drawable.ic_ethernet; }
        }
        networkIcon.setImageResource(icon);
        name(networkLabel, s);
        updateCaption();
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

    /** The screensaver's QR screen, its phone page opening at the household tasks (the page works only while it is open). */
    private void tasks() {
        Intent i = new Intent().setClassName("om.luban.screen", "om.luban.screen.SettingsActivity").putExtra("om.luban.screen.extra.SECTION", "tasks");
        open(i, getPackageManager().getLaunchIntentForPackage("om.luban.screen"));
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
