package com.lexus2026.cdc;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class GlyphDrawable extends Drawable {
    private final String text;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = 0xFFFFFFFF;

    public GlyphDrawable(String text) {
        this.text = text;
        paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        paint.setTextSize(b.height() * 0.9f);
        paint.setColor(color);
        float w = paint.measureText(text);
        if (w > b.width() && w > 0f) paint.setTextSize(paint.getTextSize() * b.width() / w);
        Paint.FontMetrics fm = paint.getFontMetrics();
        c.drawText(text, b.exactCenterX(), b.exactCenterY() - (fm.ascent + fm.descent) / 2f, paint);
    }

    @Override
    public void setTintList(ColorStateList t) {
        if (t != null) { color = t.getDefaultColor(); invalidateSelf(); }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @SuppressWarnings("deprecation")
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
