package com.lexus2026.cdc;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * Los cuatro módulos que ve el usuario (Control Center, Action Hub, Game Center y Launcher) sobre una única app.
 * Acá vive el estado de cada uno, cómo se enciende o apaga y qué permisos necesita. Cada módulo guarda su estado
 * donde ya lo guardaba el código existente (burbuja → SideConfig, juegos → GameSettings); lo único nuevo son
 * el interruptor del Control Center y la marca de «asistente inicial terminado».
 */
final class Features {
    private Features() {}

    private static final String FILE = "zc_features";
    private static final String K_SETUP = "setup_done", K_CC = "cc_on", K_LAST_SIDE = "last_side_mode";
    static final int REQ_HOME_ROLE = 77;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ asistente inicial

    static boolean setupDone(Context c) { return sp(c).getBoolean(K_SETUP, false); }

    static void setSetupDone(Context c) { sp(c).edit().putBoolean(K_SETUP, true).apply(); }

    // ------------------------------------------------------------------ Control Center

    /** Por defecto encendido: así se comportaba la app antes de existir el interruptor. */
    static boolean controlCenter(Context c) { return sp(c).getBoolean(K_CC, true); }

    static void setControlCenter(Context c, boolean on) {
        sp(c).edit().putBoolean(K_CC, on).apply();
        ControlService s = ControlService.instance;
        if (s != null) s.rebuildHandle();      // pone o saca la zona táctil de la barrita
        if (!on) setSystemPanelBlocked(c, false, null);   // apagado: se devuelve el panel del sistema
    }

    static boolean systemPanelBlocked(Context c) {
        return c.getApplicationContext().getSharedPreferences("cc", Context.MODE_PRIVATE).getBoolean("lock", false);
    }

    /** Bloquea o libera el panel original (necesita Shizuku). Devuelve false si no se pudo. */
    static boolean setSystemPanelBlocked(Context c, boolean on, Runnable done) {
        final Context app = c.getApplicationContext();
        if (!Privileged.available()) {
            if (!on) app.getSharedPreferences("cc", Context.MODE_PRIVATE).edit().putBoolean("lock", false).apply();
            if (done != null) done.run();
            return !on;
        }
        Privileged.run("cmd statusbar send-disable-flag " + (on ? "statusbar-expansion" : "none"), () -> {
            app.getSharedPreferences("cc", Context.MODE_PRIVATE).edit().putBoolean("lock", on).apply();
            if (done != null) done.run();
        });
        return true;
    }

    // ------------------------------------------------------------------ Action Hub (burbuja)

    static boolean hub(Context c) { return SideConfig.getMode(c) != SideConfig.MODE_OFF; }

    static void setHub(Context c, boolean on) {
        if (on) {
            int m = sp(c).getInt(K_LAST_SIDE, SideConfig.MODE_BUBBLE);
            if (m == SideConfig.MODE_GESTURE && !SideConfig.threeButtonNav(c)) m = SideConfig.MODE_BUBBLE;
            SideConfig.setMode(c, m == SideConfig.MODE_OFF ? SideConfig.MODE_BUBBLE : m);
        } else {
            int cur = SideConfig.getMode(c);
            if (cur != SideConfig.MODE_OFF) sp(c).edit().putInt(K_LAST_SIDE, cur).apply();
            SideConfig.setMode(c, SideConfig.MODE_OFF);
        }
    }

    static void setHubMode(Context c, int mode) {
        if (mode != SideConfig.MODE_OFF) sp(c).edit().putInt(K_LAST_SIDE, mode).apply();
        SideConfig.setMode(c, mode);
    }

    static SideHub sideHub() {
        ControlService cs = ControlService.instance;
        return cs != null ? cs.side() : null;
    }

    // ------------------------------------------------------------------ Game Center

    static boolean game(Context c) { return GameSettings.enabled(c); }

    static void setGame(Context c, boolean on) { GameSettings.setEnabled(c, on); }

    // ------------------------------------------------------------------ Launcher

