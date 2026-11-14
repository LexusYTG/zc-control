package dev.cc.panel

import android.graphics.Bitmap

/** Desenfoque de caja (3 pasadas ≈ gaussiano) sobre un bitmap chico. Barato y sin depender del compositor. */
object Blur {
    fun apply(src: Bitmap, r: Int, passes: Int): Bitmap {
        val w = src.width
        val h = src.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        repeat(passes) {
            box(a, b, w, h, r, true)
            box(b, a, w, h, r, false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun box(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horiz: Boolean) {
        val len = if (horiz) w else h
        val lines = if (horiz) h else w
        val d = 2 * r + 1
        for (line in 0 until lines) {
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val c = src[ix(i.coerceIn(0, len - 1), line, w, horiz)]
                sr += (c shr 16) and 255
                sg += (c shr 8) and 255
                sb += c and 255
            }
            for (i in 0 until len) {
                dst[ix(i, line, w, horiz)] = (0xFF shl 24) or ((sr / d) shl 16) or ((sg / d) shl 8) or (sb / d)
                val add = src[ix((i + r + 1).coerceAtMost(len - 1), line, w, horiz)]
                val sub = src[ix((i - r).coerceAtLeast(0), line, w, horiz)]
                sr += ((add shr 16) and 255) - ((sub shr 16) and 255)
                sg += ((add shr 8) and 255) - ((sub shr 8) and 255)
                sb += (add and 255) - (sub and 255)
            }
        }
    }

    private fun ix(i: Int, line: Int, w: Int, horiz: Boolean) = if (horiz) line * w + i else i * w + line
}
