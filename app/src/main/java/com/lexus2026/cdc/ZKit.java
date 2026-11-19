package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Kit visual de las pantallas nuevas (Home, asistente inicial y los cinco módulos). Todas se arman igual:
 * encabezado con «volver», tarjetas agrupadas, filas con interruptor y una guía corta al final.
 * Cada pantalla vuelve a dibujarse entera desde el estado guardado ({@code render()}), así nunca queda desincronizada.
 */
final class ZKit {
    private ZKit() {}

    static final int BG = ZUi.BG, CARD = ZUi.CARD, CARD_HI = ZUi.CARD_HI, TXT = ZUi.TXT, SUB = ZUi.SUB,
        OK = ZUi.OK, WARN = ZUi.WARN, ACC = ZUi.ACC, BAD = 0xFFF87171, DARK = 0xFF0B0F1A;

    static int dp(Context c, float v) { return ZUi.dp(c, v); }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float weight, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static TextView pill(Context c, String s, int color) {
        TextView t = ZUi.text(c, s, 11f, color, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        GradientDrawable g = ZUi.shape(c, (color & 0x00FFFFFF) | 0x26000000, 20);
        t.setBackground(g);
        return t;
    }

    /** Pantalla con encabezado y contenido que se puede vaciar y volver a llenar. */
    static final class Page {
        final Activity a;
        final LinearLayout content;
        final ScrollView scroll;

        Page(Activity a, String title, String subtitle, boolean back) {
            this.a = a;
            LinearLayout root = ZUi.box(a, LinearLayout.VERTICAL);
            root.setPadding(dp(a, 20), dp(a, 20), dp(a, 20), dp(a, 36));
            if (back) {
                TextView b = ZUi.text(a, "‹  " + Lang.t(S.BACK), 14f, SUB, true);
                b.setPadding(0, dp(a, 6), dp(a, 20), dp(a, 14));
                b.setOnClickListener(v -> a.finish());
                root.addView(b);
            } else {
                root.setPadding(dp(a, 20), dp(a, 32), dp(a, 20), dp(a, 36));
            }
            if (title != null) root.addView(ZUi.text(a, title, 28f, TXT, true));
            if (subtitle != null) {
                TextView s = ZUi.text(a, subtitle, 14f, SUB, false);
                s.setLineSpacing(0f, 1.15f);
                s.setPadding(0, dp(a, 4), 0, dp(a, 8));
                root.addView(s);
            }
            content = ZUi.box(a, LinearLayout.VERTICAL);
            root.addView(content, new LinearLayout.LayoutParams(-1, -2));
            scroll = new ScrollView(a);
            scroll.setBackgroundColor(BG);
            scroll.setClipToPadding(false);
            scroll.setFillViewport(true);
            scroll.addView(root);
            Insets.apply(scroll);
        }

        void show() { a.setContentView(scroll); }

        void clear() { content.removeAllViews(); }

        void section(String title) { content.addView(ZUi.sectionLabel(a, title)); }

        LinearLayout card() {
            LinearLayout c = ZUi.box(a, LinearLayout.VERTICAL);
            c.setBackground(ZUi.shape(a, CARD, 20));
            c.setPadding(dp(a, 16), dp(a, 6), dp(a, 16), dp(a, 6));
            content.addView(c, lp(a, -1, -2, 0f, 0, 0, 0, 4));
            return c;
        }

        void divider(LinearLayout card) { card.addView(ZUi.divider(a), new LinearLayout.LayoutParams(-1, 1)); }

        void note(LinearLayout card, String s, int color) {
            TextView t = ZUi.text(a, s, 13f, color, false);
            t.setLineSpacing(0f, 1.15f);
            t.setPadding(0, dp(a, 8), 0, dp(a, 10));
            card.addView(t);
        }

        /** Cuadro de ayuda suelto (fuera de una tarjeta). */
        void guide(String title, String body) {
            section(title);
            LinearLayout c = card();
            c.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14));
            TextView t = ZUi.text(a, body, 13f, SUB, false);
            t.setLineSpacing(0f, 1.2f);
            c.addView(t);
        }

