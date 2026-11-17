package com.lexus2026.cdc;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Subsistema de interfaz lateral de ZC Control.
 *
 * Es el ÚNICO dueño de: accesos directos, lanzamiento de apps / ventanas, notificaciones animadas,
 * panel y animaciones. La burbuja (modo burbuja) y la zona del borde (modo gestos) son solo dos formas
 * distintas de invocar este mismo panel. Vive dentro de {@link ControlService}, que le presta el
 * WindowManager de accesibilidad y le avisa del ciclo de vida de la pantalla.
 */
public final class SideHub implements SidePanel.Host {
    private static final Set<String> LIVE_KEYS = new HashSet<>(Arrays.asList(
        "mode", "bubbleSize", "bubbleAlpha", "posX", "posY", "edgeW", "edgeH"));

    private static final int ST_IDLE = 0;
    private static final int ST_RISING = 1;
    private static final int ST_SHOWN = 2;
    private static final int ST_RETRACT = 3;

    private final ControlService svc;
    private final WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float d;

    private boolean started = false;
    private boolean suspended = false;

    // modo burbuja
    private SideBubbleView bubble = null;
    private WindowManager.LayoutParams bubbleLp = null;
    private ValueAnimator ringAnim = null;
    private Runnable holdFire = null;

    // modo gestos
    private View edge = null;
    private int edgeY = -1;

    // panel
    private SidePanel panel = null;
    private WindowManager.LayoutParams panelLp = null;

    // lienzo (mover la burbuja)
    private FrameLayout canvas = null;

    // notificación animada
    private int nState = ST_IDLE;
    private FrameLayout stage = null;
    private WindowManager.LayoutParams stageLp = null;
    private FrameLayout morph = null;
    private GradientDrawable morphBg = null;
    private SideBubbleView mBubble = null;
    private SideNoticeCard mCard = null;
    private MediaListener.Note current = null;
    private MediaListener.Note pending = null;
    private ValueAnimator nAnim = null;
    private boolean nEdgeMode = false;
    private int nS, nSw, nOx, nOy, nTopY, nCardW, nCardH;
    private final Runnable retractR = this::retractNotice;

    private final Runnable refreshR = this::rebuildAll;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefL = (sp, key) -> {
        if (key == null || LIVE_KEYS.contains(key)) refresh();
    };

    public SideHub(ControlService svc, WindowManager wm) {
        this.svc = svc;
        this.wm = wm;
        this.d = svc.getResources().getDisplayMetrics().density;
    }

    private int dp(int v) { return (int) (v * d); }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    // =====================================================================
    // ciclo de vida
    // =====================================================================

    public void start() {
        if (started) return;
        started = true;
        SideConfig.prefs(svc).registerOnSharedPreferenceChangeListener(prefL);
        refresh();
    }

    public void stop() {
        started = false;
        try { SideConfig.prefs(svc).unregisterOnSharedPreferenceChangeListener(prefL); } catch (Exception e) {}
        ui.removeCallbacks(refreshR);
        teardown();
    }

    /** Pantalla apagada o bloqueada: no queda nada vivo encima del bloqueo. */
    public void suspend() {
        suspended = true;
        ui.removeCallbacks(refreshR);
        teardown();
    }

    public void resume() {
        suspended = false;
        refresh();
    }

    /** Vuelve a armar todo según la configuración (varios cambios seguidos se juntan en uno). */
    public void refresh() {
        ui.removeCallbacks(refreshR);
        ui.postDelayed(refreshR, 80);
    }

    private int effectiveMode() {
        int m = SideConfig.getMode(svc);
        if (m == SideConfig.MODE_GESTURE && !SideConfig.threeButtonNav(svc)) return SideConfig.MODE_OFF;
        return m;
    }

    private void rebuildAll() {
        teardown();
        if (!started || suspended || wm == null) return;
        int m = effectiveMode();
        if (m == SideConfig.MODE_BUBBLE) addBubble();
        else if (m == SideConfig.MODE_GESTURE) addEdge();
    }

    private void teardown() {
        cancelNotice();
        closePanelNow();
        removeCanvas();
        cancelHold();
        removeView(bubble); bubble = null; bubbleLp = null;
        removeView(edge); edge = null;
    }

