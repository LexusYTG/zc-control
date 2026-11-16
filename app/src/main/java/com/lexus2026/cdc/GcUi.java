package com.lexus2026.cdc;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class GcUi {
    private GcUi() {}

    public static final int WRAP = -2;
    public static final int MATCH = -1;
    public static final int BG = 0xFF0B0F1A;
    public static final int CARD = 0xFF111827;
    public static final int BOX = 0xFF1F2937;
    public static final int TEXT = 0xFFF3F4F6;
    public static final int SUB = 0xFF9CA3AF;
    public static final int GREEN = 0xFF6EE7B7;
    public static final int ORANGE = 0xFFFB923C;
    public static final int RED = 0xFFF87171;
    public static final int GRAY = 0xFF6B7280;

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable round(Context c, int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, r));
        return g;
    }

    public static TextView tv(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static void paint(TextView t, String s, int color) {
        t.setText(s);
        t.setTextColor(color);
        t.setBackground(round(t.getContext(), (color & 0x00FFFFFF) | 0x33000000, 8));
    }

    public static TextView badge(Context c, String s, int color) {
        TextView t = tv(c, s, 11, color, true);
        t.setPadding(dp(c, 10), dp(c, 3), dp(c, 10), dp(c, 3));
        paint(t, s, color);
        return t;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View line(Context c) {
        View v = new View(c);
        v.setBackgroundColor(BOX);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(c, 1))));
        return v;
    }

    public static LinearLayout.LayoutParams lp(Context c, int w, int h, float weight, float topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.topMargin = dp(c, topDp);
        return p;
    }
}
