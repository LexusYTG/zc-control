package com.lexus2026.cdc

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout

class ControlService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private var handle: View? = null
    private var panel: PanelView? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var pillView: View? = null

    /** Paneles que ya se mandaron a cerrar pero siguen en pantalla hasta terminar la animación. */
    private val closing = mutableSetOf<PanelView>()
    private var receiverOn = false

    // Fondo difuminado: captura reducida y desenfocada de lo que hay detrás del panel.
    private var backdrop: Bitmap? = null
    private var backdropFailed = false
    private var capturing = false

    companion object {
        @Volatile var instance: ControlService? = null
    }

    fun global(action: Int) { performGlobalAction(action) }

    /** Zona de apertura elegida en la app: left, center, right o full. */
    private fun zone() = getSharedPreferences("cc", MODE_PRIVATE).getString("zone", "full") ?: "full"

    /** Opacidad de la barrita (0 = invisible); la zona táctil sigue activa. */
    fun applyPillAlpha() {
        val a = getSharedPreferences("cc", MODE_PRIVATE).getInt("pill_alpha", 60)
        pillView?.alpha = a / 100f
        pillView?.visibility = if (a == 0) View.INVISIBLE else View.VISIBLE
    }

    // ---------------- guardián de pantalla bloqueada ----------------

    private fun locked(): Boolean = try {
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
    } catch (e: Exception) { false }

    private fun screenOff(): Boolean = try {
        !(getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    } catch (e: Exception) { false }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                // Al bloquear: se saca TODO (panel, ventanas en cierre y la zona táctil). Nada queda vivo sobre el bloqueo.
                Intent.ACTION_SCREEN_OFF -> { forceCloseAll(); removeHandle() }
                // Al desbloquear: se vuelve a poner la zona táctil, limpia.
                Intent.ACTION_USER_PRESENT -> { forceCloseAll(); rebuildHandle() }
                // Pantalla encendida sin bloqueo (sin PIN, por ejemplo): también se restaura.
                Intent.ACTION_SCREEN_ON -> { forceCloseAll(); if (!locked()) rebuildHandle() }
            }
        }
    }

    private fun registerGuard() {
        if (receiverOn) return
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenReceiver, f)
            receiverOn = true
        } catch (e: Exception) {}
    }

    /** Si el panel quedó abierto sin contenido (o con la pantalla apagada), se cierra solo. */
    private val watchdog = object : Runnable {
        override fun run() {
            val p = panel
            if (p != null && (screenOff() || p.isStuck())) forceCloseAll()
            if (panel != null || closing.isNotEmpty()) ui.postDelayed(this, 1000)
        }
    }

    private fun startWatchdog() { ui.removeCallbacks(watchdog); ui.postDelayed(watchdog, 1000) }

    /** Saca ya mismo, sin animación, todas las ventanas de panel que haya. Es idempotente. */
    fun forceCloseAll() {
        val p = panel
        panel = null; panelLp = null
        if (p != null) { p.stopAnim(); removeNow(p) }
        closing.toList().forEach { it.stopAnim(); removeNow(it) }
        closing.clear()
    }

    private fun removeNow(p: PanelView) {
        closing.remove(p)
        try { wm.removeViewImmediate(p) } catch (e: Exception) { /* ya no estaba */ }
    }

    /** La ventana tiene que llegar hasta el borde real: debajo de la barra de estado y de la muesca de la cámara. */
    private fun noInsets(lp: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            lp.setFitInsetsTypes(0)
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    // ---------------- zona táctil ----------------

    private fun removeHandle() {
        try { handle?.let { wm.removeViewImmediate(it) } } catch (e: Exception) {}
        handle = null; pillView = null
    }

    /** Vuelve a crear la zona táctil (al cambiar la opción o al girar la pantalla). */
    fun rebuildHandle() = ui.post {
        removeHandle()
        if (!locked() && !screenOff()) addHandle()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::wm.isInitialized) rebuildHandle()
    }

    /** Abre el panel en la vista acotada (lo usa también el botón "Probar" de la app). */
    fun open() = ui.post {
        if (panel == null) { capture(); showPanel(false) }
    }

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        registerGuard()
        if (!locked() && !screenOff()) addHandle()
        reapplyLock(0)
    }

    /** Si el bloqueo del panel del sistema estaba activo, lo reaplica (Shizuku puede tardar en arrancar). */
    private fun reapplyLock(attempt: Int) {
        if (!getSharedPreferences("cc", MODE_PRIVATE).getBoolean("lock", false)) return
        if (Privileged.available()) Privileged.run("cmd statusbar send-disable-flag statusbar-expansion")
        else if (attempt < 8) ui.postDelayed({ reapplyLock(attempt + 1) }, 15000)
    }

    private fun addHandle() {
        if (handle != null) return
        val d = resources.displayMetrics
        val dp = d.density
        val v = FrameLayout(this)
        v.setBackgroundColor(0x01000000)
        // Barrita visible para saber dónde deslizar
        val pill = View(this).apply {
            background = GradientDrawable().apply { setColor(0xE6FFFFFF.toInt()); cornerRadius = 4 * dp }
        }
        pillView = pill
        v.addView(pill, FrameLayout.LayoutParams(
            (Skin.get(this, "pillW") * dp).toInt(), (Skin.get(this, "pillH") * dp).toInt(), Gravity.CENTER_HORIZONTAL or Gravity.TOP
        ).apply { topMargin = (3 * dp).toInt() })

        // El panel sigue al dedo: se crea apenas empieza el arrastre y se acomoda al soltar.
        var y0 = 0f
        var dragging = false
        var vt: VelocityTracker? = null
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    y0 = e.rawY; dragging = false
                    vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
                    if (panel == null) capture()
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e)
                    val dy = e.rawY - y0
                    if (!dragging && dy > 12 * dp) { dragging = true; showPanel(true) }
                    if (dragging) panel?.dragFromHandle(dy)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        vt?.computeCurrentVelocity(1000)
                        val vy = if (e.actionMasked == MotionEvent.ACTION_UP) (vt?.yVelocity ?: 0f) else 0f
                        panel?.release(vy)
                    }
                    dragging = false
                    vt?.recycle(); vt = null
                }
            }
            true
        }
        val z = zone()
        val hw = when (z) { "full" -> d.widthPixels; "center" -> d.widthPixels / 3; else -> d.widthPixels / 2 }
        val lp = WindowManager.LayoutParams(
            hw, (28 * dp).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = when (z) {
            "left", "full" -> Gravity.TOP or Gravity.START
            "center" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            else -> Gravity.TOP or Gravity.END
        }
        noInsets(lp)
        try {
            wm.addView(v, lp)
            handle = v
            applyPillAlpha()
        } catch (e: Exception) { handle = null; pillView = null }
    }

    // ---------------- fondo difuminado ----------------

    private fun capture() {
        backdrop = null; backdropFailed = false
        // Solo el modo "Difuminado" necesita captura; los demás fondos no gastan nada.
        if (Skin(this).bgMode != BgMode.BLUR) return
        if (Build.VERSION.SDK_INT < 30 || capturing) { deliver(null); return }
        capturing = true
        ui.postDelayed({ capturing = false }, 3000)
        val radius = Skin.get(this, "blurR")
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                    val hb = r.hardwareBuffer
                    val cs = r.colorSpace
                    Thread {
                        var out: Bitmap? = null
                        try {
                            val hw = Bitmap.wrapHardwareBuffer(hb, cs)
                            val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            hw?.recycle()
                            if (soft != null) {
                                val w = 96
                                val h = (soft.height * w / soft.width).coerceAtLeast(1)
                                val small = Bitmap.createScaledBitmap(soft, w, h, true)
                                if (small !== soft) soft.recycle()
                                out = Blur.apply(small, radius, 3)
                            }
                        } catch (e: Throwable) { /* sin fondo difuminado */ }
                        try { hb.close() } catch (e: Throwable) {}
                        ui.post { capturing = false; deliver(out) }
                    }.start()
                }
                override fun onFailure(errorCode: Int) { capturing = false; deliver(null) }
            })
        } catch (e: Throwable) { capturing = false; deliver(null) }
    }

    private fun deliver(b: Bitmap?) {
        backdrop = b
        backdropFailed = b == null
        panel?.let { applyBackdrop(it) }
    }

    private fun applyBackdrop(p: PanelView) {
        if (Skin(this).bgMode != BgMode.BLUR) return
        val b = backdrop
        if (b != null) p.setBackdrop(b)
        else if (backdropFailed && !enableWinBlur(p)) p.noBlur()
    }

    /** Plan B: desenfoque del compositor (Android 12+), si el dispositivo lo permite. */
    private fun enableWinBlur(p: PanelView): Boolean {
        val lp = panelLp ?: return false
        if (Build.VERSION.SDK_INT < 31 || !wm.isCrossWindowBlurEnabled) return false
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        p.blurHook = { r ->
            try { lp.blurBehindRadius = r; wm.updateViewLayout(p, lp) } catch (e: Exception) {}
        }
        p.useWindowBlur()
        return true
    }

    // ---------------- panel ----------------

    private fun showPanel(interactive: Boolean) {
        if (panel != null) return
        val p = PanelView(this) { hidePanel() }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0; lp.y = 0
        noInsets(lp)   // sin esto quedaba una franja arriba (muesca / barra de estado) donde no llegaba el fondo
        try { wm.addView(p, lp) } catch (e: Exception) { return }
        panel = p; panelLp = lp
        applyBackdrop(p)
        if (!interactive) p.animateTo(1f)
        startWatchdog()
    }

    /**
     * Cierra con animación, pero sin depender de ella: la ventana en cierre no recibe toques (NOT_TOUCHABLE),
     * el panel ignora cualquier toque que llegue y un temporizador la saca pase lo que pase.
     * Antes, un toque durante la animación la cancelaba y la ventana quedaba huérfana a pantalla completa.
     */
    private fun hidePanel() {
        val p = panel ?: return
        val lp = panelLp
        panel = null; panelLp = null
        closing.add(p)
        p.beginClose()
        if (lp != null) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try { wm.updateViewLayout(p, lp) } catch (e: Exception) {}
        }
        p.animateTo(0f) { removeNow(p) }
        ui.postDelayed({ removeNow(p) }, 600)
        startWatchdog()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Autocuración: si por algún motivo la zona táctil no está y el dispositivo está en uso, se repone.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            ::wm.isInitialized && handle == null && !locked() && !screenOff()) addHandle()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        ui.removeCallbacks(watchdog)
        if (receiverOn) { try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}; receiverOn = false }
        if (::wm.isInitialized) { forceCloseAll(); removeHandle() }
        super.onDestroy()
    }
}
