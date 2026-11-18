package com.lexus2026.cdc;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Piezas visuales compartidas por las pantallas de Ajustes (mismos colores que la pantalla de permisos). */
final class ZUi {
    private ZUi() {}

    static final int BG = 0xFF0B0F1A, CARD = 0xFF151B2C, CARD_HI = 0xFF1D2540, TXT = 0xFFF1F5F9,
        SUB = 0xFF94A3B8, OK = 0xFF34D399, WARN = 0xFFFBBF24, ACC = 0xFF6C8CFF;

    static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }

    static GradientDrawable shape(Context c, int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radius));
        return g;
    }

    static TextView text(Context c, String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout box(Context c, int orientation) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(orientation);
        return l;
    }

    static TextView sectionLabel(Context c, String title) {
        TextView t = text(c, title.toUpperCase(), 11f, SUB, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(c, 4), dp(c, 24), 0, dp(c, 10));
        return t;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x1AFFFFFF);
        return v;
    }

    /** Fila: ícono en cuadro, título, descripción y flecha. {@code dim} = ajuste todavía no disponible. */
    static View row(Context c, String icon, String title, String sub, boolean dim, View.OnClickListener click) {
        LinearLayout r = box(c, LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        r.setClickable(true);
        r.setOnClickListener(click);
        if (icon != null) {
            TextView ic = text(c, icon, 18f, dim ? SUB : TXT, false);
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(shape(c, CARD_HI, 12));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40));
            ip.rightMargin = dp(c, 14);
            r.addView(ic, ip);
        }
        LinearLayout col = box(c, LinearLayout.VERTICAL);
        col.addView(text(c, title, 15f, dim ? SUB : TXT, false));
        if (sub != null) col.addView(text(c, sub, 12f, SUB, false));
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        if (dim) {
            TextView tag = text(c, Lang.t(541), 11f, SUB, true);
            tag.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
            tag.setBackground(shape(c, CARD_HI, 10));
            r.addView(tag);
        } else {
            r.addView(text(c, "›", 22f, SUB, false));
        }
        return r;
    }
}
