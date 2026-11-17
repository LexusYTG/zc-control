package com.lexus2026.cdc;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * El panel de accesos directos. Es el mismo para la burbuja y para el gesto del borde:
 * solo cambia cómo se coloca ({@code edgeMode}) y cómo se anima su apertura.
 * Ocupa toda la ventana: un velo que atrapa el toque de afuera y, encima, la tarjeta del panel.
 */
public class SidePanel extends FrameLayout {
    public interface Host {
        void launch(String pkg);
        void closePanel();
        void recents();
        void openSettings();
    }

    private static final int M_LIST = 0;
    private static final int M_PICK = 2;

    private static final int TXT = 0xFFEDE7F6;
    private static final int SUB = 0xFFB0A9B5;
    private static final int ACC = 0xFFB69DF8;

    private final Host host;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final View scrim;
    private final LinearLayout card;
    private final float d;

    private boolean edgeMode = false;
    private int anchorCx = 0;
    private int anchorCy = 0;
    private int bubbleSize = 0;
    private int sw = 0;
    private int sh = 0;
    private int pw = 0;
    private int mode = M_LIST;
    private boolean editing = false;
    private float progress = 0f;
    private ValueAnimator anim = null;
    private boolean pickLoading = false;

    /** ScrollView con alto máximo, para que el panel se ajuste al contenido pero nunca pase de la pantalla. */
    private static class LimitedScroll extends ScrollView {
        int maxH = Integer.MAX_VALUE;

