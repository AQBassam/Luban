package om.luban.home;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Every app, seven to a row on the sea chart, opened from the last tile of the home screen's favourites. The head names
 * the app in focus (many banners carry no words); a resin dot marks the favourites. Back returns home; hold OK on an
 * app to add it to the favourites there.
 */
public class AppsActivity extends Activity {
    private static final int COLUMNS = 7, TILE_H = 58, GAP = 22;
    private LinearLayout grid;
    private TextView count, selected;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.load(this);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildGrid();   // apps may have been installed or removed meanwhile
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(new SeaChart(.91f, .11f));   // the rose behind the mark in the head
        int side = Ui.dp(this, 64);

        // the head: the mark, the title and how many, and on the far side the app in focus
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(side, Ui.dp(this, 30), side, Ui.dp(this, 18));
        View mark = new View(this);
        mark.setBackground(new LubanMark());
        head.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        TextView title = Ui.text(this, "جميع التطبيقات", 28, Ui.bold(), Ui.INK);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.setMarginStart(Ui.dp(this, 14));
        head.addView(title, tl);
        count = Ui.text(this, "", 13, Ui.bold(), Ui.DEEP);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Ui.LUBAN);
        pill.setCornerRadius(Ui.dp(this, 20));
        count.setBackground(pill);
        count.setPadding(Ui.dp(this, 10), Ui.dp(this, 3), Ui.dp(this, 10), Ui.dp(this, 3));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.setMarginStart(Ui.dp(this, 14));
        head.addView(count, cl);
        selected = Ui.text(this, "", 15, Ui.medium(), Ui.LUBAN_SOFT);
        selected.setSingleLine(true);
        selected.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -2, 1f);
        sl.setMarginStart(Ui.dp(this, 24));
        head.addView(selected, sl);
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // the grid scrolls under a fade at the bottom, so tiles do not stop at a hard edge
        FrameLayout body = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(side, Ui.dp(this, 8), side, Ui.dp(this, 48));
        col.setClipChildren(false);    // a focused tile at the edge lifts a little past the grid
        col.setClipToPadding(false);   // ...into the side margins, where it was cut before
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        col.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] {Ui.DEEP, 0x0006192A}));
        body.addView(fade, new FrameLayout.LayoutParams(-1, Ui.dp(this, 44), Gravity.BOTTOM));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        // the keys, on one quiet line at the foot
        SpannableStringBuilder keys = new SpannableStringBuilder();
        key(keys, "OK", " فتح");
        key(keys, "OK مطوّلاً", " المفضلة والترتيب");
        key(keys, "Back", " العودة");
        key(keys, "●", " في المفضلة");
        TextView foot = Ui.text(this, "", 13, Ui.regular(), Ui.INK3);
        foot.setText(keys);
        foot.setTextDirection(View.TEXT_DIRECTION_RTL);
        foot.setPadding(side, Ui.dp(this, 4), side, Ui.dp(this, 18));
        root.addView(foot, new LinearLayout.LayoutParams(-1, -2));

        root.getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> showSelected(now));
        return root;
    }

    /** One key and what it does. A right-to-left mark on each side keeps a Latin key ("OK", "Back") beside its own words. */
    private static void key(SpannableStringBuilder s, String key, String what) {
        if (s.length() > 0) s.append("\u200F      ");
        s.append("\u200F");
        int at = s.length();
        s.append(key);
        s.setSpan(new ForegroundColorSpan(Ui.LUBAN), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.append("\u200F").append(what);
    }

    /** The name of the app in focus, beside the title. */
    private void showSelected(View now) {
        CharSequence name = now != null && now.getTag() instanceof String ? now.getContentDescription() : null;
        if (name == null) { selected.setText(""); return; }
        SpannableStringBuilder s = new SpannableStringBuilder("المحدد: ");
        s.setSpan(new ForegroundColorSpan(Ui.INK3), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.append(name);
        selected.setText(s);
    }

    private void buildGrid() {
        List<Apps.App> apps = Apps.load(this);
        List<String> fav = Favourites.get(this, apps);
        count.setText(String.valueOf(apps.size()));
        View focused = getCurrentFocus();
        Object focusedTag = focused != null ? focused.getTag() : null;
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, Ui.dp(this, TILE_H));
                rl.bottomMargin = Ui.dp(this, GAP);
                grid.addView(row, rl);
            }
            Apps.App a = apps.get(i);
            View t = Tiles.app(this, a, apps, TILE_H, a.pkg, () -> { HomeActivity.favouritesChanged(); buildGrid(); }, fav.contains(a.pkg));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i % COLUMNS != COLUMNS - 1) lp.setMarginEnd(Ui.dp(this, GAP));
            row.addView(t, lp);
        }
        // a short last row keeps its tiles the same width as the others
        if (row != null) for (int i = row.getChildCount(); i < COLUMNS; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i != COLUMNS - 1) lp.setMarginEnd(Ui.dp(this, GAP));
            row.addView(new View(this), lp);
        }
        View again = focusedTag != null ? grid.findViewWithTag(focusedTag) : null;
        if (again != null) again.requestFocus();
        else if (!apps.isEmpty()) ((LinearLayout) grid.getChildAt(0)).getChildAt(0).requestFocus();
        showSelected(getCurrentFocus());
    }
}
