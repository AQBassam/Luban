package om.luban.home;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Every app, six to a row, opened from the last tile of the home screen's favourites. Back returns home; hold OK on an
 * app to add it to the favourites there.
 */
public class AppsActivity extends Activity {
    private static final int COLUMNS = 6, TILE_H = 72;
    private LinearLayout grid;

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
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.DEEP);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 48);
        col.setPadding(side, Ui.dp(this, 40), side, Ui.dp(this, 36));
        col.setClipChildren(false);   // a focused tile at the edge lifts a little past the grid
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        head.addView(Ui.text(this, "كل التطبيقات", 26, Ui.bold(), Ui.INK));
        TextView hint = Ui.text(this, "اضغط مطوّلاً على تطبيق لإضافته إلى المفضلة · Back للعودة", 13, Ui.regular(), Ui.INK3);
        hint.setPaddingRelative(Ui.dp(this, 14), 0, 0, Ui.dp(this, 4));   // the gap on the title's side, in either direction
        head.addView(hint);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.bottomMargin = Ui.dp(this, 18);
        col.addView(head, hl);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        col.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private void buildGrid() {
        List<Apps.App> apps = Apps.load(this);
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
                rl.bottomMargin = Ui.dp(this, 16);
                grid.addView(row, rl);
            }
            Apps.App a = apps.get(i);
            View t = Tiles.app(this, a, apps, TILE_H, a.pkg, HomeActivity::favouritesChanged);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i % COLUMNS != COLUMNS - 1) lp.setMarginEnd(Ui.dp(this, 18));
            row.addView(t, lp);
        }
        // a short last row keeps its tiles the same width as the others
        if (row != null) for (int i = row.getChildCount(); i < COLUMNS; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i != COLUMNS - 1) lp.setMarginEnd(Ui.dp(this, 18));
            row.addView(new View(this), lp);
        }
        View again = focusedTag != null ? grid.findViewWithTag(focusedTag) : null;
        if (again != null) again.requestFocus();
        else if (!apps.isEmpty()) ((LinearLayout) grid.getChildAt(0)).getChildAt(0).requestFocus();
    }
}