        LimitedScroll(Context c) { super(c); }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
        }
    }

    public SidePanel(Context c, Host host) {
        super(c);
        this.host = host;
        this.d = c.getResources().getDisplayMetrics().density;

        scrim = new View(c);
        scrim.setBackgroundColor(0x44000000);
        scrim.setOnClickListener(v -> SidePanel.this.host.closePanel());
        addView(scrim, new LayoutParams(-1, -1));

        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setPadding(dp(14), dp(14), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEC1B1B22);
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), 0x33FFFFFF);
        card.setBackground(bg);
        addView(card, new LayoutParams(dp(236), -2));
        applyProgress(0f);
    }

    private int dp(int v) { return (int) (v * d); }

    // ---------------- colocación ----------------

    /** Prepara el contenido. En modo borde se ancla al lateral derecho a la altura [cy]; si no, junto a la burbuja. */
    public void prepare(boolean edge, int cx, int cy, int bSize) {
        edgeMode = edge;
        anchorCx = cx;
        anchorCy = cy;
        bubbleSize = bSize;
        mode = M_LIST;
        editing = false;
        rebuild();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sw = w;
        sh = h;
        rebuild();   // recalcula también los altos máximos de las listas
    }

    private void place() {
        if (sw <= 0 || sh <= 0) return;
        int margin = dp(10);
        pw = Math.min(dp(236), sw - 2 * margin);
        int maxH = sh * 78 / 100;
        card.measure(MeasureSpec.makeMeasureSpec(pw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
        int ph = Math.min(card.getMeasuredHeight(), maxH);
        int left;
        int top;
        if (edgeMode) {
            left = sw - pw - margin;
            top = anchorCy - ph / 2;
        } else {
            boolean rightSide = anchorCx >= sw / 2;
            left = rightSide ? anchorCx + bubbleSize / 2 - pw : anchorCx - bubbleSize / 2;
            top = anchorCy + bubbleSize / 2 + dp(8);
            if (top + ph > sh - margin) top = anchorCy - bubbleSize / 2 - dp(8) - ph;
        }
        left = Math.max(margin, Math.min(sw - pw - margin, left));
        top = Math.max(dp(36), Math.min(sh - ph - margin, top));
        LayoutParams lp = new LayoutParams(pw, ph);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = left;
        lp.topMargin = top;
        card.setLayoutParams(lp);
        card.setPivotX(Math.max(0, Math.min(pw, anchorCx - left)));
        card.setPivotY(Math.max(0, Math.min(ph, anchorCy - top)));
        applyProgress(progress);
    }

    // ---------------- progreso / animación ----------------

    /** 0 = escondido, 1 = abierto. En modo borde se desliza desde el lateral; en modo burbuja crece desde ella. */
    public void applyProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        scrim.setAlpha(progress);
        if (edgeMode) {
            card.setTranslationX((1f - progress) * (pw + dp(10)));
            card.setScaleX(1f);
            card.setScaleY(1f);
            card.setAlpha(Math.min(1f, progress * 1.6f));
        } else {
            float s = 0.55f + 0.45f * progress;
            card.setTranslationX(0f);
            card.setScaleX(s);
            card.setScaleY(s);
            card.setAlpha(Math.min(1f, progress * 1.5f));
        }
    }

    public float progress() { return progress; }

    /** El panel sigue al dedo mientras se arrastra desde el borde. */
    public void dragTo(float p) {
        stopAnim();
        applyProgress(p);
    }

    public void stopAnim() {
        if (anim != null) { anim.cancel(); anim = null; }
    }

    public void animateTo(final float target, final Runnable end) {
        stopAnim();
        final float from = progress;
        long dur = 120L + (long) (240f * Math.abs(target - from));
        ValueAnimator a = ValueAnimator.ofFloat(from, target);
        a.setDuration(dur);
        a.setInterpolator(target > from ? new DecelerateInterpolator(1.6f) : new AccelerateInterpolator(1.3f));
        a.addUpdateListener(v -> applyProgress((Float) v.getAnimatedValue()));
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override public void onAnimationCancel(Animator an) { cancelled = true; }

            @Override
            public void onAnimationEnd(Animator an) {
                if (anim == a) anim = null;
                if (!cancelled && end != null) end.run();
            }
        });
        anim = a;
        a.start();
    }

    // ---------------- contenido ----------------

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private View divider() {
        View v = new View(getContext());
        v.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        v.setLayoutParams(lp);
        return v;
    }

    private void header(String title, String action, final Runnable onAction) {
        LinearLayout h = new LinearLayout(getContext());
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(title, 15f, TXT, true);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView a = text(action, 15f, ACC, true);
        a.setPadding(dp(10), dp(6), dp(4), dp(6));
        a.setOnClickListener(v -> onAction.run());
        h.addView(a, new LinearLayout.LayoutParams(-2, -2));
        card.addView(h, new LinearLayout.LayoutParams(-1, -2));
        card.addView(divider());
    }

    private LimitedScroll scroller() {
        LimitedScroll s = new LimitedScroll(getContext());
        s.maxH = Math.max(dp(120), (sh > 0 ? sh * 78 / 100 : dp(500)) - dp(170));
        s.setVerticalScrollBarEnabled(false);
        return s;
    }

    private ImageView iconView(Drawable ic) {
        ImageView iv = new ImageView(getContext());
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (ic != null) iv.setImageDrawable(ic);
        else {
            GradientDrawable g = new GradientDrawable();
            g.setColor(0x33FFFFFF);
            g.setCornerRadius(dp(10));
            iv.setImageDrawable(g);
        }
        return iv;
    }

    private LinearLayout row(Drawable ic, String label, int labelColor) {
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(2), dp(7), dp(2), dp(7));
        r.addView(iconView(ic), new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView t = text(label, 15f, labelColor, false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = dp(12);
        r.addView(t, lp);
        return r;
    }

    private TextView miniBtn(String s, boolean enabled, final Runnable action) {
        TextView t = text(s, 16f, TXT, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(9), dp(6), dp(9), dp(6));
        t.setAlpha(enabled ? 1f : 0.25f);
        if (enabled) t.setOnClickListener(v -> action.run());
        return t;
    }

    private void rebuild() {
        card.removeAllViews();
        if (mode == M_PICK) buildPicker(); else buildList();
        place();
    }

    private void buildList() {
        final Context c = getContext();
        header(editing ? "Editar accesos" : "Accesos rápidos", editing ? "Listo" : "✎", () -> { editing = !editing; rebuild(); });
        final List<String> apps = SideConfig.shortcuts(c);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        int shown = 0;
        for (int i = 0; i < apps.size(); i++) {
            final String pkg = apps.get(i);
            final int idx = i;
            boolean ok = SideLauncher.installedPkg(c, pkg);
            if (!ok && !editing) continue;
            LinearLayout r = row(ok ? SideLauncher.icon(c, pkg) : null,
                ok ? SideLauncher.label(c, pkg) : pkg + " (no instalada)", ok ? TXT : SUB);
            if (editing) {
                final int last = apps.size() - 1;
                r.addView(miniBtn("▲", idx > 0, () -> { SideConfig.moveShortcut(c, idx, idx - 1); rebuild(); }));
                r.addView(miniBtn("▼", idx < last, () -> { SideConfig.moveShortcut(c, idx, idx + 1); rebuild(); }));
                r.addView(miniBtn("✕", true, () -> { SideConfig.removeShortcut(c, pkg); rebuild(); }));
            } else {
                r.setOnClickListener(v -> host.launch(pkg));
                r.setOnLongClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    editing = true;
                    rebuild();
                    return true;
                });
            }
            list.addView(r, new LinearLayout.LayoutParams(-1, -2));
            shown++;
        }
        if (shown == 0) {
            TextView e = text("Todavía no agregaste apps.", 13f, SUB, false);
            e.setPadding(dp(2), dp(10), dp(2), dp(10));
            list.addView(e);
        }
        LinearLayout add = row(null, "Agregar acceso…", TXT);
        ((ImageView) add.getChildAt(0)).setImageDrawable(plusDrawable());
        add.setOnClickListener(v -> { mode = M_PICK; rebuild(); });
        list.addView(divider());
        list.addView(add, new LinearLayout.LayoutParams(-1, -2));
        LimitedScroll sc = scroller();
        sc.addView(list);
        card.addView(sc, new LinearLayout.LayoutParams(-1, -2));
        if (!editing) {
            card.addView(divider());
            TextView rec = text("Ventanas recientes", 13f, TXT, false);
            rec.setPadding(dp(4), dp(9), dp(4), dp(9));
            rec.setOnClickListener(v -> host.recents());
            card.addView(rec);
            TextView cfg = text("Configuración", 13f, TXT, false);
            cfg.setPadding(dp(4), dp(9), dp(4), dp(9));
            cfg.setOnClickListener(v -> host.openSettings());
            card.addView(cfg);
        }
    }

    // (el "+" del renglón de agregar)
    private Drawable plusDrawable() {
        return new Drawable() {
            private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(android.graphics.Canvas cv) {
                android.graphics.Rect b = getBounds();
                p.setColor(0x33FFFFFF);
                cv.drawRoundRect(new android.graphics.RectF(b), dp(10), dp(10), p);
                p.setColor(TXT);
                p.setStrokeWidth(dp(2));
                float cx = b.exactCenterX();
                float cy = b.exactCenterY();
                float r = b.width() * 0.22f;
                cv.drawLine(cx - r, cy, cx + r, cy, p);
                cv.drawLine(cx, cy - r, cx, cy + r, p);
            }

            @Override public void setAlpha(int a) {}
            @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    private void buildPicker() {
        final Context c = getContext();
        header("Elegí las apps", "Listo", () -> { mode = M_LIST; rebuild(); });
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView loading = text("Cargando…", 13f, SUB, false);
        loading.setPadding(dp(2), dp(10), dp(2), dp(10));
        list.addView(loading);
        LimitedScroll sc = scroller();
        sc.addView(list);
        card.addView(sc, new LinearLayout.LayoutParams(-1, -2));
        pickLoading = true;
        new Thread(() -> {
            final List<SideLauncher.App> all = SideLauncher.installed(c);
            ui.post(() -> {
                if (!pickLoading || mode != M_PICK || !isAttachedToWindow()) return;
                pickLoading = false;
                list.removeAllViews();
                final List<String> cur = SideConfig.shortcuts(c);
                for (final SideLauncher.App a : all) {
                    final LinearLayout r = row(a.icon, a.label, TXT);
                    final TextView mark = text(cur.contains(a.pkg) ? "✓" : "", 16f, ACC, true);
                    mark.setGravity(Gravity.CENTER);
                    r.addView(mark, new LinearLayout.LayoutParams(dp(28), -2));
                    r.setOnClickListener(v -> {
                        if (cur.contains(a.pkg)) { SideConfig.removeShortcut(c, a.pkg); cur.remove(a.pkg); mark.setText(""); }
                        else { SideConfig.addShortcut(c, a.pkg); cur.add(a.pkg); mark.setText("✓"); }
                    });
                    list.addView(r, new LinearLayout.LayoutParams(-1, -2));
                }
                place();
            });
        }).start();
    }
}
