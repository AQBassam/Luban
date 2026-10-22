package om.luban.screen;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
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

import java.util.Collections;

/**
 * Opening the app: a QR code for the phone settings page, the state of the screen, and three buttons.
 * Everything is set from the phone, so the remote only has to move between three buttons.
 */
public class SettingsActivity extends Activity {
    private static final int LUBAN = 0xFFE2A94F, INK = 0xFFF1EBDD, INK2 = 0xBDF1EBDD, BG = 0xFF06192A, CARD = 0xFF0C2E45;
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
        root.setBackgroundColor(BG);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(64), dp(40), dp(64), dp(40));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        root.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View mark = new View(this);
        mark.setBackground(new LubanMark());
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(dp(72), dp(72));
        fl.gravity = Gravity.START;
        text.addView(mark, fl);

        text.addView(label("لُبان", 44, bold, INK, 12));
        text.addView(label("امسح الرمز بكاميرا جوالك لضبط المدينة والمواقيت والفرق وكل شيء.\nScan with your phone to set everything up.", 20, regular, INK2, 8));
        url = label("…", 16, regular, LUBAN, 16);
        url.setTextDirection(View.TEXT_DIRECTION_LTR);
        text.addView(url);
        status = label("", 16, regular, INK2, 20);
        text.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(28), 0, 0);
        text.addView(buttons);
        Button preview = button("معاينة\nPreview", v -> startActivity(new Intent(this, PreviewActivity.class)));
        buttons.addView(preview);
        buttons.addView(button("تحديث\nUpdate", v -> {
            status.setText("…");
            new Thread(() -> { String s = Updates.check(getApplicationContext(), true); runOnUiThread(() -> refreshStatus(s)); }).start();
        }));
        buttons.addView(button("شاشة التوقف\nScreensaver", v -> {
            try { startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS)); }
            catch (ActivityNotFoundException e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        }));
        preview.requestFocus();

        LinearLayout qrBox = new LinearLayout(this);
        qrBox.setGravity(Gravity.CENTER);
        qrBox.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE);
        white.setCornerRadius(dp(18));
        qrBox.setBackground(white);
        qr = new ImageView(this);
        qrBox.addView(qr, new LinearLayout.LayoutParams(dp(300), dp(300)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(56));
        root.addView(qrBox, lp);
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
        url.setText(link);
        qr.setImageBitmap(qrBitmap(link, dp(300)));
    }

    private void refreshStatus(String update) {
        status.setText("الإصدار " + BuildConfig.VERSION_NAME + " · الشاشة " + Updates.activeVersion(this)
            + " · القناة " + ("canary".equals(Updates.channel(this)) ? "التجريبية" : "المستقرة")
            + (update != null ? "\n" + update : ""));
    }

    static Bitmap qrBitmap(String text, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
                Collections.singletonMap(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) px[y * size + x] = m.get(x, y) ? 0xFF111111 : 0xFFFFFFFF;
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            return null;
        }
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

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTypeface(bold);
        b.setTextColor(INK);
        b.setOnClickListener(l);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        // three share the row beside the QR code: Arabic over English, the words stepping down (to 11) rather than cut off
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMaxLines(2);
        b.setLineSpacing(0, 0.95f);
        b.setAutoSizeTextTypeUniformWithConfiguration(11, 17, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(CARD);
        normal.setCornerRadius(dp(14));
        normal.setStroke(dp(2), 0x33F7F4EE);
        b.setBackground(normal);
        b.setOnFocusChangeListener((v, has) -> {
            normal.setStroke(dp(3), has ? LUBAN : 0x33F7F4EE);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(150).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(76), 1f);
        lp.setMarginEnd(dp(14));
        b.setLayoutParams(lp);
        return b;
    }

    private Typeface font(String path) {
        try { return Typeface.createFromAsset(getAssets(), path); } catch (Exception e) { return Typeface.DEFAULT; }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
