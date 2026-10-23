package om.luban.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** An app's tile and its hold-OK menu: the same on the home screen and on the all-apps page. */
final class Tiles {
    private Tiles() {}

    /** A tile of the given height; the caller sizes and places it. {@code changed} runs after the favourites change. */
    static FrameLayout app(Activity c, Apps.App a, List<Apps.App> apps, int heightDp, String tag, Runnable changed) {
        return app(c, a, apps, heightDp, tag, changed, false);
    }

    /** {@code favourite}: a resin dot in the corner, for the all-apps page (on the home screen every tile is one). */
    static FrameLayout app(Activity c, Apps.App a, List<Apps.App> apps, int heightDp, String tag, Runnable changed, boolean favourite) {
        FrameLayout t = new FrameLayout(c);
        t.setTag(tag);
        t.setClipToOutline(true);
        t.setElevation(Ui.dp(c, 3));   // a soft shadow: the tile lies on the chart, not printed on it
        Ui.focusable(t, Ui.glass(c, 12));
        ImageView img = new ImageView(c);
        img.setImageDrawable(Apps.art(c, a));
        if (Apps.hasBanner(c, a)) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            t.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            // no banner: the icon above its name, both scaled to the tile
            int icon = Ui.dp(c, heightDp * 0.46f);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(icon, icon, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            ip.topMargin = Ui.dp(c, heightDp * 0.12f);
            t.addView(img, ip);
            TextView label = Ui.text(c, a.label, 12, Ui.medium(), Ui.INK);
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            // a long name on a small tile steps down instead of being cut
            label.setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            lp.setMargins(Ui.dp(c, 6), 0, Ui.dp(c, 6), Ui.dp(c, 6));
            t.addView(label, lp);
        }
        if (favourite) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Ui.LUBAN);
            d.setStroke(Ui.dp(c, 1.5f), Ui.DEEP);
            android.view.View dot = new android.view.View(c);
            dot.setBackground(d);
            int s = Ui.dp(c, 8);
            FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(s, s, Gravity.TOP | Gravity.START);
            dl.setMargins(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), 0);
            t.addView(dot, dl);
        }
        t.setContentDescription(a.label);
        t.setOnClickListener(v -> Apps.open(c, a));
        t.setOnLongClickListener(v -> { menu(c, a, apps, changed); return true; });
        t.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { menu(c, a, apps, changed); return true; }
            return false;
        });
        return t;
    }

    /** The last tile of the favourites row: opens every app. */
    static FrameLayout allApps(Activity c, int heightDp) {
        FrameLayout t = new FrameLayout(c);
        t.setTag("all-apps");
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0);
        bg.setCornerRadius(Ui.dp(c, 12));
        bg.setStroke(Ui.dp(c, 1), 0x66E2A94F, Ui.dp(c, 4), Ui.dp(c, 3));
        Ui.focusable(t, bg);
        LinearLayout in = new LinearLayout(c);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setGravity(Gravity.CENTER);
        ImageView grid = new ImageView(c);
        grid.setImageResource(R.drawable.ic_grid);
        grid.setImageTintList(ColorStateList.valueOf(Ui.LUBAN_SOFT));
        int s = Ui.dp(c, heightDp * 0.27f);
        in.addView(grid, new LinearLayout.LayoutParams(s, s));
        // the tile's whole width, centred, one line: a font with wide letters steps down instead of being cut
        TextView label = Ui.text(c, "جميع التطبيقات", 13, Ui.medium(), Ui.LUBAN_SOFT);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, Ui.dp(c, 22));
        ll.setMargins(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), 0);
        in.addView(label, ll);
        t.addView(in, new FrameLayout.LayoutParams(-1, -1));
        t.setContentDescription("جميع التطبيقات · All apps");
        t.setOnClickListener(v -> c.startActivity(new Intent(c, AppsActivity.class)));
        return t;
    }

    /** Hold OK (or press Menu) on an app: add or remove from favourites, move it, or open its info. */
    static void menu(Activity c, Apps.App a, List<Apps.App> apps, Runnable changed) {
        List<String> fav = Favourites.get(c, apps);
        boolean in = fav.contains(a.pkg);
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(in ? "إزالة من المفضلة · Remove from favourites" : "إضافة إلى المفضلة · Add to favourites");
        actions.add(() -> Favourites.toggle(c, apps, a.pkg));
        if (in) {
            items.add("تحريك لليمين · Move right");
            actions.add(() -> Favourites.move(c, apps, a.pkg, -1));
            items.add("تحريك لليسار · Move left");
            actions.add(() -> Favourites.move(c, apps, a.pkg, 1));
        }
        items.add("معلومات التطبيق · App info");
        actions.add(() -> {
            try {
                c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        });
        new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(a.label)
            .setItems(items.toArray(new String[0]), (d, which) -> { actions.get(which).run(); changed.run(); })
            .show();
    }
}
