package dev.cc.panel

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import android.accessibilityservice.AccessibilityService
import android.net.ConnectivityManager
import android.nfc.NfcAdapter
import android.content.pm.PackageManager
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.ViewGroup
import android.widget.Switch

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock

private const val MP = -1
private const val WC = -2

/** ScrollView que no crece más de maxH (lista de notificaciones). */
private class MaxScroll(c: Context, private val maxH: Int) : ScrollView(c) {
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
    }
}

/** Panel de ajustes rápidos, dibujado como overlay de accesibilidad. Su aspecto sale del Skin que armó el usuario. */
class PanelView(private val ctx: Context, private val onClose: () -> Unit) : FrameLayout(ctx) {

    private val sk = Skin(ctx)
    private val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    private val dark: Boolean = when (sk.theme) { 1 -> false; 2 -> true; else -> night }

    private fun withA(c: Int, pct: Int): Int {
        val a = ((c ushr 24) and 0xFF) * pct.coerceIn(0, 100) / 100
        return (c and 0x00FFFFFF) or (a shl 24)
    }
    private fun lum(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
    private fun tint(c: Int) = ColorStateList.valueOf(c)

    private val cText: Int = if (sk.textColor != 0) sk.textColor else if (dark) 0xFFF2EEF5.toInt() else 0xFF1D1B1E.toInt()
    private val cSub: Int = withA(cText, 72)
    private val cAccent: Int = if (sk.accent != 0) sk.accent
        else if (Build.VERSION.SDK_INT >= 31)
            ctx.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
        else if (dark) 0xFFCFBCFF.toInt() else 0xFF6750A4.toInt()
    private val cOnAccent: Int = if (lum(cAccent) > 0.6) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt()
    private val offBase: Int = if (sk.tileOff != 0) sk.tileOff else if (dark) 0xFF49454F.toInt() else 0xFFDDD6DE.toInt()
    private val cOn: Int = withA(cAccent, sk.onAlpha)
    private val cOff: Int = withA(offBase, sk.offAlpha)
    private val cCard: Int = withA(if (sk.tileOff != 0) sk.tileOff else if (dark) 0xFF2B2930.toInt() else 0xFFE6E0E3.toInt(), sk.cardAlpha)
    private val cTrack: Int = if (dark) 0xFF49454F.toInt() else 0xFFD0C9D3.toInt()
    private val cSheet: Int = withA(if (sk.sheetColor != 0) sk.sheetColor else if (dark) 0xFF141218.toInt() else 0xFFF3EEF0.toInt(), sk.sheetAlpha)

    private class Tile(
        val label: String, val icon: Int, val on: () -> Boolean,
        val onText: String, val offText: String, val subFn: (() -> String)? = null, val tap: () -> Unit
    ) {
        var key: String = label
        var iconDrawable: android.graphics.drawable.Drawable? = null
    }

    /** Una instancia dibujada de un acceso (un mismo acceso puede estar en la vista acotada y en la completa). */
    private class TileView(
        val t: Tile, val box: View, val shape: Shaped, val img: ImageView,
        val title: TextView?, val sub: TextView?, val pill: Boolean
    )
    private val holders = mutableListOf<TileView>()

    private val prefs = ctx.getSharedPreferences("cc", Context.MODE_PRIVATE)
    private val cam = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var torch = false
    private val camId: String? = try {
        cam.cameraIdList.firstOrNull {
            cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) { null }
    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == camId) { torch = enabled; refresh() }
        }
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun wifi() = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private fun settingsOn(key: String) = Settings.Global.getInt(ctx.contentResolver, key, 0) == 1

    private fun secureOk() = ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    private fun secureOn(key: String) = Settings.Secure.getInt(ctx.contentResolver, key, 0) == 1
    private fun toggleSecure(key: String, fallback: String) {
        val v = if (secureOn(key)) 0 else 1
        if (secureOk()) { Settings.Secure.putInt(ctx.contentResolver, key, v); refresh() }
        else priv("settings put secure $key $v", fallback)
    }
    private fun later(block: () -> Unit) { onClose(); android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(block, 450) }
    private fun svc(action: Int) = later { ControlService.instance?.global(action) }
    private fun nfcOn() = try { NfcAdapter.getDefaultAdapter(ctx)?.isEnabled == true } catch (e: Exception) { false }
    private fun mobileData() = Settings.Global.getInt(ctx.contentResolver, "mobile_data", 1) == 1
    private fun dataSaver() = try {
        (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).restrictBackgroundStatus !=
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
    } catch (e: Exception) { false }

