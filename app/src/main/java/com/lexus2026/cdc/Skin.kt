package com.lexus2026.cdc

import android.content.Context
import java.io.File

enum class BgMode(val label: String) { SOLID("Sólido"), TRANSPARENT("Transparente"), BLUR("Difuminado"), IMAGE("Imagen") }

enum class TileShape(val label: String, val sym: String) {
    CIRCLE("Círculo", "●"), ROUNDED("Redondeado", "▢"), SQUARE("Cuadrado", "■"),
    TRIANGLE("Triángulo", "▲"), STAR("Estrella", "★"), HEXAGON("Hexágono", "⬢"), DIAMOND("Rombo", "◆")
}

enum class TileLayout(val label: String) { ICON("Solo ícono"), LABEL("Ícono y texto"), PILL("Píldora") }

/**
 * Configuración de la barra, creada por el usuario en "Make your action bar".
 * Se guarda en SharedPreferences con prefijo "sk_". Los valores por defecto viven solo acá.
 */
class Skin(c: Context) {
    companion object {
        /** Valores por defecto. Los colores con 0 significan "automático". */
        val D: Map<String, Int> = mapOf(
            // colores y tema
            "theme" to 0, "accent" to 0, "tileOff" to 0, "offAlpha" to 100, "onAlpha" to 100, "textColor" to 0,
            // botones
            "shape" to 1, "round" to 34, "layout" to 2, "cols" to 2, "size" to 62, "gap" to 8,
            "iconScale" to 52, "labelSize" to 12, "showSub" to 1,
            // fondo detrás del panel
            "bgMode" to 2, "bgColor" to 0xFF000000.toInt(), "dim" to 35, "blurR" to 4, "imgBlur" to 0,
            // hoja
            "sheetColor" to 0, "sheetAlpha" to 84, "sheetRadius" to 28,
            // reloj
            "clockSize" to 36, "clockAlign" to 0, "clockWeight" to 1, "showDate" to 1, "showGear" to 0,
            // controles
            "sliderStyle" to 0, "sliderThick" to 60, "compact" to 4, "showMedia" to 1,
            // tarjetas y notificaciones
            "cardR" to 22, "cardAlpha" to 100, "notifApp" to 0,
            // barrita de acceso
            "pillW" to 72, "pillH" to 4
        )

        fun prefs(c: Context) = c.getSharedPreferences("cc", Context.MODE_PRIVATE)
        private fun k(key: String) = "sk_$key"
        fun get(c: Context, key: String): Int = prefs(c).getInt(k(key), D[key] ?: 0)
        fun set(c: Context, key: String, v: Int) { prefs(c).edit().putInt(k(key), v).apply() }
        fun reset(c: Context) {
            val e = prefs(c).edit()
            D.keys.forEach { e.remove(k(it)) }
            e.apply()
        }
        fun imageFile(c: Context) = File(c.filesDir, "bg_custom.jpg")
    }

    private val p = prefs(c)
    private fun i(key: String): Int = p.getInt(k(key), D[key] ?: 0)
    private fun b(key: String) = i(key) != 0

    val theme = i("theme")                     // 0 sistema, 1 claro, 2 oscuro
    val accent = i("accent")
    val tileOff = i("tileOff")
    val offAlpha = i("offAlpha")
    val onAlpha = i("onAlpha")
    val textColor = i("textColor")

    val shape: TileShape = TileShape.values().getOrElse(i("shape")) { TileShape.ROUNDED }
    val round = i("round")
    val layout: TileLayout = TileLayout.values().getOrElse(i("layout")) { TileLayout.PILL }
    val cols = i("cols").coerceIn(2, 6)
    val size = i("size").coerceIn(32, 120)
    val gap = i("gap")
    val iconScale = i("iconScale")
    val labelSize = i("labelSize")
    val showSub = b("showSub")

    val bgMode: BgMode = BgMode.values().getOrElse(i("bgMode")) { BgMode.BLUR }
    val bgColor = i("bgColor")
    val dim = i("dim")
    val blurR = i("blurR").coerceIn(1, 12)
    val imgBlur = i("imgBlur")

    val sheetColor = i("sheetColor")
    val sheetAlpha = i("sheetAlpha")
    val sheetRadius = i("sheetRadius")

    val clockSize = i("clockSize")
    val clockAlign = i("clockAlign")           // 0 izquierda, 1 centro, 2 derecha
    val clockWeight = i("clockWeight")         // 0 fino, 1 normal, 2 negrita
    val showDate = b("showDate")
    val showGear = b("showGear")

    val sliderStyle = i("sliderStyle")         // 0 horizontal, 1 vertical
    val sliderThick = i("sliderThick")
    val compact = i("compact")
    val showMedia = b("showMedia")

    val cardR = i("cardR")
    val cardAlpha = i("cardAlpha")
    val notifApp = b("notifApp")
}
