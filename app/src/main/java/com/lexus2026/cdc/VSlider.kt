package com.lexus2026.cdc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Slider vertical en forma de pastilla, con relleno desde abajo (brillo / volumen). */
class VSlider(
    ctx: Context, icon: Int, private val max: Int, private val track: Int, private val fill: Int,
    private val get: () -> Int, private val set: (Int) -> Unit,
    private val iconOnFill: Int = 0xFF444444.toInt(), private val iconOffFill: Int = 0xFFFFFFFF.toInt()
) : View(ctx) {
    private var value = get()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val rect = RectF()
    private val ico = ctx.getDrawable(icon)?.mutate()

    fun sync() { value = get(); invalidate() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = w / 2.1f
        rect.set(0f, 0f, w, h)
        paint.color = track
        c.drawRoundRect(rect, r, r, paint)
        c.save()
        clip.reset(); clip.addRoundRect(rect, r, r, Path.Direction.CW); c.clipPath(clip)
        val top = h - h * value.coerceIn(0, max) / max.coerceAtLeast(1)
        paint.color = fill
        c.drawRect(0f, top, w, h, paint)
        c.restore()
        ico?.let {
            val s = (w * 0.42f).toInt()
            val l = ((w - s) / 2f).toInt()
            val t = (h - s - w * 0.30f).toInt()
            it.setBounds(l, t, l + s, t + s)
            it.setTint(if (t + s / 2f > top) iconOnFill else iconOffFill)
            it.draw(c)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val f = 1f - (e.y / height).coerceIn(0f, 1f)
                value = (f * max).toInt()
                set(value); invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}