        /** Fila con título, descripción y interruptor. Devuelve el interruptor. */
        Switch toggle(LinearLayout card, String title, String sub, boolean on, Consumer<Boolean> change) {
            LinearLayout r = ZUi.box(a, LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(a, 12), 0, dp(a, 12));
            LinearLayout col = ZUi.box(a, LinearLayout.VERTICAL);
            col.addView(ZUi.text(a, title, 15f, TXT, true));
            if (sub != null) {
                TextView s = ZUi.text(a, sub, 12f, SUB, false);
                s.setPadding(0, dp(a, 2), 0, 0);
                col.addView(s);
            }
            r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            Switch sw = new Switch(a);
            sw.setChecked(on);
            sw.setOnCheckedChangeListener((v, b) -> change.accept(b));
            r.addView(sw, lp(a, -2, -2, 0f, 12, 0, 0, 0));
            r.setOnClickListener(v -> sw.toggle());
            card.addView(r);
            return sw;
        }

        /** Fila que abre otra cosa. */
        void nav(LinearLayout card, String title, String sub, Runnable go) {
            card.addView(ZUi.row(a, null, title, sub, false, v -> go.run()));
        }

        TextView button(String label, boolean primary, Runnable go) {
            TextView t = ZUi.text(a, label, 15f, primary ? DARK : TXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(a, 12), dp(a, 15), dp(a, 12), dp(a, 15));
            t.setBackground(ZUi.shape(a, primary ? ACC : CARD_HI, 16));
            t.setClickable(true);
            t.setOnClickListener(v -> go.run());
            return t;
        }

        void addButton(String label, boolean primary, Runnable go) {
            content.addView(button(label, primary, go), lp(a, -1, -2, 0f, 0, 10, 0, 0));
        }

        /** Fila de opciones excluyentes. {@code pick} recibe el índice elegido. */
        void chips(LinearLayout card, String title, String[] opts, int sel, IntConsumer pick) {
            if (title != null) {
                TextView t = ZUi.text(a, title, 14f, TXT, true);
                t.setPadding(0, dp(a, 12), 0, dp(a, 8));
                card.addView(t);
            }
            LinearLayout row = ZUi.box(a, LinearLayout.HORIZONTAL);
            for (int i = 0; i < opts.length; i++) {
                final int idx = i;
                boolean on = i == sel;
                TextView c = ZUi.text(a, opts[i], 12f, on ? DARK : TXT, true);
                c.setGravity(Gravity.CENTER);
                c.setPadding(dp(a, 6), dp(a, 11), dp(a, 6), dp(a, 11));
                c.setBackground(ZUi.shape(a, on ? ACC : CARD_HI, 12));
                c.setClickable(true);
                c.setOnClickListener(v -> pick.accept(idx));
                row.addView(c, lp(a, 0, -2, 1f, i == 0 ? 0 : 3, 0, i == opts.length - 1 ? 0 : 3, 0));
            }
            card.addView(row, lp(a, -1, -2, 0f, 0, 0, 0, 10));
        }

        /** Deslizador con valor visible. Guarda al soltar y mientras se mueve; no redibuja la pantalla. */
        void slider(LinearLayout card, String label, int min, int max, String unit, IntSupplier get, IntConsumer set) {
            final TextView t = ZUi.text(a, "", 14f, TXT, false);
            t.setPadding(0, dp(a, 12), 0, 0);
            card.addView(t);
            final int cur = Math.max(min, Math.min(max, get.getAsInt()));
            t.setText(label + ": " + cur + unit);
            SeekBar sb = new SeekBar(a);
            sb.setMax(max - min);
            sb.setProgress(cur - min);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                    t.setText(label + ": " + (p + min) + unit);
                    if (user) set.accept(p + min);
                }
                @Override public void onStartTrackingTouch(SeekBar s) { }
                @Override public void onStopTrackingTouch(SeekBar s) { set.accept(s.getProgress() + min); }
            });
            card.addView(sb, lp(a, -1, -2, 0f, 0, 2, 0, 10));
        }

        /** Fila de estado: punto de color + texto. */
        void status(LinearLayout card, String text, boolean ok) {
            LinearLayout r = ZUi.box(a, LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(a, 8), 0, dp(a, 8));
            TextView dot = ZUi.text(a, "●", 12f, ok ? OK : WARN, false);
            dot.setPadding(0, 0, dp(a, 10), 0);
            r.addView(dot);
            r.addView(ZUi.text(a, text, 14f, TXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(r);
        }
    }
}
