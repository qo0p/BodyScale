package uz.bodyscale.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small view helpers so the screens share one visual language. */
public final class Ui {

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    public static TextView chip(Context c, String s, boolean accent) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(accent ? 0xFF2DD4BF : 0xFFB6C0CF);
        t.setBackgroundResource(accent ? uz.bodyscale.R.drawable.bg_pill : uz.bodyscale.R.drawable.bg_chip);
        int ph = dp(c, 10), pv = dp(c, 6);
        t.setPadding(ph, pv, ph, pv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(c, 6);
        t.setLayoutParams(lp);
        return t;
    }

    /**
     * One metric line: label on the left, value on the right and an optional
     * colored verdict under the value.
     */
    public static void row(LinearLayout parent, String label, String value, String badge, int badgeColor) {
        Context c = parent.getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));

        TextView l = text(c, label, 14, 0xFF8B95A5);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l.setLayoutParams(lp);

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);

        TextView v = text(c, value, 16, 0xFFE8EDF4);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.END);
        right.addView(v);

        if (badge != null && !badge.isEmpty()) {
            TextView b = text(c, badge, 11, badgeColor);
            b.setGravity(Gravity.END);
            right.addView(b);
        }

        row.addView(l);
        row.addView(right);
        parent.addView(row);

        View divider = new View(c);
        divider.setBackgroundColor(0x142A3240);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.5f)));
        dl.leftMargin = dp(c, 14);
        dl.rightMargin = dp(c, 14);
        divider.setLayoutParams(dl);
        parent.addView(divider);
    }

    public static String date(long millis) {
        return new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    public static String f1(float v) {
        return String.format(Locale.getDefault(), "%.1f", v);
    }

    public static void clear(LinearLayout parent) {
        parent.removeAllViews();
    }

    public static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Keeps content clear of the status/navigation bars (edge-to-edge on API 35+). */
    public static void applyInsets(android.app.Activity a) {
        View content = a.findViewById(android.R.id.content);
        if (content == null) return;
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.graphics.Insets i = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                top = i.top;
                bottom = i.bottom;
                left = i.left;
                right = i.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });
    }
}
