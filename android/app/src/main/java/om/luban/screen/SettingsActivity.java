package om.luban.screen;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Opening the app: a QR code for the phone settings page, the state of the screen, and three buttons, on the same
 * sea chart as the screensaver. Everything is set from the phone, so the remote only has to move between three buttons.
 */
public class SettingsActivity extends Activity {
    private static final int LUBAN = 0xFFE2A94F, INK = 0xFFF1EBDD, INK2 = 0xBDF1EBDD, INK3 = 0x80F1EBDD;
    private static final int GLASS = 0xB30C2E45, LINE = 0x29F1EBDD, PAPER = 0xFFF7F1E3, NAVY = 0xFF06192A;
    private static final int QR_DP = 260;
    private PhoneServer server;
    private ImageView qr;
    private TextView url, status;
    private Typeface regular, bold;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        regular = font("native/ElMessiri-Regular.ttf");
        bold = font("native/ElMessiri-SemiBold.ttf");
        setContentView(build());
    }

    @Override
    protected void onStart() {
        super.onStart();
        server = new PhoneServer(this);
        new Thread(() -> {
            String link;
            try { link = server.start(); } catch (Exception e) { link = null; }
            final String l = link;
            runOnUiThread(() -> showLink(l));
        }).start();
        refreshStatus(null);
    }

    @Override
    protected void onStop() {
        if (server != null) server.stop();   // the settings page is only reachable while this screen is open
        server = null;
        super.onStop();
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(new SeaChart());
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(64), dp(24), dp(64), dp(24));   // the words and three two-line buttons fit 540dp
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setClipChildren(false);
        root.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // the mark beside the name, as one lockup
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        View mark = new View(this);
        mark.setBackground(new LubanMark());
        brand.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout name = new LinearLayout(this);
        name.setOrientation(LinearLayout.VERTICAL);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView word = new ImageView(this);
        word.setImageResource(R.drawable.wordmark);
        word.setImageTintList(android.content.res.ColorStateList.valueOf(LUBAN));
        word.setContentDescription("لُبان · Luban");
        name.addView(word, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)));
        word.setAdjustViewBounds(true);
        TextView latin = label("LUBAN", 11, regular, LUBAN, 2);
        latin.setLetterSpacing(.4f);
        latin.setPadding(dp(6), dp(2), dp(6), 0);   // the spacing after the last letter is not measured: the N was cut
        name.addView(latin);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.setMarginStart(dp(18));
        brand.addView(name, nl);
        text.addView(brand);

        text.addView(label("امسح الرمز بكاميرا جوالك", 30, bold, INK, 18));
        text.addView(label("لضبط المدينة والمواقيت والفرق وكل شيء.", 18, regular, INK2, 4));
        TextView en = label("Scan with your phone to set everything up.", 15, regular, INK3, 2);
        en.setTextDirection(View.TEXT_DIRECTION_LTR);   // its full stop stays at its end
        en.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        text.addView(en);

        // the address in a glass pill, on two lines on purpose: the TV and port, then the page
        url = label("…", 14, regular, LUBAN, 0);
        url.setTextDirection(View.TEXT_DIRECTION_LTR);
        url.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        url.setBackground(glass(11));
        url.setPadding(dp(14), dp(9), dp(14), dp(9));
        url.setLineSpacing(0, 1f);
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ul.topMargin = dp(18);
        text.addView(url, ul);

        status = label("", 14, regular, INK2, 16);
        text.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(18), 0, 0);
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        text.addView(buttons);
        Button preview = button("معاينة", "Preview", v -> startActivity(new Intent(this, PreviewActivity.class)));
        buttons.addView(preview);
        buttons.addView(button("تحديث", "Update", v -> {
            status.setText("…");
            new Thread(() -> { String s = Updates.check(getApplicationContext(), true); runOnUiThread(() -> refreshStatus(s)); }).start();
        }));
        Button saver = button("شاشة التوقف", "Screensaver", v -> {
            try { startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS)); }
            catch (ActivityNotFoundException e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        ((LinearLayout.LayoutParams) saver.getLayoutParams()).setMarginEnd(0);
        buttons.addView(saver);
        preview.requestFocus();

        // the code on warm paper in a glass frame, at the centre of the chart's rose
        LinearLayout qrCol = new LinearLayout(this);
        qrCol.setOrientation(LinearLayout.VERTICAL);
        qrCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout frame = new LinearLayout(this);
        frame.setBackground(glass(22));
        frame.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(17), dp(17), dp(17), dp(17));
        GradientDrawable paper = new GradientDrawable();
        paper.setColor(PAPER);
        paper.setCornerRadius(dp(13));
        tile.setBackground(paper);
        qr = new ImageView(this);
        tile.addView(qr, new LinearLayout.LayoutParams(dp(QR_DP), dp(QR_DP)));
        frame.addView(tile);
        qrCol.addView(frame);
        TextView caption = label("الجوال والتلفزيون على الشبكة نفسها\nPhone and TV on the same network", 14, regular, INK2, 14);
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        caption.setLineSpacing(0, 1.05f);
        qrCol.addView(caption);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(56));
        root.addView(qrCol, lp);
        return root;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);   // opened again from the home screen's tasks button: the next onStart links to the tasks
    }

    private void showLink(String link) {
        if (link == null) {
            url.setText("لا توجد شبكة: صِل التلفزيون بالواي فاي · No network");
            return;
        }
        // from the home screen's «مهام البيت» button: the phone page opens at the household tasks
        if ("tasks".equals(getIntent().getStringExtra("om.luban.screen.extra.SECTION"))) link += "#tasks";
        int path = link.indexOf('/', link.indexOf("//") + 2);
        url.setText(path > 0 ? link.substring(0, path) + "\n" + link.substring(path) : link);
        qr.setImageBitmap(qrBitmap(link, dp(QR_DP)));
    }

    private void refreshStatus(String update) {
        SpannableStringBuilder s = new SpannableStringBuilder("الإصدار " + BuildConfig.VERSION_NAME);
        dot(s);
        s.append("الشاشة ").append(String.valueOf(Updates.activeVersion(this)));
        dot(s);
        s.append("القناة ").append("canary".equals(Updates.channel(this)) ? "التجريبية" : "المستقرة");
        if (update != null) s.append("\n").append(update);
        status.setText(s);
    }

    /** A resin bullet between the parts, as between the two dates on the screensaver. */
    private static void dot(SpannableStringBuilder s) {
        int at = s.length();
        s.append("  •  ");
        s.setSpan(new ForegroundColorSpan(LUBAN), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Navy modules on warm paper; a one-module margin, the tile around it is the rest of the quiet zone. */
    static Bitmap qrBitmap(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) px[y * size + x] = m.get(x, y) ? NAVY : PAPER;
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            return null;
        }
    }

    private GradientDrawable glass(int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(GLASS);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1, dp(1)), LINE);
        return g;
    }

    private TextView label(String s, int sp, Typeface tf, int color, int topDp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setPadding(0, dp(topDp), 0, 0);
        t.setLineSpacing(0, 1.25f);
        return t;
    }

    /** Arabic over English, the English smaller and quieter. */
    private Button button(String ar, String en, View.OnClickListener l) {
        SpannableStringBuilder s = new SpannableStringBuilder(ar).append("\n");
        int at = s.length();
        s.append(en);
        s.setSpan(new RelativeSizeSpan(.8f), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(INK2), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTypeface(bold);
        b.setTextColor(INK);
        b.setOnClickListener(l);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        // three share the row beside the QR code, the words stepping down (to 11) rather than cut off
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMaxLines(2);
        b.setLineSpacing(0, 0.95f);
        b.setAutoSizeTextTypeUniformWithConfiguration(11, 17, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        b.setStateListAnimator(null);   // no system shadow under the glass
        GradientDrawable normal = glass(13);
        normal.setColor(0xD10C2E45);
        b.setBackground(normal);
        b.setOnFocusChangeListener((v, has) -> {
            normal.setStroke(has ? dp(2) : Math.max(1, dp(1)), has ? LUBAN : LINE);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(150).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1f);
        lp.setMarginEnd(dp(12));
        b.setLayoutParams(lp);
        return b;
    }

    private Typeface font(String path) {
        try { return Typeface.createFromAsset(getAssets(), path); } catch (Exception e) { return Typeface.DEFAULT; }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
