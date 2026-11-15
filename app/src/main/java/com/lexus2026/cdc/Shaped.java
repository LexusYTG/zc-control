package com.lexus2026.cdc;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

/** Fondo con la forma elegida (círculo, redondeado, cuadrado, triángulo, estrella, hexágono, rombo). */
public class Shaped extends Drawable {
    private final TileShape shape;
    private int color;
    private final int roundPct;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int strokeW = 0;
    private final Path path = new Path();

    public Shaped(TileShape shape, int color) {
        this(shape, color, 34);
    }

    public Shaped(TileShape shape, int color, int roundPct) {
        this.shape = shape;
        this.color = color;
        this.roundPct = roundPct;
        paint.setStyle(Paint.Style.FILL);
    }

    public void setFill(int c) { color = c; invalidateSelf(); }

    public void setStroke(int c, int w) {
        strokeW = w;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(c);
        strokePaint.setStrokeWidth((float) w);
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect b) { build(b); }

    private void build(Rect b) {
        path.reset();
        float w = b.width();
        float h = b.height();
        if (w <= 0f || h <= 0f) return;
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        float r = Math.min(w, h) / 2f;
        switch (shape) {
            case CIRCLE:
                path.addCircle(cx, cy, r, Path.Direction.CW);
                break;
            case ROUNDED: {
                float rr = Math.min(w, h) * Math.max(0, Math.min(50, roundPct)) / 100f;
                path.addRoundRect(new RectF(b), rr, rr, Path.Direction.CW);
                break;
            }
            case SQUARE: {
                float rr = Math.min(w, h) * 0.06f;
                path.addRoundRect(new RectF(b), rr, rr, Path.Direction.CW);
                break;
            }
            case TRIANGLE: {
                List<PointF> pts = new ArrayList<>();
                pts.add(new PointF(cx, b.top + h * 0.06f));
                pts.add(new PointF(b.right - w * 0.03f, b.bottom - h * 0.12f));
                pts.add(new PointF(b.left + w * 0.03f, b.bottom - h * 0.12f));
                poly(pts, r * 0.2f);
                break;
            }
            case DIAMOND: {
                List<PointF> pts = new ArrayList<>();
                pts.add(new PointF(cx, (float) b.top));
                pts.add(new PointF((float) b.right, cy));
                pts.add(new PointF(cx, (float) b.bottom));
                pts.add(new PointF((float) b.left, cy));
                poly(pts, r * 0.14f);
                break;
            }
            case HEXAGON: {
                List<PointF> pts = new ArrayList<>();
                for (int k = 0; k < 6; k++) {
                    double a = Math.PI / 3.0 * k;
                    pts.add(new PointF(cx + (float) (r * Math.cos(a)), cy + (float) (r * Math.sin(a))));
                }
                poly(pts, r * 0.12f);
                break;
            }
            case STAR: {
                List<PointF> pts = new ArrayList<>();
                for (int k = 0; k < 10; k++) {
                    double a = -Math.PI / 2.0 + Math.PI / 5.0 * k;
                    float rad = k % 2 == 0 ? r : r * 0.5f;
                    pts.add(new PointF(cx + (float) (rad * Math.cos(a)), cy + (float) (rad * Math.sin(a))));
                }
                poly(pts, r * 0.08f);
                break;
            }
        }
    }

    /** Polígono con las esquinas suavizadas (curva cuadrática), sin depender de PathEffect. */
    private void poly(List<PointF> pts, float rad) {
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            PointF p0 = pts.get((i + n - 1) % n);
            PointF p1 = pts.get(i);
            PointF p2 = pts.get((i + 1) % n);
            float d1 = Math.max((float) Math.hypot(p0.x - p1.x, p0.y - p1.y), 0.001f);
            float d2 = Math.max((float) Math.hypot(p2.x - p1.x, p2.y - p1.y), 0.001f);
            float r1 = Math.min(rad, d1 / 2f);
            float r2 = Math.min(rad, d2 / 2f);
            float ax = p1.x + (p0.x - p1.x) * r1 / d1;
            float ay = p1.y + (p0.y - p1.y) * r1 / d1;
            float bx = p1.x + (p2.x - p1.x) * r2 / d2;
            float by = p1.y + (p2.y - p1.y) * r2 / d2;
            if (i == 0) path.moveTo(ax, ay); else path.lineTo(ax, ay);
            path.quadTo(p1.x, p1.y, bx, by);
        }
        path.close();
    }

    @Override
    public void draw(Canvas c) {
        paint.setColor(color);
        c.drawPath(path, paint);
        if (strokeW > 0) c.drawPath(path, strokePaint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @SuppressWarnings("deprecation")
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
