package com.lexus2026.cdc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** La burbuja: círculo translúcido con cuatro cuadritos, un anillo de progreso (al mantener apretado) y un ícono opcional. */
public class SideBubbleView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sq = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float hold = 0f;
    private float glyphAlpha = 1f;
    private Drawable glyph = null;
    private boolean editing = false;
    private boolean bare = false;

    public SideBubbleView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        fill.setColor(0xF2202030);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.2f * d);
        stroke.setColor(0x44FFFFFF);
        sq.setColor(0xFFE9E3FF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3f * d);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setColor(0xFFB69DF8);
    }

    /** 0..1: avance de "mantener apretado" para entrar al modo lienzo. */
    public void setHold(float v) { hold = Math.max(0f, Math.min(1f, v)); invalidate(); }

    /** Ícono de la app que notifica (null = los cuatro cuadritos). */
    public void setGlyph(Drawable g) { glyph = g; invalidate(); }

    /** Cuánto se ve el símbolo/ícono (se apaga mientras la burbuja se convierte en tarjeta). */
    public void setGlyphAlpha(float a) { glyphAlpha = Math.max(0f, Math.min(1f, a)); invalidate(); }

    /** En el lienzo la burbuja se resalta con un borde de acento. */
    public void setEditing(boolean e) { editing = e; invalidate(); }

    /** Sin círculo de fondo: solo el símbolo (lo usa la tarjeta animada, que pone su propio fondo). */
    public void setBare(boolean b) { bare = b; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float rad = Math.min(w, h) / 2f - stroke.getStrokeWidth();
        if (!bare) {
            c.drawCircle(cx, cy, rad, fill);
            stroke.setColor(editing ? 0xFFB69DF8 : 0x44FFFFFF);
            c.drawCircle(cx, cy, rad, stroke);
        }
        if (glyphAlpha > 0f) {
            if (glyph != null) {
                int s = (int) (rad * 1.15f);
                glyph.setBounds((int) (cx - s / 2f), (int) (cy - s / 2f), (int) (cx + s / 2f), (int) (cy + s / 2f));
                glyph.setAlpha((int) (255 * glyphAlpha));
                glyph.draw(c);
            } else {
                sq.setAlpha((int) (255 * glyphAlpha));
                float cell = rad * 0.42f;
                float gap = rad * 0.12f;
                float corner = cell * 0.28f;
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        float l = cx - cell - gap / 2f + j * (cell + gap);
                        float t = cy - cell - gap / 2f + i * (cell + gap);
                        r.set(l, t, l + cell, t + cell);
                        c.drawRoundRect(r, corner, corner, sq);
                    }
                }
            }
        }
        if (hold > 0f) {
            float m = ring.getStrokeWidth() / 2f + stroke.getStrokeWidth();
            r.set(m, m, w - m, h - m);
            c.drawArc(r, -90f, 360f * hold, false, ring);
        }
    }
}