    static String homePackage(Context c) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null ? ri.activityInfo.packageName : null;
        } catch (Throwable e) {
            return null;
        }
    }

    static boolean launcherIsHome(Context c) { return c.getPackageName().equals(homePackage(c)); }

    static String homeLabel(Context c) {
        String p = homePackage(c);
        if (p == null) return "—";
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString();
        } catch (Throwable e) {
            return p;
        }
    }

    /**
     * Con Shizuku fija o quita el rol de inicio sin preguntar; sin Shizuku, al activar abre el selector de Android
     * (lo confirma el usuario) y al desactivar abre los ajustes de inicio.
     */
    static void setLauncher(Activity a, boolean on, Runnable done) {
        final String pkg = a.getPackageName();
        if (Privileged.available()) {
            Privileged.run("cmd role " + (on ? "add" : "remove") + "-role-holder --user 0 android.app.role.HOME " + pkg, done);
            return;
        }
        try {
            if (on && Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = (RoleManager) a.getSystemService(Context.ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME_ROLE);
                    if (done != null) done.run();
                    return;
                }
            }
            a.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Throwable e) {
            try { a.startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) { }
        }
        if (done != null) done.run();
    }

    // ------------------------------------------------------------------ permisos según módulos activos

    static boolean accessibilityOn() { return ControlService.instance != null; }

    static boolean notificationListenerOn(Context c) {
        String l = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return l != null && l.contains(c.getPackageName() + "/com.lexus2026.cdc.MediaListener");
    }

    static boolean writeSettingsOn(Context c) { return Settings.System.canWrite(c); }

    static boolean dndOn(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    static boolean secureSettingsOn(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    /** ¿Algún módulo elegido por el usuario no tiene lo que necesita? Lo que no usa, no molesta. */
    static boolean needsAttention(Context c) {
        boolean cc = controlCenter(c), hub = hub(c), game = game(c);
        if ((cc || hub || game) && !accessibilityOn()) return true;
        if (cc && (!notificationListenerOn(c) || !writeSettingsOn(c) || !dndOn(c))) return true;
        if (game && !Privileged.available()) return true;
        return false;
    }

    static boolean ccNeedsAttention(Context c) {
        return controlCenter(c) && (!accessibilityOn() || !notificationListenerOn(c));
    }

    static boolean hubNeedsAttention(Context c) { return hub(c) && !accessibilityOn(); }

    static boolean gameNeedsAttention(Context c) { return game(c) && !Privileged.available(); }

    // ------------------------------------------------------------------ aplicar elecciones del asistente

    /** Lo que quedó pendiente porque Shizuku no estaba: se completa apenas haya Shizuku (asistente y Permisos lo llaman). */
    static void finishPending(Context c) {
        SharedPreferences cc = c.getApplicationContext().getSharedPreferences("cc", Context.MODE_PRIVATE);
        if (controlCenter(c) && !cc.contains("lock") && Privileged.available()) setSystemPanelBlocked(c, true, null);
    }

    /**
     * Aplica la elección del asistente. {@code full} = paquete completo: además deja el launcher como inicio,
     * suspende la barra del sistema y pone la burbuja (nunca el gesto, que es experimental).
     */
    static void apply(final Activity a, final boolean full, final boolean cc, final boolean hub,
                      final boolean game, final boolean launcher, final Runnable done) {
        final Context app = a.getApplicationContext();
        sp(app).edit().putBoolean(K_CC, cc).apply();
        if (hub) setHubMode(app, SideConfig.MODE_BUBBLE); else setHub(app, false);
        setGame(app, game);
        final Handler ui = new Handler(Looper.getMainLooper());
        final Runnable finish = () -> {
            setSetupDone(app);
            if (done != null) done.run();
        };
        if (!Privileged.available()) {
            // Sin Shizuku solo se guardan las elecciones; el resto queda para ZC Permission Control.
            ControlService s = ControlService.instance;
            if (s != null) s.rebuildHandle();
            finish.run();
            return;
        }
        Privileged.grantAll(app, () -> ui.postDelayed(() -> {
            ControlService s = ControlService.instance;
            if (s != null) s.rebuildHandle();
            Runnable afterLock = () -> {
                if (launcher) setLauncher(a, true, finish); else finish.run();
            };
            if (cc && (full || !app.getSharedPreferences("cc", Context.MODE_PRIVATE).contains("lock"))) {
                setSystemPanelBlocked(app, true, afterLock);
            } else {
                afterLock.run();
            }
        }, 1500));
    }
}
