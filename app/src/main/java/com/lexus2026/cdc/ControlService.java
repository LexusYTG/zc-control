package com.lexus2026.cdc;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.graphics.ColorSpace;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class ControlService extends AccessibilityService {
    private WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private View handle = null;
    private PanelView panel = null;
    private PanelView warm = null;
    private WindowManager.LayoutParams panelLp = null;
    private View pillView = null;

    /** Paneles que ya se mandaron a cerrar pero siguen en pantalla hasta terminar la animación. */
    private final Set<PanelView> closing = new LinkedHashSet<>();
    private boolean receiverOn = false;

    // Fondo difuminado: captura reducida y desenfocada de lo que hay detrás del panel.

    public static volatile ControlService instance = null;

    public void global(int action) { performGlobalAction(action); }

    /** Zona de apertura elegida en la app: left, center, right o full. */
    private String zone() {
        String z = getSharedPreferences("cc", MODE_PRIVATE).getString("zone", "full");
        return z != null ? z : "full";
    }

    /** Opacidad de la barrita (0 = invisible); la zona táctil sigue activa. */
    public void applyPillAlpha() {
        int a = getSharedPreferences("cc", MODE_PRIVATE).getInt("pill_alpha", 60);
        if (pillView != null) {
            pillView.setAlpha(a / 100f);
            pillView.setVisibility(a == 0 ? View.INVISIBLE : View.VISIBLE);
        }
    }

    // ---------------- guardián de pantalla bloqueada ----------------

    private boolean locked() {
        try {
            return ((KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked();
        } catch (Exception e) { return false; }
    }

    private boolean screenOff() {
        try {
            return !((PowerManager) getSystemService(Context.POWER_SERVICE)).isInteractive();
        } catch (Exception e) { return false; }
    }

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String act = i.getAction();
            if (act == null) return;
            switch (act) {
                // Al bloquear: se saca TODO (panel, ventanas en cierre y la zona táctil). Nada queda vivo sobre el bloqueo.
                case Intent.ACTION_SCREEN_OFF: forceCloseAll(); removeHandle(); break;
                // Al desbloquear: se vuelve a poner la zona táctil, limpia.
                case Intent.ACTION_USER_PRESENT: forceCloseAll(); rebuildHandle(); break;
                // Pantalla encendida sin bloqueo (sin PIN, por ejemplo): también se restaura.
                case Intent.ACTION_SCREEN_ON: forceCloseAll(); if (!locked()) rebuildHandle(); break;
                default: break;
            }
        }
    };

    private void registerGuard() {
        if (receiverOn) return;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, f);
            receiverOn = true;
        } catch (Exception e) {}
    }

    /** Si el panel quedó abierto sin contenido (o con la pantalla apagada), se cierra solo. */
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            PanelView p = panel;
            if (p != null && (screenOff() || p.isStuck())) forceCloseAll();
            if (panel != null || !closing.isEmpty()) ui.postDelayed(this, 1000);
        }
    };

    private void startWatchdog() { ui.removeCallbacks(watchdog); ui.postDelayed(watchdog, 1000); }

    /** Saca ya mismo, sin animación, todas las ventanas de panel que haya. Es idempotente. */
    public void forceCloseAll() {
        PanelView p = panel;
        panel = null; panelLp = null;
        if (p != null) { p.stopAnim(); removeNow(p); }
        for (PanelView c : new ArrayList<>(closing)) { c.stopAnim(); removeNow(c); }
        closing.clear();
    }

    private void removeNow(PanelView p) {
        closing.remove(p);
        try { wm.removeViewImmediate(p); } catch (Exception e) { /* ya no estaba */ }
        prewarm();
    }

    /** La ventana tiene que llegar hasta el borde real: debajo de la barra de estado y de la muesca de la cámara. */
    private void noInsets(WindowManager.LayoutParams lp) {
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.setFitInsetsTypes(0);
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    // ---------------- zona táctil ----------------

    private void removeHandle() {
        try { if (handle != null) wm.removeViewImmediate(handle); } catch (Exception e) {}
        handle = null; pillView = null;
    }

    /** Vuelve a crear la zona táctil (al cambiar la opción o al girar la pantalla). */
    public void rebuildHandle() {
        ui.post(() -> {
            removeHandle();
            if (!locked() && !screenOff()) addHandle();
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (wm != null) { warm = null; rebuildHandle(); prewarm(); }
    }

    /** Abre el panel en la vista acotada (lo usa también el botón "Probar" de la app). */
    public void open() {
        ui.post(() -> {
            if (panel == null) showPanel(false);
        });
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerGuard();
        if (!locked() && !screenOff()) addHandle();
        reapplyLock(0);
        Skin.prefs(this).registerOnSharedPreferenceChangeListener(prefListener);
        prewarm();
    }

    /** Si el bloqueo del panel del sistema estaba activo, lo reaplica (Shizuku puede tardar en arrancar). */
    private void reapplyLock(final int attempt) {
        if (!getSharedPreferences("cc", MODE_PRIVATE).getBoolean("lock", false)) return;
        if (Privileged.available()) Privileged.run("cmd statusbar send-disable-flag statusbar-expansion");
        else if (attempt < 8) ui.postDelayed(() -> reapplyLock(attempt + 1), 15000);
    }

    private void addHandle() {
        if (handle != null) return;
        final DisplayMetrics d = getResources().getDisplayMetrics();
        final float dp = d.density;
        FrameLayout v = new FrameLayout(this);
        v.setBackgroundColor(0x01000000);
        // Barrita visible para saber dónde deslizar
        View pill = new View(this);
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(Skin.get(this, "pillColor") != 0 ? Skin.get(this, "pillColor") : 0xE6FFFFFF);
        pg.setCornerRadius(Skin.get(this, "pillRadius") * dp);
        pill.setBackground(pg);
        pillView = pill;
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
            (int) (Skin.get(this, "pillW") * dp), (int) (Skin.get(this, "pillH") * dp), Gravity.CENTER_HORIZONTAL | Gravity.TOP
        );
        plp.topMargin = (int) (Skin.get(this, "pillTop") * dp);
        v.addView(pill, plp);

        // El panel sigue al dedo: se crea apenas empieza el arrastre y se acomoda al soltar.
        v.setOnTouchListener(new View.OnTouchListener() {
            float y0 = 0f;
            boolean dragging = false;
            VelocityTracker vt = null;
            long t0 = 0L;
            long lastTapUp = 0L;
            boolean longFired = false;
            final Runnable longR = () -> { longFired = true; runGesture(Skin.get(ControlService.this, "gestLong"), "gestLongP"); };
            final Runnable singleR = () -> runGesture(Skin.get(ControlService.this, "gestTap"), "gestTapP");

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        y0 = e.getRawY(); dragging = false;
                        t0 = e.getEventTime(); longFired = false; ui.removeCallbacks(longR);
                        if (Skin.get(ControlService.this, "gestLong") != 0) ui.postDelayed(longR, 500);
                        if (vt != null) vt.recycle();
                        vt = VelocityTracker.obtain(); vt.addMovement(e);
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        if (vt != null) vt.addMovement(e);
                        float dy = e.getRawY() - y0;
                        if (!dragging && dy > Skin.get(ControlService.this, "openThr") * dp) { dragging = true; ui.removeCallbacks(longR); showPanel(true); }
                        if (dragging && panel != null) panel.dragFromHandle(dy);
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        ui.removeCallbacks(longR);
                        if (!dragging && !longFired && e.getActionMasked() == MotionEvent.ACTION_UP && e.getEventTime() - t0 < 350) {
                            int gd = Skin.get(ControlService.this, "gestDouble");
                            if (gd != 0 && e.getEventTime() - lastTapUp < 350) {
                                ui.removeCallbacks(singleR);
                                lastTapUp = 0L;
                                runGesture(gd, "gestDoubleP");
                            } else {
                                lastTapUp = e.getEventTime();
                                if (gd != 0) ui.postDelayed(singleR, 350);
                                else singleR.run();
                            }
                        }
                        if (dragging) {
                            float vy = 0f;
                            if (vt != null) {
                                vt.computeCurrentVelocity(1000);
                                if (e.getActionMasked() == MotionEvent.ACTION_UP) vy = vt.getYVelocity();
                            }
                            if (panel != null) panel.release(vy);
                        }
                        dragging = false;
                        if (vt != null) vt.recycle();
                        vt = null;
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        String z = zone();
        int hw = z.equals("full") ? d.widthPixels : z.equals("center") ? d.widthPixels / 3 : d.widthPixels / 2;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            hw, (int) (Skin.get(this, "handleH") * dp),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        if (z.equals("left") || z.equals("full")) lp.gravity = Gravity.TOP | Gravity.START;
        else if (z.equals("center")) lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        else lp.gravity = Gravity.TOP | Gravity.END;
        noInsets(lp);
        try {
            wm.addView(v, lp);
            handle = v;
            applyPillAlpha();
        } catch (Exception e) { handle = null; pillView = null; }
    }

    private final Runnable prewarmR = () -> {
        if (warm == null && panel == null && wm != null && !locked() && !screenOff()) warm = new PanelView(this, this::hidePanel);
    };

    private void prewarm() {
        ui.removeCallbacks(prewarmR);
        ui.postDelayed(prewarmR, 800);
    }

    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sp, key) -> {
        warm = null;
        prewarm();
    };

    private void runGesture(int code, String paramKey) {
        String param = Skin.getStr(this, paramKey, "");
        if (param == null) param = "";
        switch (code) {
            case 1: open(); break;
            case 2: openEditor(); break;
            case 3: global(GLOBAL_ACTION_TAKE_SCREENSHOT); break;
            case 4: global(GLOBAL_ACTION_LOCK_SCREEN); break;
            case 5: global(GLOBAL_ACTION_POWER_DIALOG); break;
            case 6: global(GLOBAL_ACTION_HOME); break;
            case 7: global(GLOBAL_ACTION_BACK); break;
            case 8: global(GLOBAL_ACTION_RECENTS); break;
            case 9: global(GLOBAL_ACTION_NOTIFICATIONS); break;
            case 10: global(GLOBAL_ACTION_QUICK_SETTINGS); break;
            case 11: {
                Intent li = getPackageManager().getLaunchIntentForPackage(param.trim());
                if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); }
                break;
            }
            case 12:
                if (Privileged.available() && !param.trim().isEmpty()) Privileged.run(param.trim());
                break;
            default:
                break;
        }
    }

    public void openEditor() {
        ui.post(() -> {
            if (panel == null) showPanel(false);
            if (panel != null) panel.openEditor();
        });
    }

    // ---------------- fondo difuminado ----------------

    private void applyBackdrop(PanelView p) {
        if (new Skin(this).bgMode != BgMode.BLUR) return;
        if (!enableWinBlur(p)) p.noBlur();
    }

    /** Plan B: desenfoque del compositor (Android 12+), si el dispositivo lo permite. */
    private boolean enableWinBlur(final PanelView p) {
        final WindowManager.LayoutParams lp = panelLp;
        if (lp == null) return false;
        if (Build.VERSION.SDK_INT < 31 || !wm.isCrossWindowBlurEnabled()) return false;
        lp.flags = lp.flags | WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        p.blurHook = r -> {
            try { lp.setBlurBehindRadius(r); wm.updateViewLayout(p, lp); } catch (Exception e) {}
        };
        p.useWindowBlur();
        return true;
    }

    // ---------------- panel ----------------

    private void showPanel(boolean interactive) {
        if (panel != null) return;
        PanelView p = warm != null ? warm : new PanelView(this, this::hidePanel);
        warm = null;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0; lp.y = 0;
        noInsets(lp);   // sin esto quedaba una franja arriba (muesca / barra de estado) donde no llegaba el fondo
        try { wm.addView(p, lp); } catch (Exception e) { return; }
        panel = p; panelLp = lp;
        p.postDelayed(() -> p.refresh(), 120);
        applyBackdrop(p);
        if (!interactive) p.animateTo(Skin.get(this, "startStage") == 0 ? 1f : 2f);
        startWatchdog();
    }

    /**
     * Cierra con animación, pero sin depender de ella: la ventana en cierre no recibe toques (NOT_TOUCHABLE),
     * el panel ignora cualquier toque que llegue y un temporizador la saca pase lo que pase.
     * Antes, un toque durante la animación la cancelaba y la ventana quedaba huérfana a pantalla completa.
     */
    private void hidePanel() {
        final PanelView p = panel;
        if (p == null) return;
        WindowManager.LayoutParams lp = panelLp;
        panel = null; panelLp = null;
        closing.add(p);
        p.beginClose();
        if (lp != null) {
            lp.flags = lp.flags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { wm.updateViewLayout(p, lp); } catch (Exception e) {}
        }
        p.animateTo(0f, () -> removeNow(p));
        ui.postDelayed(() -> removeNow(p), Math.max(600, Skin.get(this, "animDur") + 200));
        startWatchdog();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Autocuración: si por algún motivo la zona táctil no está y el dispositivo está en uso, se repone.
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            wm != null && handle == null && !locked() && !screenOff()) addHandle();
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        warm = null;
        ui.removeCallbacks(prewarmR);
        Skin.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener);
        ui.removeCallbacks(watchdog);
        if (receiverOn) { try { unregisterReceiver(screenReceiver); } catch (Exception e) {} receiverOn = false; }
        if (wm != null) { forceCloseAll(); removeHandle(); }
        super.onDestroy();
    }
}