    private void removeView(View v) {
        if (v == null) return;
        try { wm.removeViewImmediate(v); } catch (Exception e) { /* ya no estaba */ }
    }

    // =====================================================================
    // utilidades de pantalla
    // =====================================================================

    private int[] screen() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Rect b = wm.getCurrentWindowMetrics().getBounds();
                return new int[]{b.width(), b.height()};
            } catch (Throwable ignored) { /* cae al método viejo */ }
        }
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        return new int[]{m.widthPixels, m.heightPixels};
    }

    private int topInset() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                WindowInsets wi = wm.getCurrentWindowMetrics().getWindowInsets();
                return wi.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
            } catch (Throwable ignored) { /* valor por defecto */ }
        }
        return dp(28);
    }

    private static void noInsets(WindowManager.LayoutParams lp) {
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.setFitInsetsTypes(0);
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private WindowManager.LayoutParams overlay(int w, int h, boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        noInsets(lp);
        return lp;
    }

    private int bubbleSizePx() { return dp(SideConfig.get(svc, "bubbleSize")); }

    /** Esquina superior izquierda de la burbuja en pantalla (px). */
    private int[] bubbleOrigin() {
        int[] s = screen();
        int size = bubbleSizePx();
        int px = SideConfig.get(svc, "posX");
        int py = SideConfig.get(svc, "posY");
        int x = px < 0 ? s[0] - size : Math.max(0, Math.min(s[0] - size, px * s[0] / 1000 - size / 2));
        int y = Math.max(dp(24), Math.min(s[1] - size - dp(24), py * s[1] / 1000 - size / 2));
        return new int[]{x, y};
    }

    // =====================================================================
    // modo burbuja
    // =====================================================================

    private void addBubble() {
        if (bubble != null) return;
        int size = bubbleSizePx();
        int[] o = bubbleOrigin();
        final SideBubbleView b = new SideBubbleView(svc);
        b.setAlpha(SideConfig.get(svc, "bubbleAlpha") / 100f);
        b.setOnTouchListener(new BubbleTouch());
        WindowManager.LayoutParams lp = overlay(size, size, true);
        lp.x = o[0];
        lp.y = o[1];
        try {
            wm.addView(b, lp);
            bubble = b;
            bubbleLp = lp;
        } catch (Exception e) { bubble = null; bubbleLp = null; }
    }

    private void cancelHold() {
        if (holdFire != null) { ui.removeCallbacks(holdFire); holdFire = null; }
        if (ringAnim != null) { ringAnim.cancel(); ringAnim = null; }
        if (bubble != null) bubble.setHold(0f);
    }

    /** Toque corto: abre el panel. Mantener apretado (10 s por defecto): modo lienzo para mover la burbuja. */
    private final class BubbleTouch implements View.OnTouchListener {
        float x0, y0;
        long t0;
        boolean fired, moved;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = e.getRawX(); y0 = e.getRawY(); t0 = e.getEventTime();
                    fired = false; moved = false;
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
                    startHold(v);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved && Math.hypot(e.getRawX() - x0, e.getRawY() - y0) > dp(18)) {
                        moved = true;
                        cancelHold();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelHold();
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && !fired && !moved && e.getEventTime() - t0 < 600) togglePanel();
                    return true;
                default:
                    return true;
            }
        }

        private void startHold(final View v) {
            cancelHold();
            final long holdMs = Math.max(1500L, SideConfig.get(svc, "holdSecs") * 1000L);
            final SideBubbleView bv = bubble;
            if (bv != null) {
                // el anillo empieza a llenarse al cabo de 1 s para que el toque normal no lo muestre
                ValueAnimator ra = ValueAnimator.ofFloat(0f, 1f);
                ra.setStartDelay(1000L);
                ra.setDuration(Math.max(300L, holdMs - 1000L));
                ra.setInterpolator(t -> t);
                ra.addUpdateListener(a -> bv.setHold((Float) a.getAnimatedValue()));
                ringAnim = ra;
                ra.start();
            }
            holdFire = () -> {
                holdFire = null;
                fired = true;
                cancelHold();
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                enterCanvas();
            };
            ui.postDelayed(holdFire, holdMs);
        }
    }

    // =====================================================================
    // modo gestos (borde derecho, solo con navegación de tres botones)
    // =====================================================================

    private float travel() { return 246f * d; }

    private void addEdge() {
        if (edge != null) return;
        int[] s = screen();
        int w = dp(SideConfig.get(svc, "edgeW"));
        int h = s[1] * SideConfig.get(svc, "edgeH") / 100;
        View v = new View(svc);
        v.setBackgroundColor(0x01000000);
        v.setOnTouchListener(new EdgeTouch());
        WindowManager.LayoutParams lp = overlay(w, h, true);
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        try {
            wm.addView(v, lp);
            edge = v;
        } catch (Exception e) { edge = null; }
    }

    /** Desliza desde el borde derecho hacia la izquierda: el panel sigue al dedo y se asienta al soltar. */
    private final class EdgeTouch implements View.OnTouchListener {
        float x0, y0;
        boolean dragging;
        VelocityTracker vt = null;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = e.getRawX(); y0 = e.getRawY(); dragging = false;
                    if (vt != null) vt.recycle();
                    vt = VelocityTracker.obtain();
                    vt.addMovement(e);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (vt != null) vt.addMovement(e);
                    float dx = x0 - e.getRawX();
                    float dy = e.getRawY() - y0;
                    if (!dragging && dx > dp(10) && dx > Math.abs(dy)) {
                        dragging = true;
                        edgeY = (int) y0;
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        openPanel(true, screen()[0], (int) y0, false);
                    }
                    if (dragging && panel != null) panel.dragTo(dx / travel());
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (dragging && panel != null) {
                        float vx = 0f;
                        if (vt != null && e.getActionMasked() == MotionEvent.ACTION_UP) {
                            vt.addMovement(e);
                            vt.computeCurrentVelocity(1000);
                            vx = vt.getXVelocity();   // negativo = hacia la izquierda
                        }
                        float p = panel.progress();
                        boolean open = e.getActionMasked() == MotionEvent.ACTION_UP && (vx < -700f || (p > 0.4f && vx < 700f));
                        if (open) panel.animateTo(1f, null);
                        else closePanel();
                    }
                    dragging = false;
                    if (vt != null) { vt.recycle(); vt = null; }
                    return true;
                }
                default:
                    return true;
            }
        }
    }

    // =====================================================================
    // panel (compartido)
    // =====================================================================

    /** Abre el panel desde donde esté la interfaz (lo usa también el botón "Probar" de la app). */
    public void open() {
        ui.post(() -> {
            int m = effectiveMode();
            int[] s = screen();
            if (m == SideConfig.MODE_BUBBLE) togglePanelOpen();
            else if (m == SideConfig.MODE_GESTURE) openPanel(true, s[0], s[1] / 2, true);
        });
    }

    private void togglePanel() {
        if (panel != null) closePanel(); else togglePanelOpen();
    }

    private void togglePanelOpen() {
        if (panel != null || canvas != null) return;
        cancelNotice();
        int size = bubbleSizePx();
        int[] o = bubbleOrigin();
        openPanel(false, o[0] + size / 2, o[1] + size / 2, true);
    }

    private void openPanel(boolean edgeMode, int cx, int cy, boolean animate) {
        if (panel != null || wm == null) return;
        cancelNotice();
        SidePanel p = new SidePanel(svc, this);
        p.prepare(edgeMode, cx, cy, bubbleSizePx());
        WindowManager.LayoutParams lp = overlay(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true);
        try { wm.addView(p, lp); } catch (Exception e) { return; }
        panel = p;
        panelLp = lp;
        if (animate) p.animateTo(1f, null);
    }

    /** Cierra con animación; la ventana en cierre ya no recibe toques y un temporizador la saca pase lo que pase. */
    @Override
    public void closePanel() {
        final SidePanel p = panel;
        if (p == null) return;
        WindowManager.LayoutParams lp = panelLp;
        panel = null;
        panelLp = null;
        if (lp != null) {
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { wm.updateViewLayout(p, lp); } catch (Exception e) {}
        }
        p.animateTo(0f, () -> removeView(p));
        ui.postDelayed(() -> removeView(p), 700);
    }

    private void closePanelNow() {
        SidePanel p = panel;
        panel = null;
        panelLp = null;
        if (p != null) { p.stopAnim(); removeView(p); }
    }

    @Override
    public void launch(String pkg) {
        boolean ok = SideLauncher.launch(svc, pkg);
        if (!ok) Toast.makeText(svc, "No se pudo abrir la app", Toast.LENGTH_SHORT).show();
        closePanel();
    }

    @Override
    public void recents() {
        closePanel();
        ui.postDelayed(() -> svc.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS), 250);
    }

    @Override
    public void openSettings() {
        closePanel();
        Intent it = new Intent(svc, SettingsPageActivity.class);
        it.putExtra(SettingsPageActivity.EXTRA_PATH, "personalizacion/lateral");
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { svc.startActivity(it); } catch (Exception e) {}
    }

    // =====================================================================
    // modo lienzo: mover la burbuja a donde se quiera
    // =====================================================================

    public void enterCanvas() {
        ui.post(this::enterCanvasNow);
    }

    private void enterCanvasNow() {
        if (canvas != null || wm == null || suspended || effectiveMode() != SideConfig.MODE_BUBBLE) return;
        cancelNotice();
        closePanelNow();
        final int[] s = screen();
        final int size = bubbleSizePx();
        final int[] o = bubbleOrigin();
        final int minY = topInset();
        if (bubble != null) bubble.setVisibility(View.INVISIBLE);

        final FrameLayout root = new FrameLayout(svc);
        root.setBackgroundColor(0x88000000);

        // cartel con las instrucciones y los dos botones
        LinearLayout hint = new LinearLayout(svc);
        hint.setOrientation(LinearLayout.VERTICAL);
        hint.setPadding(dp(20), dp(16), dp(20), dp(14));
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0xF01B1B22);
        hb.setCornerRadius(dp(22));
        hb.setStroke(dp(1), 0x33FFFFFF);
        hint.setBackground(hb);
        TextView t1 = new TextView(svc);
        t1.setText("Modo lienzo");
        t1.setTextSize(18f);
        t1.setTextColor(0xFFEDE7F6);
        t1.setTypeface(null, android.graphics.Typeface.BOLD);
        hint.addView(t1);
        TextView t2 = new TextView(svc);
        t2.setText("Arrastrá la burbuja hasta donde quieras y tocá «Listo» para dejarla ahí.");
        t2.setTextSize(13f);
        t2.setTextColor(0xFFB0A9B5);
        t2.setPadding(0, dp(6), 0, dp(12));
        hint.addView(t2);
        LinearLayout btns = new LinearLayout(svc);
        btns.setGravity(Gravity.END);
        TextView reset = chip("Borde derecho", 0xFF2A2833);
        TextView done = chip("Listo", 0xFF5B4B9A);
        btns.addView(reset);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.leftMargin = dp(10);
        btns.addView(done, dl);
        hint.addView(btns);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(Math.min(dp(320), s[0] - dp(32)), -2, Gravity.CENTER);
        root.addView(hint, hlp);

        // la burbuja que se arrastra
        final SideBubbleView cb = new SideBubbleView(svc);
        cb.setEditing(true);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START);
        root.addView(cb, blp);
        cb.setTranslationX(o[0]);
        cb.setTranslationY(o[1]);
        final ValueAnimator pulse = ValueAnimator.ofFloat(1f, 1.1f);
        pulse.setDuration(700);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(a -> { float v = (Float) a.getAnimatedValue(); cb.setScaleX(v); cb.setScaleY(v); });
        pulse.start();
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { pulse.cancel(); }
        });

        cb.setOnTouchListener(new View.OnTouchListener() {
            float offX, offY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        offX = e.getRawX() - v.getTranslationX();
                        offY = e.getRawY() - v.getTranslationY();
                        pulse.cancel();
                        v.setScaleX(1.15f); v.setScaleY(1.15f);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float nx = Math.max(0, Math.min(s[0] - size, e.getRawX() - offX));
                        float ny = Math.max(minY, Math.min(s[1] - size - dp(8), e.getRawY() - offY));
                        v.setTranslationX(nx);
                        v.setTranslationY(ny);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setScaleX(1f); v.setScaleY(1f);
                        return true;
                    default:
                        return true;
                }
            }
        });

        done.setOnClickListener(v -> {
            int cx = (int) (cb.getTranslationX() + size / 2f);
            int cy = (int) (cb.getTranslationY() + size / 2f);
            SideConfig.setPos(svc, cx * 1000 / s[0], cy * 1000 / s[1]);
            exitCanvas();
        });
        reset.setOnClickListener(v -> { SideConfig.resetPos(svc); exitCanvas(); });

        WindowManager.LayoutParams lp = overlay(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true);
        try {
            wm.addView(root, lp);
            canvas = root;
        } catch (Exception e) {
            if (bubble != null) bubble.setVisibility(View.VISIBLE);
        }
    }

    private TextView chip(String text, int color) {
        TextView t = new TextView(svc);
        t.setText(text);
        t.setTextSize(14f);
        t.setTextColor(0xFFEDE7F6);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(20));
        t.setBackground(g);
        return t;
    }

    private void exitCanvas() {
        removeCanvas();
        refresh();   // la burbuja reaparece en su nueva posición
    }

    private void removeCanvas() {
        View c = canvas;
        canvas = null;
        if (c != null) removeView(c);
        if (bubble != null) bubble.setVisibility(View.VISIBLE);
    }

    // =====================================================================
    // notificaciones: la burbuja sube, se abre en tarjeta, se retrae y vuelve
    // =====================================================================

    /** Llega una notificación nueva (ya filtrada por MediaListener). */
    public void onNotification(MediaListener.Note n) {
        if (!started || suspended || n == null) return;
        if (effectiveMode() == SideConfig.MODE_OFF || SideConfig.get(svc, "noteOn") == 0) return;
        if (panel != null || canvas != null) return;
        if (nState == ST_IDLE) {
            showNotice(n);
        } else if (nState == ST_SHOWN) {
            current = n;
            mCard.bind(n);
            ui.removeCallbacks(retractR);
            ui.postDelayed(retractR, noteMs());
        } else {
            pending = n;
        }
    }

    /** Notificación de prueba para el botón de la configuración. */
    public boolean test() {
        if (effectiveMode() == SideConfig.MODE_OFF) return false;
        ui.post(() -> {
            MediaListener.Note n = new MediaListener.Note("zc-test", svc.getPackageName(), "Mensajes",
                "Nuevo mensaje de Juan", "¿Te vemos más tarde?", 0, null, SideLauncher.icon(svc, svc.getPackageName()),
                null, true, false, System.currentTimeMillis(), new ArrayList<>());
            if (panel == null && canvas == null && nState == ST_IDLE) showNotice(n);
            else onNotification(n);
        });
        return true;
    }

    /** Copia independiente de un ícono (dos vistas no pueden compartir la misma Drawable: se pisan los límites). */
    private static Drawable copyOf(Drawable dr) {
        if (dr == null) return null;
        Drawable.ConstantState cs = dr.getConstantState();
        return cs != null ? cs.newDrawable().mutate() : null;
    }

    private long noteMs() { return Math.max(1500L, SideConfig.get(svc, "noteSecs") * 1000L); }

    private void showNotice(MediaListener.Note n) {
        if (wm == null || nState != ST_IDLE) return;
        final int[] sc = screen();
        nSw = sc[0];
        nS = bubbleSizePx();
        nEdgeMode = effectiveMode() == SideConfig.MODE_GESTURE;
        if (nEdgeMode) {
            int py = SideConfig.get(this.svc, "posY");
            int y = edgeY >= 0 ? edgeY - nS / 2 : py * sc[1] / 1000 - nS / 2;
            nOx = sc[0] - nS;
            nOy = Math.max(dp(24), Math.min(sc[1] - nS - dp(24), y));
        } else {
            int[] o = bubbleOrigin();
            nOx = o[0];
            nOy = o[1];
        }
        nTopY = topInset() + dp(6);
        nCardW = Math.min(sc[0] - dp(24), dp(360));
        current = n;

        // tarjeta (se mide para saber cuánto alto necesita)
        mCard = new SideNoticeCard(svc);
        mCard.bind(n);
        mCard.measure(View.MeasureSpec.makeMeasureSpec(nCardW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        nCardH = Math.max(mCard.getMeasuredHeight(), dp(64));

        FrameLayout st = new FrameLayout(svc);
        FrameLayout m = new FrameLayout(svc);
        m.setClipChildren(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2202030);
        bg.setCornerRadius(nS / 2f);
        bg.setStroke(dp(1), 0x44FFFFFF);
        m.setBackground(bg);
        morphBg = bg;
        mBubble = new SideBubbleView(svc);
        mBubble.setBare(true);
        mBubble.setGlyph(copyOf(n.appIcon));
        m.addView(mBubble, new FrameLayout.LayoutParams(nS, nS, Gravity.CENTER));
        mCard.setAlpha(0f);
        m.addView(mCard, new FrameLayout.LayoutParams(nCardW, nCardH, Gravity.TOP | Gravity.START));
        mCard.setOnTouchListener(new CardTouch());
        st.addView(m, new FrameLayout.LayoutParams(nS, nS, Gravity.TOP | Gravity.START));
        m.setPivotX(nS / 2f);
        m.setPivotY(nS / 2f);
        morph = m;
        m.setTranslationX(nEdgeMode ? nSw : nOx);
        m.setTranslationY(nOy);

        WindowManager.LayoutParams lp = overlay(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, false);
        try { wm.addView(st, lp); } catch (Exception e) { cleanupStage(); return; }
        stage = st;
        stageLp = lp;
        nState = ST_RISING;
        if (bubble != null) bubble.setVisibility(View.INVISIBLE);
        runIn();
    }

    /** Arriba: [sale del borde] → se desprende y sube → se abre en tarjeta. */
    private void runIn() {
        Runnable rise = () -> {
            long dur = 380L + (long) (260f * Math.abs(nOy - nTopY) / Math.max(1, screen()[1]));
            anim(0f, 1f, dur, new AccelerateDecelerateInterpolator(), this::applyRise, () -> {
                applyRise(1f);
                anim(0f, 1f, 380L, new OvershootInterpolator(0.7f), this::applyMorph, this::noticeShown);
            });
        };
        if (nEdgeMode) {
            anim(0f, 1f, 170L, new DecelerateInterpolator(), f -> morph.setTranslationX(lerp(nSw, nOx, f)), rise);
        } else {
            rise.run();
        }
    }

    /** e: 0 = en su sitio, 1 = arriba. Estira la burbuja en la subida para que se vea que "se despega". */
    private void applyRise(float e) {
        if (morph == null) return;
        morph.setTranslationY(lerp(nOy, nTopY, e));
        float k = (float) Math.sin(Math.PI * clamp01(e));
        morph.setScaleX(1f - 0.10f * k);
        morph.setScaleY(1f + 0.22f * k);
    }

    /** p: 0 = burbuja, 1 = tarjeta abierta (admite un poco de rebote). */
    private void applyMorph(float p) {
        if (morph == null) return;
        morph.setScaleX(1f);
        morph.setScaleY(1f);
        FrameLayout.LayoutParams mlp = (FrameLayout.LayoutParams) morph.getLayoutParams();
        mlp.width = Math.max(nS, (int) lerp(nS, nCardW, p));
        mlp.height = Math.max(nS, (int) lerp(nS, nCardH, p));
        morph.setLayoutParams(mlp);
        morph.setTranslationX(lerp(nOx, (nSw - nCardW) / 2f, p));
        morph.setTranslationY(nTopY);
        morphBg.setCornerRadius(lerp(nS / 2f, dp(24), clamp01(p)));
        mBubble.setGlyphAlpha(1f - clamp01(p * 2.2f));
        mCard.setAlpha(clamp01((p - 0.45f) * 2f));
    }

    private void noticeShown() {
        if (stage == null) return;
        applyMorph(1f);
        nState = ST_SHOWN;
        // la ventana se achica a la franja de la tarjeta y vuelve a recibir toques; el resto de la pantalla sigue libre
        stageLp.height = nTopY + nCardH + dp(12);
        stageLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { wm.updateViewLayout(stage, stageLp); } catch (Exception e) {}
        if (pending != null) {
            current = pending;
            mCard.bind(pending);
            pending = null;
        }
        ui.removeCallbacks(retractR);
        ui.postDelayed(retractR, noteMs());
    }

    /** Abajo: la tarjeta se recoge → la burbuja baja → vuelve a su lugar. */
    private void retractNotice() {
        if (nState != ST_SHOWN || stage == null) return;
        nState = ST_RETRACT;
        ui.removeCallbacks(retractR);
        stageLp.height = WindowManager.LayoutParams.MATCH_PARENT;
        stageLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { wm.updateViewLayout(stage, stageLp); } catch (Exception e) {}
        anim(1f, 0f, 300L, new AccelerateInterpolator(1.2f), this::applyMorph, () -> {
            applyMorph(0f);
            long dur = 380L + (long) (260f * Math.abs(nOy - nTopY) / Math.max(1, screen()[1]));
            anim(1f, 0f, dur, new AccelerateDecelerateInterpolator(), this::applyRise, () -> {
                applyRise(0f);
                if (nEdgeMode) anim(0f, 1f, 170L, new AccelerateInterpolator(), f -> morph.setTranslationX(lerp(nOx, nSw, f)), this::noticeDone);
                else noticeDone();
            });
        });
    }

    private void noticeDone() {
        cleanupStage();
        nState = ST_IDLE;
        if (bubble != null) {
            bubble.setVisibility(View.VISIBLE);
            bubble.setScaleX(0.8f);
            bubble.setScaleY(0.8f);
            bubble.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(2f)).start();
        }
        if (pending != null) {
            MediaListener.Note n = pending;
            pending = null;
            ui.postDelayed(() -> { if (nState == ST_IDLE && panel == null && canvas == null) showNotice(n); }, 250);
        }
    }

    private void cleanupStage() {
        View st = stage;
        stage = null;
        stageLp = null;
        morph = null;
        mBubble = null;
        mCard = null;
        morphBg = null;
        if (st != null) removeView(st);
    }

    /** Corta cualquier animación de notificación y deja todo limpio (la burbuja vuelve a verse). */
    private void cancelNotice() {
        ui.removeCallbacks(retractR);
        if (nAnim != null) { nAnim.cancel(); nAnim = null; }
        cleanupStage();
        nState = ST_IDLE;
        pending = null;
        current = null;
        if (bubble != null) bubble.setVisibility(canvas != null ? View.INVISIBLE : View.VISIBLE);
    }

    private void anim(float from, float to, long dur, TimeInterpolator ip, final Consumer<Float> frame, final Runnable end) {
        if (nAnim != null) nAnim.cancel();
        final ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(dur);
        a.setInterpolator(ip);
        a.addUpdateListener(v -> frame.accept((Float) v.getAnimatedValue()));
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override public void onAnimationCancel(Animator an) { cancelled = true; }

            @Override
            public void onAnimationEnd(Animator an) {
                if (nAnim == a) nAnim = null;
                if (!cancelled && end != null) end.run();
            }
        });
        nAnim = a;
        a.start();
    }

    /** Tocar la tarjeta abre la app de la notificación; deslizarla hacia arriba la descarta. */
    private final class CardTouch implements View.OnTouchListener {
        float x0, y0;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = e.getRawX(); y0 = e.getRawY();
                    ui.removeCallbacks(retractR);   // mientras se la toca, no se retrae
                    return true;
                case MotionEvent.ACTION_UP: {
                    float dy = e.getRawY() - y0;
                    float dx = e.getRawX() - x0;
                    if (dy < -dp(24) && Math.abs(dy) > Math.abs(dx)) retractNotice();
                    else if (Math.hypot(dx, dy) < dp(14)) openNoticeTarget();
                    else { ui.removeCallbacks(retractR); ui.postDelayed(retractR, 1500); }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(retractR);
                    ui.postDelayed(retractR, 1500);
                    return true;
                default:
                    return true;
            }
        }
    }

    private void openNoticeTarget() {
        MediaListener.Note n = current;
        if (n != null && !"zc-test".equals(n.key)) {
            boolean sent = MediaListener.open(svc, n);
            if (!sent) SideLauncher.launch(svc, n.pkg);
            if (n.autoCancel && n.clearable) MediaListener.dismiss(n);
        }
        retractNotice();
    }
}