    private fun open(action: String, pkg: Boolean = false) {
        try {
            val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pkg) i.data = Uri.parse("package:${ctx.packageName}")
            ctx.startActivity(i)
            onClose()
        } catch (e: Exception) {
            Toast.makeText(ctx, "No se pudo abrir el ajuste", Toast.LENGTH_SHORT).show()
        }
    }

    /** Usa Shizuku si está autorizado; si no, abre el ajuste del sistema correspondiente. */
    private fun priv(cmd: String, fallback: String) {
        if (Privileged.available()) Privileged.run(cmd) { postDelayed({ refresh() }, 700) }
        else open(fallback)
    }

    private val tiles = listOf(
        Tile("Internet", R.drawable.ic_wifi, { wifi().isWifiEnabled }, "Wi-Fi activado", "Desactivado") {
            priv("svc wifi " + (if (wifi().isWifiEnabled) "disable" else "enable"), Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        },
        Tile("Bluetooth", R.drawable.ic_bt,
            { try { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true } catch (e: Exception) { false } },
            "Activado", "Desactivado") {
            val on = try { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true } catch (e: Exception) { false }
            priv("svc bluetooth " + (if (on) "disable" else "enable"), Settings.ACTION_BLUETOOTH_SETTINGS)
        },
        Tile("Linterna", R.drawable.ic_torch, { torch }, "Encendida", "Apagada") {
            try { camId?.let { cam.setTorchMode(it, !torch) } } catch (e: Exception) {}
        },
        Tile("Giro automático", R.drawable.ic_rotate,
            { Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1 },
            "Activado", "Desactivado") {
            if (Settings.System.canWrite(ctx)) {
                val cur = Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1 - cur)
                refresh()
            } else open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true)
        },
        Tile("Ahorro de batería", R.drawable.ic_battery,
            { (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode },
            "Activado", "Desactivado") {
            val on = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
            priv("settings put global low_power " + (if (on) 0 else 1), Settings.ACTION_BATTERY_SAVER_SETTINGS)
        },
        Tile("No molestar", R.drawable.ic_dnd,
            { nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL },
            "Activado", "Desactivado") {
            if (nm.isNotificationPolicyAccessGranted) {
                val all = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
                nm.setInterruptionFilter(
                    if (all) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    else NotificationManager.INTERRUPTION_FILTER_ALL
                )
                postDelayed({ refresh() }, 300)
            } else open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        },
        Tile("Modo avión", R.drawable.ic_airplane, { settingsOn(Settings.Global.AIRPLANE_MODE_ON) }, "Activado", "Desactivado") {
            val on = settingsOn(Settings.Global.AIRPLANE_MODE_ON)
            priv("cmd connectivity airplane-mode " + (if (on) "disable" else "enable"), Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        },
        Tile("Tema oscuro", R.drawable.ic_dark, { night }, "Activado", "Desactivado") {
            priv("cmd uimode night " + (if (night) "no" else "yes"), Settings.ACTION_DISPLAY_SETTINGS)
            onClose()
        },
        Tile("Ubicación", R.drawable.ic_location,
            { (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled },
            "Activada", "Desactivada") {
            val on = (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
            priv("cmd location set-location-enabled " + (if (on) "false" else "true"), Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        },
        Tile("Datos móviles", R.drawable.ic_data, { mobileData() }, "Activados", "Desactivados") {
            priv("svc data " + (if (mobileData()) "disable" else "enable"), Settings.ACTION_DATA_ROAMING_SETTINGS)
        },
        Tile("NFC", R.drawable.ic_nfc, { nfcOn() }, "Activado", "Desactivado") {
            priv("svc nfc " + (if (nfcOn()) "disable" else "enable"), Settings.ACTION_NFC_SETTINGS)
        },
        Tile("Sonido", R.drawable.ic_sound, { audio.ringerMode != AudioManager.RINGER_MODE_NORMAL }, "", "",
            { when (audio.ringerMode) { AudioManager.RINGER_MODE_VIBRATE -> "Vibrar"; AudioManager.RINGER_MODE_SILENT -> "Silencio"; else -> "Sonar" } }) {
            if (nm.isNotificationPolicyAccessGranted) {
                audio.ringerMode = when (audio.ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                postDelayed({ refresh() }, 200)
            } else open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        },
        Tile("Brillo automático", R.drawable.ic_brightness,
            { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1 }, "Activado", "Desactivado") {
            if (Settings.System.canWrite(ctx)) {
                val cur = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 1 - cur)
                refresh()
            } else open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true)
        },
        Tile("Luz nocturna", R.drawable.ic_night, { secureOn("night_display_activated") }, "Activada", "Desactivada") {
            toggleSecure("night_display_activated", Settings.ACTION_DISPLAY_SETTINGS)
        },
        Tile("Atenuar extra", R.drawable.ic_dim, { secureOn("reduce_bright_colors_activated") }, "Activado", "Desactivado") {
            toggleSecure("reduce_bright_colors_activated", Settings.ACTION_DISPLAY_SETTINGS)
        },
        Tile("Invertir colores", R.drawable.ic_invert, { secureOn("accessibility_display_inversion_enabled") }, "Activado", "Desactivado") {
            toggleSecure("accessibility_display_inversion_enabled", Settings.ACTION_ACCESSIBILITY_SETTINGS)
        },
        Tile("Ahorro de datos", R.drawable.ic_datasaver, { dataSaver() }, "Activado", "Desactivado") {
            priv("cmd netpolicy set restrict-background " + (if (dataSaver()) "false" else "true"), Settings.ACTION_DATA_ROAMING_SETTINGS)
        },
        Tile("Pantalla activa", R.drawable.ic_screen,
            { Settings.Global.getInt(ctx.contentResolver, "stay_on_while_plugged_in", 0) != 0 }, "Al cargar", "Normal") {
            val on = Settings.Global.getInt(ctx.contentResolver, "stay_on_while_plugged_in", 0) != 0
            priv("settings put global stay_on_while_plugged_in " + (if (on) 0 else 7), Settings.ACTION_DISPLAY_SETTINGS)
        },
        Tile("Captura", R.drawable.ic_screenshot, { false }, "", "Tocar") { svc(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) },
        Tile("Bloquear", R.drawable.ic_padlock, { false }, "", "Pantalla") { svc(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) },
        Tile("Apagar", R.drawable.ic_power, { false }, "", "Menú") { svc(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) },
        Tile("Panel del sistema", R.drawable.ic_lock, { prefs.getBoolean("lock", false) }, "Bloqueado", "Libre") {
            if (Privileged.available()) {
                val locked = prefs.getBoolean("lock", false)
                Privileged.run("cmd statusbar send-disable-flag " + (if (locked) "none" else "statusbar-expansion")) {
                    prefs.edit().putBoolean("lock", !locked).apply(); refresh()
                }
            } else Toast.makeText(ctx, "Autorizá Shizuku en la app", Toast.LENGTH_SHORT).show()
        }
    )

    // ---- estado de la vista ----
    private var u = 0
    private val mediaTitle = TextView(ctx)
    private val mediaArtist = TextView(ctx)
    private val mediaArt = ImageView(ctx)
    private val mediaPlay = ImageView(ctx)
    private val vsliders = mutableListOf<VSlider>()
    private var warned = false

    // panel en dos etapas: 0 = cerrado, 1 = vista acotada (notificaciones), 2 = vista completa
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var progress = 0f
    private var anim: ValueAnimator? = null
    private val backdrop = FrameLayout(ctx)
    private val blurImg = ImageView(ctx)
    private val scrim = View(ctx)
    private var blurK = 0f
    private var winBlur = false
    private var lastBlur = -1
    var blurHook: ((Int) -> Unit)? = null
    private var cacheKey: String = ""
    private var cacheBmp: Bitmap? = null

    private val cClock = TextView(ctx)
    private val cDate = TextView(ctx)
    private val notifList = LinearLayout(ctx)
    private val clearBtn = TextView(ctx)
    private lateinit var notifScroll: MaxScroll
    private val dm = ctx.resources.displayMetrics
    private val land = dm.widthPixels > dm.heightPixels
    private val extra: List<Tile> by lazy { thirdParty() }
    private var work: MutableList<String>? = null
    private val sheetW = if (land) minOf(dm.widthPixels, dp(560)) else dm.widthPixels
    private val sheet = LinearLayout(ctx)
    private lateinit var pager: FrameLayout
    private lateinit var page0: LinearLayout
    private lateinit var page1: LinearLayout
    private lateinit var qs: FrameLayout
    private lateinit var qsCompact: View
    private lateinit var qsFull: ScrollView
    private lateinit var tabInd: View
    private val tabLabels = mutableListOf<TextView>()
    private var tabPos = 0f
    private var tabAnim: ValueAnimator? = null
    private var hC = 0
    private var hF = 0
    private var stage1H = 0
    private var hdrag = false
    private var lX = 0f
    private var onNotifs = false
    private var editor: ScrollView? = null
    private val expandedKeys = mutableSetOf<String>()
    private val expandedGroups = mutableSetOf<String>()
    private var dX = 0f
    private var dY = 0f
    private var lY = 0f
    private var drag = false
    private var vt: VelocityTracker? = null
    private var closing = false
    private var lastAct = SystemClock.uptimeMillis()

    init {
        scrim.setBackgroundColor(scrimColor())
        backdrop.addView(blurImg, LayoutParams(MP, MP))
        backdrop.addView(scrim, LayoutParams(MP, MP))
        blurImg.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(backdrop, LayoutParams(MP, MP))
        addView(sheet, LayoutParams(sheetW, WC, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        sheet.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (::qs.isInitialized) stage1H = sheet.height - (qs.layoutParams.height - hC)
            setProgress(progress)
        }
        if (sk.bgMode == BgMode.IMAGE) loadImage()
        buildShade()
        setProgress(0f)
        refresh()
    }

    // ======================= helpers de vistas =======================

    private fun hrow() = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private fun vcol() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private fun round(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    private fun setBrightness(v: Int) {
        if (Settings.System.canWrite(ctx)) {
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceAtLeast(1))
        } else if (!warned) { warned = true; open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true) }
    }

    // ======================= accesos (botones) =======================

    /** Arma un acceso con la forma, la disposición y los tamaños del Skin. */
    private fun tileView(t: Tile, cellW: Int): TileView {
        val pill = sk.layout == TileLayout.PILL
        val sizePx = dp(sk.size)
        val shape = Shaped(sk.shape, cOff, sk.round)
        val box = LinearLayout(ctx).apply {
            isClickable = true
            setOnClickListener { t.tap() }
            setOnLongClickListener { showEditor(); true }
        }
        val d = if (pill) (sizePx * 0.72f).toInt() else minOf(sizePx, cellW)
        val pad = (d * (100 - sk.iconScale) / 200f).toInt().coerceAtLeast(0)
        val img = ImageView(ctx).apply { setIcon(this, t); background = shape; setPadding(pad, pad, pad, pad) }
        var title: TextView? = null
        var sub: TextView? = null
        if (pill) {
            box.orientation = LinearLayout.HORIZONTAL
            box.gravity = Gravity.CENTER_VERTICAL
            val vpad = ((sizePx - d) / 2f).toInt()
            box.setPadding(vpad, vpad, dp(12), vpad)
            box.addView(img, LinearLayout.LayoutParams(d, d))
            val col = vcol().apply { setPadding(dp(10), 0, 0, 0) }
            title = TextView(ctx).apply {
                text = t.label; textSize = sk.labelSize.toFloat(); maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            col.addView(title)
            if (sk.showSub) {
                sub = TextView(ctx).apply {
                    textSize = (sk.labelSize - 2).coerceAtLeast(8).toFloat(); maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                col.addView(sub)
            }
            box.addView(col, LinearLayout.LayoutParams(0, WC, 1f))
        } else {
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.addView(img, LinearLayout.LayoutParams(d, d))
            if (sk.layout == TileLayout.LABEL) {
                title = TextView(ctx).apply {
                    text = t.label; textSize = sk.labelSize.toFloat(); maxLines = 1; gravity = Gravity.CENTER
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                box.addView(title, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(4) })
            }
        }
        return TileView(t, box, shape, img, title, sub, pill)
    }

    private fun paintTile(h: TileView) {
        val on = try { h.t.on() } catch (e: Exception) { false }
        val fg = if (on) cOnAccent else cText
        if (h.pill) {
            h.box.background = round(if (on) cOn else cOff, sk.size / 2)
            h.shape.setFill(if (on) withA(cOnAccent, 22) else withA(cText, 14))
            h.title?.setTextColor(fg)
            h.sub?.setTextColor(if (on) cOnAccent else cSub)
            h.sub?.text = h.t.subFn?.invoke() ?: if (on) h.t.onText else h.t.offText
        } else {
            h.shape.setFill(if (on) cOn else cOff)
            h.title?.setTextColor(cText)
        }
        h.img.imageTintList = tint(fg)
    }

    /** Cuadrícula de accesos del ancho [w]. */
    private fun grid(list: List<Tile>, w: Int): View {
        val cols = (if (sk.layout == TileLayout.PILL) minOf(sk.cols, 3) else sk.cols).coerceAtLeast(1)
        val m = dp(sk.gap) / 2
        val inner = (w / cols - 2 * m).coerceAtLeast(dp(24))
        val col = vcol()
        list.chunked(cols).forEach { chunk ->
            val line = hrow()
            chunk.forEach { t ->
                val tv = tileView(t, inner)
                holders.add(tv)
                line.addView(tv.box, LinearLayout.LayoutParams(inner, WC).apply { setMargins(m, m, m, m) })
            }
            col.addView(line)
        }
        return col
    }

    // ======================= brillo y volumen =======================

    private fun sliderRow(icon: Int, max: Int, get: () -> Int, set: (Int) -> Unit): View {
        val row = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), 0, dp(18), 0)
            background = round(cCard, sk.cardR)
        }
        row.addView(ImageView(ctx).apply { setImageResource(icon); imageTintList = tint(cSub) },
            LinearLayout.LayoutParams(dp(24), dp(24)))
        val sb = SeekBar(ctx).apply {
            this.max = max; progress = get()
            progressTintList = tint(cAccent)
            thumbTintList = tint(cAccent)
            progressBackgroundTintList = tint(cTrack)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) set(p) }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        row.addView(sb, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(10) })
        row.layoutParams = LinearLayout.LayoutParams(MP, dp(sk.sliderThick)).apply { topMargin = dp(10) }
        return row
    }

    private fun vBright(): View = VSlider(ctx, R.drawable.ic_brightness, 255, cOff, cAccent,
        get = { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) },
        set = { v -> setBrightness(v) },
        iconOnFill = cOnAccent, iconOffFill = cText
    ).also { vsliders.add(it) }

    private fun vVol(): View = VSlider(ctx, R.drawable.ic_volume, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), cOff, cAccent,
        get = { audio.getStreamVolume(AudioManager.STREAM_MUSIC) },
        set = { v -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) },
        iconOnFill = cOnAccent, iconOffFill = cText
    ).also { vsliders.add(it) }

    // ======================= multimedia =======================

    private fun controller(): MediaController? = try {
        (ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager)
            .getActiveSessions(ComponentName(ctx, MediaListener::class.java)).firstOrNull()
    } catch (e: Exception) { null }

    private fun mediaKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        postDelayed({ refreshMedia() }, 350)
    }

    private fun refreshMedia() {
        val c = controller()
        val md = c?.metadata
        mediaTitle.text = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Sin reproducción"
        mediaArtist.text = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (bmp != null) {
            mediaArt.setPadding(0, 0, 0, 0); mediaArt.imageTintList = null; mediaArt.setImageBitmap(bmp)
        } else {
            mediaArt.setPadding(dp(9), dp(9), dp(9), dp(9))
            mediaArt.imageTintList = tint(cText)
            mediaArt.setImageResource(R.drawable.ic_music)
        }
        mediaPlay.setImageResource(if (c?.playbackState?.state == PlaybackState.STATE_PLAYING) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun mediaCard(): View {
        val box = vcol().apply {
            setPadding(dp(12), dp(10), dp(12), dp(6))
            background = round(cCard, sk.cardR)
        }
        mediaArt.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = round(withA(cText, 16), 10); clipToOutline = true
        }
        mediaTitle.apply { textSize = 12f; setTextColor(cText); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        mediaArtist.apply { textSize = 10f; setTextColor(cSub); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val top = hrow().apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(mediaArt.free(), LinearLayout.LayoutParams(dp(40), dp(40)))
        val col = vcol().apply { setPadding(dp(8), 0, 0, 0) }
        col.addView(mediaTitle.free()); col.addView(mediaArtist.free())
        top.addView(col, LinearLayout.LayoutParams(0, WC, 1f))
        box.addView(top)
        box.addView(View(ctx), LinearLayout.LayoutParams(1, 0, 1f))
        val ctrl = hrow().apply { gravity = Gravity.CENTER }
        fun cbtn(ic: Int, code: Int) = ImageView(ctx).apply {
            setImageResource(ic); imageTintList = tint(cText)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { mediaKey(code) }
        }
        ctrl.addView(cbtn(R.drawable.ic_prev, KeyEvent.KEYCODE_MEDIA_PREVIOUS), LinearLayout.LayoutParams(0, dp(40), 1f))
        mediaPlay.apply {
            setImageResource(R.drawable.ic_play); imageTintList = tint(cText)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        }
        ctrl.addView(mediaPlay.free(), LinearLayout.LayoutParams(0, dp(40), 1f))
        ctrl.addView(cbtn(R.drawable.ic_next, KeyEvent.KEYCODE_MEDIA_NEXT), LinearLayout.LayoutParams(0, dp(40), 1f))
        box.addView(ctrl, LinearLayout.LayoutParams(MP, WC))
        return box
    }

    // ======================= refresco =======================

    fun refresh() {
        cClock.text = SimpleDateFormat("H:mm", Locale.getDefault()).format(Date())
        cDate.text = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Date())
        holders.forEach { paintTile(it) }
        refreshMedia(); vsliders.forEach { it.sync() }
    }

    // ======================= PANEL EN DOS ETAPAS =======================

    private fun <T : View> T.free(): T { (parent as? ViewGroup)?.removeView(this); return this }

    private fun statusBarH(): Int {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun setIcon(img: ImageView, t: Tile) {
        val d = t.iconDrawable
        if (d != null) img.setImageDrawable(d) else img.setImageResource(t.icon)
    }

    /** 0 = cerrado, 1 = hoja con notificaciones, 2 = accesos expandidos (empujan las notificaciones hacia abajo). */
    fun setProgress(v: Float) {
        progress = v.coerceIn(0f, 2f)
        val p1 = progress.coerceAtMost(1f)
        val p2 = (progress - 1f).coerceAtLeast(0f)
        val sh = dm.heightPixels.toFloat()
        val h1 = if (stage1H > 0) stage1H.toFloat() else sh
        sheet.translationY = -(1f - p1) * h1
        if (::qs.isInitialized) {
            val h = (hC + (hF - hC) * p2).toInt()
            val lp = qs.layoutParams
            if (lp.height != h) { lp.height = h; qs.layoutParams = lp }
            qsCompact.alpha = 1f - p2
            qsCompact.visibility = if (p2 < 1f) View.VISIBLE else View.INVISIBLE
            qsFull.alpha = p2
            qsFull.visibility = if (p2 > 0f) View.VISIBLE else View.INVISIBLE
        }
        blurImg.alpha = p1 * blurK
        scrim.alpha = p1
        if (winBlur) {
            val r = (70f * p1).toInt() / 7 * 7
            if (r != lastBlur) { lastBlur = r; blurHook?.invoke(r) }
        }
    }

    fun animateTo(target: Float, end: (() -> Unit)? = null) {
        lastAct = SystemClock.uptimeMillis()
        anim?.cancel()
        var cancelled = false
        val a = ValueAnimator.ofFloat(progress, target)
        a.duration = 220
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { setProgress(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(an: Animator) { cancelled = true }
            override fun onAnimationEnd(an: Animator) { if (!cancelled) end?.invoke() }
        })
        anim = a
        a.start()
    }

    /** Al soltar: elige etapa según velocidad o cercanía. Cerrar delega en onClose (que anima hasta 0). */
    private fun settle(vy: Float) {
        val t = when {
            vy > 1200f -> if (progress < 1f) 1f else 2f
            vy < -1200f -> if (progress > 1f) 1f else 0f
            progress < 0.5f -> 0f
            progress < 1.5f -> 1f
            else -> 2f
        }
        if (t == 0f) onClose() else animateTo(t)
    }

    fun dragFromHandle(dy: Float) {
        if (closing) return
        lastAct = SystemClock.uptimeMillis()
        anim?.cancel()
        val hc = if (stage1H > 0) stage1H.toFloat() else dm.heightPixels * 0.5f
        setProgress((dy / hc).coerceIn(0f, 1f))
    }

    fun release(vy: Float) = settle(vy)

    // ---- guardián: lo usa el servicio para cerrar sin depender de la animación ----

    /** Marca el panel como "en cierre": deja de procesar toques. */
    fun beginClose() { closing = true; drag = false; hdrag = false }

    fun stopAnim() { anim?.cancel(); tabAnim?.cancel() }

    /** Abierto pero sin contenido visible ni actividad: es el estado que dejaba la pantalla "colgada". */
    fun isStuck(): Boolean =
        !closing && progress < 0.02f && anim?.isRunning != true && SystemClock.uptimeMillis() - lastAct > 1500

    /** Para la vista previa del constructor: etapa 1 (acotada) o 2 (completa) y pestaña 0/1. */
    fun previewState(stage: Int, tab: Int) {
        tabPos = tab.toFloat(); applyTab()
        setProgress(if (stage <= 1) 1f else 2f)
    }

    private fun dragStep(delta: Float) {
        val sh = dm.heightPixels.toFloat()
        val h1 = if (stage1H > 0) stage1H.toFloat() else sh
        val h2 = maxOf((hF - hC).toFloat(), dp(160).toFloat())
        val span = if (progress < 1f || (progress == 1f && delta < 0f)) h1 else h2
        setProgress(progress + delta / span)
    }

    private fun blockedByScroll(down: Boolean): Boolean {
        if (tabPos > 0.5f) return false
        val s: View? = if (progress >= 1.5f) (if (::qsFull.isInitialized) qsFull else null) else (if (::notifScroll.isInitialized) notifScroll else null)
        return s?.canScrollVertically(if (down) -1 else 1) == true
    }

    private fun touchOnNotifs(e: MotionEvent): Boolean {
        if (tabPos >= 0.5f || !::notifScroll.isInitialized) return false
        val loc = IntArray(2)
        notifScroll.getLocationOnScreen(loc)
        return e.rawX >= loc[0] && e.rawX <= loc[0] + notifScroll.width &&
            e.rawY >= loc[1] && e.rawY <= loc[1] + notifScroll.height
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // Si el servicio ya mandó cerrar este panel, no toca nada: así no se cancela la animación de salida.
        if (closing || editor != null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastAct = SystemClock.uptimeMillis()
                dX = e.rawX; dY = e.rawY; lY = e.rawY; lX = e.rawX; drag = false; hdrag = false
                onNotifs = touchOnNotifs(e)
                anim?.cancel(); tabAnim?.cancel()
                vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dy = e.rawY - dY
                val dx = e.rawX - dX
                if (!drag && !hdrag) {
                    if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) * 1.2f && !blockedByScroll(dy > 0f)) {
                        drag = true; lY = e.rawY
                        return true
                    }
                    // Pestañas: deslizar a izquierda/derecha (no sobre las notificaciones, que se descartan así)
                    if (!onNotifs && progress >= 0.9f && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        hdrag = true; lX = e.rawX
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (closing) return false
        if (editor != null) return true
        lastAct = SystemClock.uptimeMillis()
        vt?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!drag && !hdrag && Math.abs(e.rawY - dY) > slop) { drag = true; lY = e.rawY }
                if (drag) { dragStep(e.rawY - lY); lY = e.rawY }
                if (hdrag) { tabStep(-(e.rawX - lX) / sheetW); lX = e.rawX }
            }
            MotionEvent.ACTION_UP -> {
                vt?.computeCurrentVelocity(1000)
                val vy = vt?.yVelocity ?: 0f
                val vx = vt?.xVelocity ?: 0f
                if (hdrag) settleTab(vx)
                else if (drag) settle(vy)
                else if (Math.abs(e.rawY - dY) < slop && Math.abs(e.rawX - dX) < slop) onClose()
                drag = false; hdrag = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (hdrag) settleTab(0f) else if (drag) settle(0f)
                drag = false; hdrag = false
            }
        }
        return true
    }

    // La pantalla giró: el panel se armó para otra orientación, así que se cierra.
    override fun onConfigurationChanged(c: Configuration?) {
        super.onConfigurationChanged(c)
        onClose()
    }

    // ---- fondo detrás del panel: sólido / transparente / difuminado / imagen ----

    private fun scrimColor(): Int =
        if (sk.bgMode == BgMode.TRANSPARENT) 0x00000000 else withA(sk.bgColor, sk.dim)

    private fun loadImage() {
        val f = Skin.imageFile(ctx)
        if (!f.exists()) { scrim.setBackgroundColor(withA(sk.bgColor, maxOf(sk.dim, 60))); return }
        val key = f.lastModified().toString() + ":" + sk.imgBlur
        var bmp: Bitmap? = if (cacheKey == key) cacheBmp else null
        if (bmp == null) {
            bmp = try {
                val src = BitmapFactory.decodeFile(f.path)
                if (src != null && sk.imgBlur > 0) {
                    val w = 160
                    val h = (src.height * w / src.width).coerceAtLeast(1)
                    val small = Bitmap.createScaledBitmap(src, w, h, true)
                    if (small !== src) src.recycle()
                    Blur.apply(small, sk.imgBlur, 3)
                } else src
            } catch (e: Throwable) { null }
            cacheKey = key; cacheBmp = bmp
        }
        if (bmp != null) { blurImg.setImageBitmap(bmp); blurK = 1f }
    }

    /** Captura difuminada de lo que hay detrás (solo en modo "Difuminado"). */
    fun setBackdrop(b: Bitmap) {
        if (sk.bgMode != BgMode.BLUR) return
        blurImg.setImageBitmap(b)
        val a = ValueAnimator.ofFloat(blurK, 1f)
        a.duration = 160
        a.addUpdateListener { blurK = it.animatedValue as Float; setProgress(progress) }
        a.start()
    }

    fun useWindowBlur() {
        if (sk.bgMode != BgMode.BLUR) return
        winBlur = true
        setProgress(progress)
    }

    /** Sin ninguna forma de difuminar: oscurece más para que el texto siga leyéndose. */
    fun noBlur() {
        if (sk.bgMode == BgMode.BLUR) scrim.setBackgroundColor(withA(sk.bgColor, maxOf(sk.dim, 60)))
    }

    // ======================= ACCESOS: orden, activar/quitar, apps de terceros =======================

    private fun allTiles(): List<Tile> = tiles + extra

    private fun defaultOrder(): List<String> = tiles.map { it.key }

    private fun enabledKeys(): List<String> {
        val raw = prefs.getString("tiles_custom", null) ?: prefs.getString("tiles_aosp", null) ?: return defaultOrder()
        val all = allTiles().map { it.key }
        return raw.split("\n").filter { it.isNotEmpty() && it in all }
    }

    private fun shown(): List<Tile> {
        val m = allTiles().associateBy { it.key }
        return enabledKeys().mapNotNull { m[it] }
    }

    /** Accesos de apps (TileService). Solo se pueden accionar vía Shizuku con `cmd statusbar click-tile`. */
    private fun thirdParty(): List<Tile> {
        val pm = ctx.packageManager
        val list = try { pm.queryIntentServices(Intent("android.service.quicksettings.action.QS_TILE"), 0) }
                   catch (e: Exception) { emptyList() }
        return list.filter { it.serviceInfo.packageName != ctx.packageName }.map { ri ->
            val si = ri.serviceInfo
            val comp = si.packageName + "/" + si.name
            val label = try { si.loadLabel(pm).toString() } catch (e: Exception) { si.packageName }
            val t = Tile(label, 0, { false }, "", "App", null) {
                if (Privileged.available()) {
                    Privileged.run("cmd statusbar add-tile $comp; cmd statusbar click-tile $comp")
                    onClose()
                } else Toast.makeText(ctx, "Los accesos de apps necesitan Shizuku", Toast.LENGTH_SHORT).show()
            }
            t.key = "tp:$comp"
            t.iconDrawable = try { si.loadIcon(pm) } catch (e: Exception) { null }
            t
        }.sortedBy { it.label.lowercase() }
    }

    private fun pillButton(label: String, tc: Int, bg: Int, action: () -> Unit): TextView = TextView(ctx).apply {
        text = label; textSize = 13f; setTextColor(tc); gravity = Gravity.CENTER
        setPadding(dp(16), dp(8), dp(16), dp(8)); background = round(bg, 20)
        setOnClickListener { action() }
    }

    private fun editRow(t: Tile, on: Boolean, i: Int, w: MutableList<String>, tc: Int, bg: Int): View {
        val row = hrow().apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(8), dp(8))
            background = round(bg, 16); alpha = if (on) 1f else 0.6f
        }
        val ic = ImageView(ctx).apply { setIcon(this, t); imageTintList = tint(tc) }
        row.addView(ic, LinearLayout.LayoutParams(dp(26), dp(26)))
        row.addView(TextView(ctx).apply {
            text = t.label; textSize = 15f; setTextColor(tc); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, WC, 1f))
        fun arrow(txt: String, delta: Int) = TextView(ctx).apply {
            text = txt; textSize = 18f; setTextColor(tc); setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                val j = i + delta
                val k = w[i]; w[i] = w[j]; w[j] = k
                post { showEditor() }
            }
        }
        if (on && i > 0) row.addView(arrow("▲", -1))
        if (on && i < w.size - 1) row.addView(arrow("▼", 1))
        row.addView(Switch(ctx).apply {
            isChecked = on
            setOnCheckedChangeListener { _, c ->
                if (c) w.add(t.key) else w.remove(t.key)
                post { showEditor() }
            }
        })
        return row
    }

    private fun closeEditor() {
        editor?.let { removeView(it) }
        editor = null
        sheet.visibility = View.VISIBLE
    }

    /** Editor de accesos (mantener apretado un acceso). Es una capa que tapa la hoja; usa colores fijos para leerse siempre. */
    private fun showEditor() {
        val w = work ?: enabledKeys().toMutableList().also { work = it }
        val tc = if (dark) 0xFFF2EEF5.toInt() else 0xFF1D1B1E.toInt()
        val sc = withA(tc, 70)
        val rowBg = if (dark) 0xFF2B2930.toInt() else 0xFFE6E0E3.toInt()
        val layerBg = if (dark) 0xF0141218.toInt() else 0xF0F3EEF0.toInt()
        val map = allTiles().associateBy { it.key }
        val keep = editor?.scrollY ?: 0
        val col = vcol()
        col.setPadding(dp(16), statusBarH() + dp(12), dp(16), dp(32))
        val top = hrow().apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(ctx).apply { text = "Editar accesos"; textSize = 20f; setTextColor(tc) }, LinearLayout.LayoutParams(0, WC, 1f))
        top.addView(pillButton("Listo", tc, rowBg) {
            prefs.edit().putString("tiles_custom", w.joinToString("\n")).apply()
            work = null
            closeEditor()
            buildShade(); setProgress(progress); refresh()
        })
        col.addView(top)
        col.addView(TextView(ctx).apply {
            text = "Activá o quitá accesos y ordenalos con las flechas. Los de apps necesitan Shizuku."
            textSize = 12f; setTextColor(sc); setPadding(0, dp(6), 0, dp(8))
        })
        w.forEachIndexed { i, k ->
            map[k]?.let { col.addView(editRow(it, true, i, w, tc, rowBg), LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6) }) }
        }
        allTiles().filter { it.key !in w }.forEach {
            col.addView(editRow(it, false, -1, w, tc, rowBg), LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6) })
        }
        col.addView(pillButton("Restablecer", sc, rowBg) {
            prefs.edit().remove("tiles_custom").remove("tiles_aosp").apply()
            work = null
            showEditor()
        }, LinearLayout.LayoutParams(WC, WC).apply { topMargin = dp(12); gravity = Gravity.CENTER_HORIZONTAL })
        val sv = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER; isClickable = true
            setBackgroundColor(layerBg)
        }
        sv.addView(col)
        editor?.let { removeView(it) }
        editor = sv
        sheet.visibility = View.INVISIBLE
        addView(sv, LayoutParams(sheetW, MP, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        sv.post { sv.scrollTo(0, keep) }
    }

    // ======================= HOJA: pestañas, accesos que empujan, notificaciones =======================

    private fun natH(v: View, w: Int): Int {
        v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        return v.measuredHeight
    }

    private fun applyTab() {
        if (!::page0.isInitialized) return
        page0.translationX = -tabPos * sheetW
        page1.translationX = (1f - tabPos) * sheetW
        page0.visibility = if (tabPos >= 1f) View.INVISIBLE else View.VISIBLE
        page1.visibility = if (tabPos <= 0f) View.INVISIBLE else View.VISIBLE
        tabInd.translationX = tabPos * (sheetW / 2f) + (sheetW / 4f - dp(14))
        tabLabels.forEachIndexed { i, tv -> tv.alpha = if (i == 0) 1f - 0.45f * tabPos else 0.55f + 0.45f * tabPos }
    }

    private fun tabStep(frac: Float) { tabPos = (tabPos + frac).coerceIn(0f, 1f); applyTab() }

    private fun animateTab(target: Float) {
        tabAnim?.cancel()
        val a = ValueAnimator.ofFloat(tabPos, target)
        a.duration = 200
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { tabPos = it.animatedValue as Float; applyTab() }
        tabAnim = a
        a.start()
    }

    private fun settleTab(vx: Float) {
        animateTab(when { vx < -800f -> 1f; vx > 800f -> 0f; tabPos > 0.5f -> 1f; else -> 0f })
    }

    private fun buildTabBar(): View {
        val bar = FrameLayout(ctx)
        val row = hrow()
        tabLabels.clear()
        listOf("Notificaciones", "Controles").forEachIndexed { i, name ->
            val tv = TextView(ctx).apply {
                text = name; textSize = 13f; setTextColor(cText); gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(10)); setOnClickListener { animateTab(i.toFloat()) }
            }
            tabLabels.add(tv)
            row.addView(tv, LinearLayout.LayoutParams(0, WC, 1f))
        }
        bar.addView(row, FrameLayout.LayoutParams(MP, WC))
        tabInd = View(ctx).apply { background = round(cAccent, 2) }
        bar.addView(tabInd, FrameLayout.LayoutParams(dp(28), dp(3), Gravity.BOTTOM or Gravity.START))
        return bar
    }

    private fun buildHeader(): View {
        cClock.free(); cDate.free()
        val g = when (sk.clockAlign) { 1 -> Gravity.CENTER_HORIZONTAL; 2 -> Gravity.END; else -> Gravity.START }
        cClock.textSize = sk.clockSize.toFloat(); cClock.setTextColor(cText); cClock.gravity = g
        cClock.typeface = when (sk.clockWeight) {
            0 -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
            2 -> Typeface.create("sans-serif", Typeface.BOLD)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
        cDate.textSize = 13f; cDate.setTextColor(cSub); cDate.gravity = g
        cDate.visibility = if (sk.showDate) View.VISIBLE else View.GONE
        val col = vcol()
        col.addView(cClock, LinearLayout.LayoutParams(MP, WC))
        col.addView(cDate, LinearLayout.LayoutParams(MP, WC))
        val wrap = hrow().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(8), dp(6), dp(10)) }
        wrap.addView(col, LinearLayout.LayoutParams(0, WC, 1f))
        if (sk.showGear) {
            val gear = TextView(ctx).apply {
                text = "⚙"; textSize = 20f; setTextColor(cText); gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cOff) }
                setOnClickListener { open(Settings.ACTION_SETTINGS) }
            }
            wrap.addView(gear, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        return wrap
    }

    /** Franja de accesos de la primera etapa: los primeros N accesos activos. */
    private fun buildQsCompact(w: Int): View {
        val list = shown().take(sk.compact)
        return if (list.isEmpty()) View(ctx) else grid(list, w)
    }

    private fun addClearTo(parent: LinearLayout) {
        clearBtn.free()
        val lp = LinearLayout.LayoutParams(WC, WC)
        lp.gravity = Gravity.CENTER_HORIZONTAL; lp.topMargin = dp(8)
        parent.addView(clearBtn, lp)
    }

    private fun buildShade() {
        sheet.removeAllViews()
        holders.clear(); vsliders.clear()
        sheet.orientation = LinearLayout.VERTICAL
        sheet.isClickable = true
        if (sk.sheetAlpha > 0) {
            val r = dp(sk.sheetRadius).toFloat()
            sheet.background = GradientDrawable().apply { setColor(cSheet); cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r) }
        } else sheet.background = null
        sheet.setPadding(0, statusBarH() + dp(4), 0, 0)

        val padH = dp(12)
        val innerW = sheetW - padH * 2
        u = innerW / 4

        sheet.addView(buildTabBar(), LinearLayout.LayoutParams(MP, WC))

        // ---- pestaña 1: notificaciones (con los accesos que se expanden y empujan) ----
        page0 = vcol()
        page0.setPadding(padH, dp(4), padH, dp(6))
        page0.addView(buildHeader(), LinearLayout.LayoutParams(MP, WC))
        qsCompact = buildQsCompact(innerW)
        val fullContent = grid(shown(), innerW)
        hC = natH(qsCompact, innerW)
        hF = minOf(natH(fullContent, innerW), (dm.heightPixels * 0.5f).toInt()).coerceAtLeast(hC)
        qsFull = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER }
        qsFull.addView(fullContent)
        qs = FrameLayout(ctx)
        qs.addView(qsCompact, FrameLayout.LayoutParams(innerW, hC))
        qs.addView(qsFull, FrameLayout.LayoutParams(innerW, hF))
        page0.addView(qs, LinearLayout.LayoutParams(innerW, hC))

        notifList.free()
        notifList.orientation = LinearLayout.VERTICAL
        notifScroll = MaxScroll(ctx, (dm.heightPixels * 0.34f).toInt()).apply {
            isVerticalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER
            addView(notifList)
        }
        page0.addView(notifScroll, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(8) })
        clearBtn.setOnClickListener {
            MediaListener.instance?.cancelAllNotifications()
            postDelayed({ refreshNotes() }, 250)
        }
        clearBtn.text = "Borrar todo"; clearBtn.textSize = 13f
        clearBtn.setTextColor(cText); clearBtn.gravity = Gravity.CENTER
        clearBtn.setPadding(dp(16), dp(8), dp(16), dp(8))
        clearBtn.background = round(cCard, 20)
        addClearTo(page0)

        // ---- pestaña 2: controles (multimedia, brillo, volumen) ----
        page1 = vcol()
        page1.setPadding(padH, dp(8), padH, dp(8))
        if (sk.sliderStyle == 0) {
            page1.addView(sliderRow(R.drawable.ic_brightness, 255,
                { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) }) { v -> setBrightness(v) })
            val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            page1.addView(sliderRow(R.drawable.ic_volume, maxVol, { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }) { v ->
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
            })
            if (sk.showMedia) page1.addView(mediaCard(), LinearLayout.LayoutParams(MP, dp(130)).apply { topMargin = dp(10) })
        } else {
            val hh = dp(sk.sliderThick) * 3
            val mg = dp(5)
            val r = hrow()
            if (sk.showMedia) r.addView(mediaCard(), LinearLayout.LayoutParams(0, hh, 2f).apply { setMargins(mg, mg, mg, mg) })
            r.addView(vBright(), LinearLayout.LayoutParams(0, hh, 1f).apply { setMargins(mg, mg, mg, mg) })
            r.addView(vVol(), LinearLayout.LayoutParams(0, hh, 1f).apply { setMargins(mg, mg, mg, mg) })
            page1.addView(r)
        }

        pager = FrameLayout(ctx)
        pager.addView(page0, FrameLayout.LayoutParams(sheetW, WC))
        pager.addView(page1, FrameLayout.LayoutParams(sheetW, WC))
        sheet.addView(pager, LinearLayout.LayoutParams(sheetW, WC))
        sheet.addView(grabBar(withA(cText, 35)), LinearLayout.LayoutParams(MP, dp(28)))
        applyTab()
        refreshNotes()
    }

    // ---- notificaciones: agrupadas por app y desplegables ----
    private fun noteRow(n: MediaListener.Note): View {
        val open = n.key in expandedKeys
        val expandable = n.text.length > 70 || n.actions.isNotEmpty()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = round(cCard, sk.cardR)
            isClickable = true
        }
        val useApp = sk.notifApp && n.appIcon != null
        val ic = ImageView(ctx)
        if (useApp) {
            ic.setImageDrawable(n.appIcon)
            ic.background = round(0x00000000, 8); ic.clipToOutline = true
        } else {
            ic.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (n.color != 0) n.color else cAccent) }
            ic.setPadding(dp(7), dp(7), dp(7), dp(7))
            n.icon?.let { ic.setImageDrawable(it); ic.imageTintList = tint(0xFFFFFFFF.toInt()) }
        }
        row.addView(ic, LinearLayout.LayoutParams(dp(36), dp(36)).apply { topMargin = dp(2) })
        val col = vcol().apply { setPadding(dp(10), 0, 0, 0) }
        val head = hrow().apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(ctx).apply { text = n.app; textSize = 11f; setTextColor(cSub); maxLines = 1 }, LinearLayout.LayoutParams(0, WC, 1f))
        head.addView(TextView(ctx).apply {
            text = SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(n.time)); textSize = 11f; setTextColor(cSub)
        })
        if (expandable) head.addView(TextView(ctx).apply {
            text = if (open) "▴" else "▾"; textSize = 14f; setTextColor(cText); setPadding(dp(10), dp(2), dp(4), dp(2))
            setOnClickListener { if (open) expandedKeys.remove(n.key) else expandedKeys.add(n.key); post { refreshNotes() } }
        })
        col.addView(head)
        if (n.title.isNotEmpty()) col.addView(TextView(ctx).apply {
            text = n.title; textSize = 14f; setTextColor(cText); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; setTypeface(null, Typeface.BOLD)
        })
        if (n.text.isNotEmpty()) col.addView(TextView(ctx).apply {
            text = n.text; textSize = 13f; setTextColor(cText); maxLines = if (open) 8 else 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        if (open && n.actions.isNotEmpty()) {
            val acts = hrow().apply { setPadding(0, dp(8), 0, 0) }
            n.actions.take(3).forEach { a ->
                acts.addView(pillButton(a.title, cText, withA(cText, 16)) {
                    try { a.intent?.send() } catch (e: Exception) {}
                    onClose()
                }, LinearLayout.LayoutParams(WC, WC).apply { rightMargin = dp(8) })
            }
            col.addView(acts)
        }
        row.addView(col, LinearLayout.LayoutParams(0, WC, 1f))

        var x0 = 0f
        var y0 = 0f
        var moved = false
        row.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x0 = e.rawX; y0 = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - x0
                    if (!moved && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(e.rawY - y0)) {
                        moved = true; v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (moved) {
                        v.translationX = if (n.clearable) dx else dx * 0.25f
                        v.alpha = 1f - (Math.abs(v.translationX) / v.width).coerceAtMost(1f) * 0.7f
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = e.rawX - x0
                    if (moved) {
                        if (n.clearable && Math.abs(dx) > v.width * 0.4f) {
                            val out = if (dx > 0) v.width.toFloat() else -v.width.toFloat()
                            v.animate().translationX(out).alpha(0f).setDuration(120).withEndAction {
                                MediaListener.instance?.cancelNotification(n.key)
                                post { refreshNotes() }
                            }.start()
                        } else v.animate().translationX(0f).alpha(1f).setDuration(120).start()
                    } else if (Math.abs(dx) < slop && Math.abs(e.rawY - y0) < slop) openNote(n)
                    true
                }
                MotionEvent.ACTION_CANCEL -> { v.animate().translationX(0f).alpha(1f).setDuration(120).start(); true }
                else -> true
            }
        }
        return row
    }

    private fun openNote(n: MediaListener.Note) {
        try { n.intent?.send() } catch (e: Exception) {
            Toast.makeText(ctx, "No se pudo abrir la notificación", Toast.LENGTH_SHORT).show()
        }
        if (n.autoCancel && n.clearable) MediaListener.instance?.cancelNotification(n.key)
        onClose()
    }

    private fun refreshNotes() {
        if (!::notifScroll.isInitialized) return
        val l = MediaListener.instance
        val notes = l?.notes() ?: emptyList()
        notifList.removeAllViews()
        if (notes.isEmpty()) {
            notifList.addView(TextView(ctx).apply {
                text = if (l == null) "Acceso a notificaciones no activo" else "Sin notificaciones"
                textSize = 13f; gravity = Gravity.CENTER; setTextColor(cSub)
                setPadding(0, dp(16), 0, dp(8))
            }, LinearLayout.LayoutParams(MP, WC))
        } else {
            val groups = LinkedHashMap<String, MutableList<MediaListener.Note>>()
            notes.forEach { groups.getOrPut(it.pkg) { mutableListOf() }.add(it) }
            groups.forEach { (pkg, list) ->
                val lpRow = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6) }
                if (list.size == 1) { notifList.addView(noteRow(list[0]), lpRow); return@forEach }
                val open = pkg in expandedGroups
                val box = vcol()
                (if (open) list else list.take(1)).forEach {
                    box.addView(noteRow(it), LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(4) })
                }
                box.addView(TextView(ctx).apply {
                    text = if (open) "▴ Mostrar menos" else "▾ " + (list.size - 1) + " más de " + list[0].app
                    textSize = 12f; setTextColor(cSub); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(6))
                    setOnClickListener { if (open) expandedGroups.remove(pkg) else expandedGroups.add(pkg); refreshNotes() }
                })
                notifList.addView(box, lpRow)
            }
        }
        clearBtn.visibility = if (notes.any { it.clearable }) View.VISIBLE else View.GONE
    }

    private fun grabBar(color: Int): View {
        val g = FrameLayout(ctx).apply { setOnClickListener { onClose() } }
        g.addView(View(ctx).apply { background = round(color, 3) }, FrameLayout.LayoutParams(dp(48), dp(5), Gravity.CENTER))
        return g
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try { cam.registerTorchCallback(torchCb, null) } catch (e: Exception) {}
        MediaListener.onChange = { post { refreshNotes() } }
        refreshNotes()
    }

    override fun onDetachedFromWindow() {
        try { cam.unregisterTorchCallback(torchCb) } catch (e: Exception) {}
        MediaListener.onChange = null
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}
