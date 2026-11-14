package com.lexus2026.cdc

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Fondo con la forma elegida (círculo, redondeado, cuadrado, triángulo, estrella, hexágono, rombo). */
class Shaped(private val shape: TileShape, private var color: Int, private val roundPct: Int = 34) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    fun setFill(c: Int) { color = c; invalidateSelf() }

    override fun onBoundsChange(b: Rect) { build(b) }

    private fun build(b: Rect) {
        path.reset()
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = min(w, h) / 2f
        when (shape) {
            TileShape.CIRCLE -> path.addCircle(cx, cy, r, Path.Direction.CW)
            TileShape.ROUNDED -> {
                val rr = min(w, h) * roundPct.coerceIn(0, 50) / 100f
                path.addRoundRect(RectF(b), rr, rr, Path.Direction.CW)
            }
            TileShape.SQUARE -> {
                val rr = min(w, h) * 0.06f
                path.addRoundRect(RectF(b), rr, rr, Path.Direction.CW)
            }
            TileShape.TRIANGLE -> poly(
                listOf(
                    PointF(cx, b.top + h * 0.06f),
                    PointF(b.right - w * 0.03f, b.bottom - h * 0.12f),
                    PointF(b.left + w * 0.03f, b.bottom - h * 0.12f)
                ), r * 0.2f
            )
            TileShape.DIAMOND -> poly(
                listOf(PointF(cx, b.top.toFloat()), PointF(b.right.toFloat(), cy), PointF(cx, b.bottom.toFloat()), PointF(b.left.toFloat(), cy)),
                r * 0.14f
            )
            TileShape.HEXAGON -> poly(
                (0 until 6).map { k ->
                    val a = PI / 3.0 * k
                    PointF(cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat())
                }, r * 0.12f
            )
            TileShape.STAR -> poly(
                (0 until 10).map { k ->
                    val a = -PI / 2.0 + PI / 5.0 * k
                    val rad = if (k % 2 == 0) r else r * 0.5f
                    PointF(cx + (rad * cos(a)).toFloat(), cy + (rad * sin(a)).toFloat())
                }, r * 0.08f
            )
        }
    }

    /** Polígono con las esquinas suavizadas (curva cuadrática), sin depender de PathEffect. */
    private fun poly(pts: List<PointF>, rad: Float) {
        val n = pts.size
        for (i in 0 until n) {
            val p0 = pts[(i + n - 1) % n]
            val p1 = pts[i]
            val p2 = pts[(i + 1) % n]
            val d1 = hypot(p0.x - p1.x, p0.y - p1.y).coerceAtLeast(0.001f)
            val d2 = hypot(p2.x - p1.x, p2.y - p1.y).coerceAtLeast(0.001f)
            val r1 = min(rad, d1 / 2f)
            val r2 = min(rad, d2 / 2f)
            val ax = p1.x + (p0.x - p1.x) * r1 / d1
            val ay = p1.y + (p0.y - p1.y) * r1 / d1
            val bx = p1.x + (p2.x - p1.x) * r2 / d2
            val by = p1.y + (p2.y - p1.y) * r2 / d2
            if (i == 0) path.moveTo(ax, ay) else path.lineTo(ax, ay)
            path.quadTo(p1.x, p1.y, bx, by)
        }
        path.close()
    }

    override fun draw(c: Canvas) {
        paint.color = color
        c.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
