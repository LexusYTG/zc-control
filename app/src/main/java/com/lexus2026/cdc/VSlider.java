package com.lexus2026.cdc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Slider vertical en forma de pastilla, con relleno desde abajo (brillo / volumen). */
public class VSlider extends View {
    private final int max;
    private final int track;
    private final int fill;
    private final IntSupplier get;
    private final IntConsumer set;
    private final int iconOnFill;
    private final int iconOffFill;
    private int value;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final Drawable ico;
    private float corner = 2.1f;

    public VSlider(Context ctx, int icon, int max, int track, int fill, IntSupplier get, IntConsumer set) {
        this(ctx, icon, max, track, fill, get, set, 0xFF444444, 0xFFFFFFFF);
    }

    public VSlider(Context ctx, int icon, int max, int track, int fill, IntSupplier get, IntConsumer set,
                   int iconOnFill, int iconOffFill) {
        super(ctx);
        this.max = max;
        this.track = track;
        this.fill = fill;
        this.get = get;
        this.set = set;
        this.iconOnFill = iconOnFill;
        this.iconOffFill = iconOffFill;
        this.value = get.getAsInt();
        Drawable d = Icons.get(icon);
        this.ico = d != null ? d.mutate() : null;
    }

    public void sync() { value = get.getAsInt(); invalidate(); }

    public void setCorner(float k) { corner = Math.max(1f, k); invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float r = w / corner;
        rect.set(0f, 0f, w, h);
        paint.setColor(track);
        c.drawRoundRect(rect, r, r, paint);
        c.save();
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        c.clipPath(clip);
        float top = h - h * Math.max(0, Math.min(max, value)) / Math.max(max, 1);
        paint.setColor(fill);
        c.drawRect(0f, top, w, h, paint);
        c.restore();
        if (ico != null) {
            int s = (int) (w * 0.42f);
            int l = (int) ((w - s) / 2f);
            int t = (int) (h - s - w * 0.30f);
            ico.setBounds(l, t, l + s, t + s);
            ico.setTint(t + s / 2f > top ? iconOnFill : iconOffFill);
            ico.draw(c);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                float f = 1f - Math.max(0f, Math.min(1f, e.getY() / getHeight()));
                value = (int) (f * max);
                set.accept(value);
                invalidate();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                break;
            default:
                break;
        }
        return true;
    }
}
