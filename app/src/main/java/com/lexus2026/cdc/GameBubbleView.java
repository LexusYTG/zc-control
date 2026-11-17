package com.lexus2026.cdc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class GameBubbleView extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ic = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public GameBubbleView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bg.setColor(0xFF111827);
        ring.setColor(0xFF34D399);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2f * d);
        ic.setColor(0xFFFFFFFF);
        hole.setColor(0xFF111827);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float cx = w / 2f;
        float cy = getHeight() / 2f;
        c.drawCircle(cx, cy, w / 2f - d, bg);
        c.drawCircle(cx, cy, w / 2f - d, ring);
        c.drawCircle(cx - 0.17f * w, cy + 0.09f * w, 0.1f * w, ic);
        c.drawCircle(cx + 0.17f * w, cy + 0.09f * w, 0.1f * w, ic);
        c.drawRoundRect(cx - 0.27f * w, cy - 0.15f * w, cx + 0.27f * w, cy + 0.1f * w, 0.12f * w, 0.12f * w, ic);
        float lx = cx - 0.14f * w;
        c.drawRect(lx - 0.055f * w, cy - 0.095f * w - 0.015f * w + 0.04f * w, lx + 0.055f * w, cy - 0.095f * w + 0.015f * w + 0.04f * w, hole);
        c.drawRect(lx - 0.015f * w, cy - 0.095f * w - 0.015f * w, lx + 0.015f * w, cy - 0.095f * w + 0.095f * w, hole);
        c.drawCircle(cx + 0.12f * w, cy - 0.07f * w, 0.026f * w, hole);
        c.drawCircle(cx + 0.18f * w, cy - 0.035f * w, 0.026f * w, hole);
    }
}
