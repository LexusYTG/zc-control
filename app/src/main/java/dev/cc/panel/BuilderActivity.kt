package dev.cc.panel

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.FileOutputStream

private const val MPB = -1
private const val WCB = -2
private const val REQ_IMG = 42

/** "Make your action bar": arma tu propia barra con vista previa en vivo. Todo se guarda al instante. */
class BuilderActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var previewBox: FrameLayout
    private var preview: PanelView? = null
    private lateinit var controls: LinearLayout
    private lateinit var scroll: ScrollView
    private var stage = 2
    private var tab = 0
    private var pendingHandle = false

    private val txt = 0xFFEDE7F6.toInt()
    private val sub = 0xFFB0A9B5.toInt()
    private val acc = 0xFFB69DF8.toInt()
    private val chipOff = 0xFF2A2833.toInt()
    private val chipOn = 0xFF5B4B9A.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val rebuildRunnable = Runnable {
        doRebuildPreview()
        if (pendingHandle) { pendingHandle = false; ControlService.instance?.rebuildHandle() }
    }

    /** Se llama en cada cambio; junta varios cambios seguidos (por ejemplo, arrastrar un slider) en una sola reconstrucción. */
    private fun changed(handle: Boolean = false) {
        if (handle) pendingHandle = true
        ui.removeCallbacks(rebuildRunnable)
        ui.postDelayed(rebuildRunnable, 120)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF121216.toInt()) }

        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(8)) }
        head.addView(TextView(this).apply {
            text = "‹"; textSize = 28f; setTextColor(txt); setPadding(0, 0, dp(14), dp(2))
            setOnClickListener { finish() }
        })
        head.addView(TextView(this).apply {
            text = "Make your action bar"; textSize = 20f; setTextColor(txt); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, WCB, 1f))
        root.addView(head)

        // ---- vista previa ----
        previewBox = FrameLayout(this)
        previewBox.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(demoBitmap(360, 640))
        }, FrameLayout.LayoutParams(MPB, MPB))
        // Capa que se come los toques: la vista previa no se maneja, se mira.
        previewBox.addView(View(this).apply { isClickable = true }, FrameLayout.LayoutParams(MPB, MPB))
        root.addView(previewBox, LinearLayout.LayoutParams(MPB, (resources.displayMetrics.heightPixels * 0.40f).toInt()))

        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        fun stateBtn(label: String, s: Int, t: Int) = Button(this).apply {
            text = label; isAllCaps = false; textSize = 12f; setPadding(0, 0, 0, 0)
            setOnClickListener { stage = s; tab = t; preview?.previewState(stage, tab) }
        }.also { bar.addView(it, LinearLayout.LayoutParams(0, WCB, 1f)) }
        stateBtn("Acotada", 1, 0); stateBtn("Completa", 2, 0); stateBtn("Controles", 2, 1)
        Button(this).apply {
            text = "▶ Real"; isAllCaps = false; textSize = 12f; setPadding(0, 0, 0, 0)
            setOnClickListener {
                val svc = ControlService.instance
                if (svc != null) svc.open()
                else Toast.makeText(this@BuilderActivity, "El servicio todavía no está activo", Toast.LENGTH_LONG).show()
            }
        }.also { bar.addView(it, LinearLayout.LayoutParams(0, WCB, 1f)) }
        root.addView(bar)

        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), dp(40)) }
        scroll = ScrollView(this).apply { addView(controls) }
        root.addView(scroll, LinearLayout.LayoutParams(MPB, 0, 1f))
        setContentView(root)

        buildControls()
        doRebuildPreview()
    }

    override fun onDestroy() {
        ui.removeCallbacks(rebuildRunnable)
        super.onDestroy()
    }

    // ======================= vista previa =======================

    private fun doRebuildPreview() {
        preview?.let { previewBox.removeView(it) }
        val p = PanelView(this) { }
        preview = p
        previewBox.addView(p, 1, FrameLayout.LayoutParams(MPB, MPB))
        p.post {
            p.previewState(stage, tab)
            if (Skin(this).bgMode == BgMode.BLUR) {
                val small = Bitmap.createScaledBitmap(demoBitmap(360, 640), 96, 170, true)
                p.setBackdrop(Blur.apply(small, Skin.get(this, "blurR"), 3))
            }
        }
    }

    /** "Pantalla de inicio" de mentira para que se note la transparencia y el difuminado. */
    private fun demoBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF1E3C72.toInt(), 0xFFB24592.toInt(), 0xFFF15F79.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        val cols = intArrayOf(0xFFFFD54F.toInt(), 0xFF4DD0E1.toInt(), 0xFF81C784.toInt(), 0xFFFFFFFF.toInt())
        val cell = w / 4f
        for (r in 0 until 8) for (q in 0 until 4) {
            p.color = cols[(r + q) % 4]
            val l = q * cell + cell * 0.2f
            val t = r * cell + cell * 0.2f
            c.drawRoundRect(RectF(l, t, l + cell * 0.6f, t + cell * 0.6f), cell * 0.16f, cell * 0.16f, p)
        }
        return bmp
    }

    // ======================= controles =======================

    private fun label(text: String, size: Float = 14f, color: Int = txt, top: Int = 8) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); setPadding(0, dp(top), 0, 0)
    }

    private fun section(title: String) {
        controls.addView(TextView(this).apply {
            text = title; textSize = 12f; setTextColor(acc); setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f; setPadding(0, dp(26), 0, dp(4))
        })
    }

    private fun note(text: String) = controls.addView(label(text, 12f, sub, 4))

    private fun slider(
        name: String, lo: Int, hi: Int, unit: String, handle: Boolean,
        getV: () -> Int, setV: (Int) -> Unit, show: ((Int) -> String)? = null
    ) {
        val tv = label("", 14f)
        fun fmt(v: Int) = name + ": " + (show?.invoke(v) ?: "$v$unit")
        val cur = getV().coerceIn(lo, hi)
        tv.text = fmt(cur)
        controls.addView(tv)
        controls.addView(SeekBar(this).apply {
            max = hi - lo; progress = cur - lo
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    val v = p + lo
                    tv.text = fmt(v)
                    if (fromUser) { setV(v); changed(handle) }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })
    }

    private fun skSlider(name: String, key: String, lo: Int, hi: Int, unit: String = "", handle: Boolean = false) =
        slider(name, lo, hi, unit, handle, { Skin.get(this, key) }, { Skin.set(this, key, it) })

    private fun chips(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val row = LinearLayout(this).apply { setPadding(0, dp(6), 0, dp(2)) }
        val views = mutableListOf<TextView>()
        fun paint(sel: Int) = views.forEachIndexed { i, v ->
            v.background = GradientDrawable().apply { setColor(if (i == sel) chipOn else chipOff); cornerRadius = dp(18).toFloat() }
        }
        options.forEachIndexed { i, o ->
            val v = TextView(this).apply {
                text = o; textSize = 13f; setTextColor(txt); gravity = Gravity.CENTER
                setPadding(dp(14), dp(9), dp(14), dp(9))
                setOnClickListener { paint(i); onPick(i) }
            }
            views.add(v)
            row.addView(v, LinearLayout.LayoutParams(WCB, WCB).apply { rightMargin = dp(8) })
        }
        paint(selected)
        controls.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
    }

    /** Opciones de un valor entero del Skin. Si [rebuild], se rearma la lista (hay controles que dependen de esta opción). */
    private fun choice(name: String, key: String, options: List<String>, rebuild: Boolean = false, extra: ((Int) -> Unit)? = null) {
        controls.addView(label(name))
        chips(options, Skin.get(this, key)) { i ->
            Skin.set(this, key, i)
            extra?.invoke(i)
            if (rebuild) buildControls()
            changed()
        }
    }

    private fun toggle(name: String, key: String) {
        controls.addView(Switch(this).apply {
            text = name; textSize = 14f; setTextColor(txt); setPadding(0, dp(10), 0, dp(2))
            isChecked = Skin.get(this@BuilderActivity, key) != 0
            setOnCheckedChangeListener { _, c -> Skin.set(this@BuilderActivity, key, if (c) 1 else 0); changed() }
        })
    }

    private val palette = intArrayOf(
        0xFF6750A4.toInt(), 0xFF3482FF.toInt(), 0xFF2BC48A.toInt(), 0xFFFF9F0A.toInt(), 0xFFE53935.toInt(),
        0xFFFF4081.toInt(), 0xFFFFC107.toInt(), 0xFF00BCD4.toInt(), 0xFFFFFFFF.toInt(), 0xFF8E8E93.toInt(), 0xFF1C1C1E.toInt()
    )

    private fun colorRow(name: String, key: String, allowAuto: Boolean) {
        controls.addView(label(name))
        val sw = LinearLayout(this).apply { setPadding(0, dp(6), 0, dp(4)) }
        val views = mutableListOf<Pair<View, Int>>()
        fun cur() = Skin.get(this, key)
        fun paint() = views.forEach { (v, c) ->
            val sel = c == cur()
            (v.background as GradientDrawable).setStroke(if (sel) dp(3) else dp(1), if (sel) 0xFFFFFFFF.toInt() else 0x66FFFFFF)
        }
        fun add(c: Int, text: String) {
            val v = TextView(this).apply {
                gravity = Gravity.CENTER; this.text = text; textSize = 13f; setTextColor(0xFFFFFFFF.toInt())
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (c == 0) 0xFF444444.toInt() else c) }
                setOnClickListener { Skin.set(this@BuilderActivity, key, c); paint(); changed() }
            }
            sw.addView(v, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(8) })
            views.add(v to c)
        }
        if (allowAuto) add(0, "A")
        palette.forEach { add(it, "") }

        // Color personalizado con matiz / saturación / brillo
        val custom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val hsv = FloatArray(3)
        Color.colorToHSV(if (cur() == 0) 0xFF6750A4.toInt() else cur(), hsv)
        fun hsvBar(n: String, max: Int, init: Int, idx: Int, scale: Float) {
            custom.addView(label(n, 12f, sub, 2))
            custom.addView(SeekBar(this).apply {
                this.max = max; progress = init
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        hsv[idx] = p * scale
                        Skin.set(this@BuilderActivity, key, Color.HSVToColor(hsv))
                        paint(); changed()
                    }
                    override fun onStartTrackingTouch(s: SeekBar) {}
                    override fun onStopTrackingTouch(s: SeekBar) {}
                })
            })
        }
        hsvBar("Matiz", 360, hsv[0].toInt(), 0, 1f)
        hsvBar("Saturación", 100, (hsv[1] * 100).toInt(), 1, 0.01f)
        hsvBar("Brillo", 100, (hsv[2] * 100).toInt(), 2, 0.01f)
        sw.addView(TextView(this).apply {
            gravity = Gravity.CENTER; text = "+"; textSize = 20f; setTextColor(txt)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(chipOff); setStroke(dp(1), 0x66FFFFFF) }
            setOnClickListener { custom.visibility = if (custom.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        paint()
        controls.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(sw) })
        controls.addView(custom)
    }

    // ---- imagen de fondo ----
    private fun imageRow() {
        val has = Skin.imageFile(this).exists()
        controls.addView(label(if (has) "Imagen elegida ✓" else "Todavía no elegiste ninguna imagen", 13f, if (has) txt else sub))
        val row = LinearLayout(this).apply { setPadding(0, dp(6), 0, dp(2)) }
        row.addView(Button(this).apply {
            text = if (has) "Cambiar imagen" else "Elegir imagen"; isAllCaps = false
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
                }, REQ_IMG)
            }
        }, LinearLayout.LayoutParams(0, WCB, 1f))
        if (has) row.addView(Button(this).apply {
            text = "Quitar"; isAllCaps = false
            setOnClickListener { Skin.imageFile(this@BuilderActivity).delete(); buildControls(); changed() }
        }, LinearLayout.LayoutParams(0, WCB, 1f))
        controls.addView(row)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != REQ_IMG || res != RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            val ok = saveImage(uri)
            ui.post {
                if (ok) {
                    Skin.set(this, "bgMode", BgMode.IMAGE.ordinal)
                    buildControls(); changed()
                } else Toast.makeText(this, "No se pudo usar esa imagen", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    /** Copia la imagen elegida a la app (reducida y con la rotación corregida), así no depende de permisos del selector. */
    private fun saveImage(uri: Uri): Boolean {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return false
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / sample > 2400) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return false
            val orient = contentResolver.openInputStream(uri)?.use {
                try { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (e: Exception) { 1 }
            } ?: 1
            val deg = when (orient) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            if (deg != 0f) {
                val m = Matrix().apply { postRotate(deg) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val k = 1600f / maxOf(bmp.width, bmp.height)
            if (k < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
            FileOutputStream(Skin.imageFile(this)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            return true
        } catch (e: Throwable) { return false }
    }

    // ======================= la lista de opciones =======================

    private fun buildControls() {
        val keep = if (::scroll.isInitialized) scroll.scrollY else 0
        controls.removeAllViews()
        val sk = Skin(this)
        val zones = listOf("left" to "Izquierda", "center" to "Centro", "right" to "Derecha", "full" to "Todo arriba")

        controls.addView(label("Los cambios se ven al instante en la vista previa de arriba (es la barra de verdad). Probá todo.", 12f, sub, 6))

        section("FONDO DETRÁS DEL PANEL")
        choice("Tipo de fondo", "bgMode", BgMode.values().map { it.label }, rebuild = true) { i ->
            val m = BgMode.values()[i]
            val dim = Skin.get(this, "dim")
            if (m == BgMode.SOLID && dim < 50) Skin.set(this, "dim", 92)
            if (m != BgMode.SOLID && dim > 70) Skin.set(this, "dim", 35)
        }
        when (sk.bgMode) {
            BgMode.SOLID -> { colorRow("Color del fondo", "bgColor", false); skSlider("Opacidad", "dim", 0, 100, "%") }
            BgMode.TRANSPARENT -> note("El panel flota sobre lo que estés usando, sin oscurecer ni difuminar.")
            BgMode.BLUR -> {
                skSlider("Intensidad del difuminado", "blurR", 1, 12)
                skSlider("Oscurecer", "dim", 0, 90, "%")
                colorRow("Tinte", "bgColor", false)
                note("Usa una captura de pantalla reducida. Si tu equipo no la permite, usa el difuminado del sistema (Android 12+).")
            }
            BgMode.IMAGE -> {
                imageRow()
                skSlider("Desenfoque de la imagen", "imgBlur", 0, 12)
                skSlider("Oscurecer", "dim", 0, 90, "%")
                colorRow("Tinte", "bgColor", false)
            }
        }

        section("HOJA DEL PANEL")
        colorRow("Color de la hoja", "sheetColor", true)
        skSlider("Opacidad de la hoja (0 = sin hoja)", "sheetAlpha", 0, 100, "%")
        skSlider("Redondeo de abajo", "sheetRadius", 0, 48, " dp")

        section("BOTONES")
        choice("Forma", "shape", TileShape.values().map { it.sym + " " + it.label }, rebuild = true)
        if (sk.shape == TileShape.ROUNDED) skSlider("Redondeo de la forma", "round", 0, 50, "%")
        choice("Disposición", "layout", TileLayout.values().map { it.label }, rebuild = true)
        skSlider("Tamaño", "size", 36, 110, " dp")
        skSlider("Separación", "gap", 0, 24, " dp")
        skSlider("Columnas", "cols", 2, 6)
        if (sk.layout == TileLayout.PILL) note("En píldora se usan hasta 3 columnas.")
        skSlider("Tamaño del ícono", "iconScale", 30, 80, "%")
        if (sk.layout != TileLayout.ICON) skSlider("Tamaño del texto", "labelSize", 9, 16, " sp")
        if (sk.layout == TileLayout.PILL) toggle("Mostrar el estado (texto chico)", "showSub")

        section("COLORES")
        choice("Tema del texto", "theme", listOf("Sistema", "Claro", "Oscuro"))
        colorRow("Acento (botón encendido)", "accent", true)
        skSlider("Opacidad encendido", "onAlpha", 20, 100, "%")
        colorRow("Botón apagado", "tileOff", true)
        skSlider("Opacidad apagado", "offAlpha", 0, 100, "%")
        colorRow("Texto e íconos", "textColor", true)

        section("RELOJ")
        skSlider("Tamaño", "clockSize", 16, 96, " sp")
        choice("Alineación", "clockAlign", listOf("Izquierda", "Centro", "Derecha"))
        choice("Grosor", "clockWeight", listOf("Fino", "Normal", "Negrita"))
        toggle("Mostrar la fecha", "showDate")
        toggle("Botón de ajustes ⚙", "showGear")

        section("PRIMERA BAJADA")
        skSlider("Accesos visibles", "compact", 0, 8)
        note("Son los primeros de tu lista. Para ordenarlos, mantené apretado un acceso en el panel real.")

        section("BRILLO Y VOLUMEN")
        choice("Estilo", "sliderStyle", listOf("Horizontal", "Vertical"))
        skSlider("Tamaño de los sliders", "sliderThick", 40, 90, " dp")
        toggle("Mostrar el reproductor", "showMedia")

        section("TARJETAS Y NOTIFICACIONES")
        skSlider("Redondeo", "cardR", 0, 40, " dp")
        skSlider("Opacidad", "cardAlpha", 0, 100, "%")
        toggle("Ícono de la app en las notificaciones", "notifApp")

        section("BARRITA DE ACCESO")
        controls.addView(label("Zona de apertura"))
        val cz = Skin.prefs(this).getString("zone", "full")
        chips(zones.map { it.second }, zones.indexOfFirst { it.first == cz }.coerceAtLeast(0)) { i ->
            Skin.prefs(this).edit().putString("zone", zones[i].first).apply()
            ControlService.instance?.rebuildHandle()
        }
        slider("Opacidad de la barrita (0 = invisible)", 0, 100, "%", true,
            { Skin.prefs(this).getInt("pill_alpha", 60) },
            { Skin.prefs(this).edit().putInt("pill_alpha", it).apply() })
        skSlider("Largo de la barrita", "pillW", 24, 240, " dp", handle = true)
        skSlider("Grosor de la barrita", "pillH", 2, 14, " dp", handle = true)

        controls.addView(Button(this).apply {
            text = "Restablecer todo"; isAllCaps = false
            setOnClickListener { Skin.reset(this@BuilderActivity); buildControls(); changed(true) }
        }, LinearLayout.LayoutParams(MPB, WCB).apply { topMargin = dp(28) })

        if (::scroll.isInitialized) scroll.post { scroll.scrollTo(0, keep) }
    }
}
