package com.lexus2026.cdc;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

/** Overlay que dibuja y anima los dos arcos. No recibe toques: el gesto lo sigue el botón Home. */
public class RadialView extends View {
    private final RadialNavigationEngine eng = new RadialNavigationEngine();
    private final LauncherSettings st;
    private final float d;
    private List<CategoryManager.Category> cats = new ArrayList<>();
    private float p1, p2, top;
    private ValueAnimator anim1, anim2;
    private float[] hl = new float[0];
    private boolean shown;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
        track = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG),
        txt = new Paint(Paint.ANTI_ALIAS_FLAG), pill = new Paint(Paint.ANTI_ALIAS_FLAG),
        pillTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    public RadialView(Context c, LauncherSettings st) {
        super(c);
        this.st = st;
        d = c.getResources().getDisplayMetrics().density;
        setClickable(false);
        setVisibility(GONE);
        line.setStyle(Paint.Style.STROKE);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        txt.setTextAlign(Paint.Align.CENTER);
        pillTxt.setTextAlign(Paint.Align.CENTER);
        pillTxt.setColor(Color.WHITE);
        pillTxt.setTextSize(14 * d);
        pill.setColor(Color.argb(220, 16, 16, 34));
    }

    public void show(float cx, float cy, List<CategoryManager.Category> list, float topInset) {
        cats = list;
        top = topInset;
        int size = list.size(), c1 = st.get(LauncherSettings.RING1), c2 = st.get(LauncherSettings.RING2);
        int n1 = Math.min(c1, size), n2 = Math.max(0, Math.min(c2, size - c1));
        eng.layout(cx, cy, getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels, topInset, d, st, n1, n2);
        hl = new float[n1 + n2];
        p1 = 0; p2 = 0; shown = true;
        setVisibility(VISIBLE);
        run(true, 1f);
    }

    public void move(float x, float y) {
        if (!shown) return;
        boolean was2 = eng.stage2;
        int wasSel = eng.sel;
        if (eng.update(x, y)) {
            if (was2 != eng.stage2) run(false, eng.stage2 ? 1f : 0f);
            if (wasSel != eng.sel && eng.sel != RadialNavigationEngine.NONE)
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            invalidate();
        }
    }

    /** Cierra con animación y devuelve la categoría elegida (índice en la lista) o -1. */
    public int hide() {
        int s = eng.sel;
        shown = false;
        run(true, 0f);
        run(false, 0f);
        return s;
    }

    private void run(final boolean first, float to) {
        ValueAnimator old = first ? anim1 : anim2;
        if (old != null) old.cancel();
        ValueAnimator va = ValueAnimator.ofFloat(first ? p1 : p2, to);
        va.setDuration(st.get(LauncherSettings.ANIM));
        va.setInterpolator(new LinearInterpolator());
        va.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            if (first) p1 = v; else p2 = v;
            invalidate();
        });
        if (first) va.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator an) { if (!shown && p1 <= 0.01f) setVisibility(GONE); }
        });
        if (first) anim1 = va; else anim2 = va;
        va.start();
    }

    private static float c01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private static float overshoot(float t) { t -= 1; return t * t * (2.4f * t + 1.4f) + 1; }

    @Override protected void onDraw(Canvas c) {
        if (p1 <= 0.001f) return;
        c.drawColor(Color.argb((int) (120 * c01(p1)), 0, 0, 10));
        busy = false;
        drawRing(c, 0, eng.n1, eng.r1, p1);
        if (eng.n2 > 0 && p2 > 0.001f) drawRing(c, eng.n1, eng.n2, eng.r2, p2 * c01(p1));
        if (eng.sel != RadialNavigationEngine.NONE && eng.sel < cats.size()) drawLabel(c, cats.get(eng.sel).name);
        if (busy) postInvalidateOnAnimation();
    }

    private boolean busy;

    private void drawRing(Canvas c, int off, int cnt, float r, float p) {
        if (cnt == 0) return;
        float R = eng.rad;
        float tw = st.get(LauncherSettings.TRACK) * d;
        if (tw > 0) {
            track.setStrokeWidth(tw);
            track.setColor(Color.argb((int) (70 * c01(p * 2)), 255, 255, 255));
            rect.set(eng.cx - r, eng.cy - r, eng.cx + r, eng.cy + r);
            c.drawArc(rect, -90 - eng.arcDeg / 2f + eng.rotDeg, eng.arcDeg * c01(p), false, track);
        }
        for (int k = 0; k < cnt; k++) {
            int g = off + k;
            float lt = c01(p * (1 + 0.07f * (cnt - 1)) - 0.07f * k);
            float e = overshoot(lt);
            float x = eng.cx + (eng.px[g] - eng.cx) * e, y = eng.cy + (eng.py[g] - eng.cy) * e;
            float target = g == eng.sel ? 1f : 0f;
            hl[g] += (target - hl[g]) * 0.3f;
            if (Math.abs(target - hl[g]) > 0.01f) busy = true; else hl[g] = target;
            float rr = R * (1 + 0.3f * hl[g]) * Math.max(0f, e);
            CategoryManager.Category cat = cats.get(g);
            if (hl[g] > 0.02f) {
                glow.setColor(cat.color);
                glow.setAlpha((int) (140 * hl[g]));
                c.drawCircle(x, y, rr * 1.4f, glow);
            }
            fill.setColor(cat.color);
            fill.setAlpha((int) (255 * c01(lt * 1.6f)));
            shape(c, x, y, rr, cat.shape >= 0 ? cat.shape : st.get(LauncherSettings.SHAPE), fill);
            line.setStrokeWidth((1.5f + 2f * hl[g]) * d);
            line.setColor(Color.argb((int) (190 * c01(lt)), 255, 255, 255));
            shape(c, x, y, rr, cat.shape >= 0 ? cat.shape : st.get(LauncherSettings.SHAPE), line);
            txt.setTextSize(rr * 0.95f);
            txt.setColor(Color.argb((int) (255 * c01(lt * 1.6f)), 255, 255, 255));
            c.drawText(cat.icon, x, y - (txt.ascent() + txt.descent()) / 2f, txt);
        }
    }

    private void shape(Canvas c, float x, float y, float r, int type, Paint p) {
        switch (type) {
            case 1: c.drawRoundRect(x - r, y - r, x + r, y + r, r * 0.62f, r * 0.62f, p); break;
            case 2: c.drawRoundRect(x - r, y - r, x + r, y + r, r * 0.28f, r * 0.28f, p); break;
            case 3:
                path.reset();
                path.moveTo(x, y - r * 1.2f); path.lineTo(x + r * 1.2f, y);
                path.lineTo(x, y + r * 1.2f); path.lineTo(x - r * 1.2f, y); path.close();
                c.drawPath(path, p);
                break;
            default: c.drawCircle(x, y, r, p);
        }
    }

    /** Nombre de la categoría seleccionada, fijo sobre el arco más externo para no tapar burbujas. */
    private void drawLabel(Canvas c, String name) {
        float outer = eng.n2 > 0 ? eng.r1 + (eng.r2 - eng.r1) * c01(p2) : eng.r1;
        float y = Math.max(top + 30 * d, eng.cy - outer - eng.rad * 1.4f - 24 * d);
        float tw = pillTxt.measureText(name), hw = tw / 2f + 16 * d;
        float x = Math.max(hw + 6 * d, Math.min(getWidth() - hw - 6 * d, eng.cx));
        pill.setAlpha((int) (220 * c01(p1)));
        c.drawRoundRect(x - hw, y - 18 * d, x + hw, y + 14 * d, 16 * d, 16 * d, pill);
        c.drawText(name, x, y + 5 * d, pillTxt);
    }
}
