package com.lexus2026.cdc;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/** Ejecuta comandos de shell con los permisos de Shizuku (sin root). */
public final class Privileged {
    private Privileged() {}

    public static boolean available() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    public static void run(String cmd) {
        run(cmd, null);
    }

    public static void run(final String cmd, final Runnable done) {
        new Thread(() -> {
            try {
                Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                m.setAccessible(true);
                Process p = (Process) m.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
                p.waitFor();
            } catch (Throwable e) { /* sin permiso o Shizuku detenido */ }
            if (done != null) new Handler(Looper.getMainLooper()).post(done);
        }).start();
    }

    public static void grantAll(Context ctx) {
        grantAll(ctx, null);
    }

    /** Concede por shell todos los permisos que la app necesitaba pedir a mano. */
    public static void grantAll(Context ctx, Runnable done) {
        String pkg = ctx.getPackageName();
        String svc = pkg + "/com.lexus2026.cdc.ControlService";
        String cur = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (cur == null) cur = "";
        String list = cur.contains(svc) ? cur : cur.isEmpty() ? svc : cur + ":" + svc;
        String cmd = String.join("; ",
                "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS",
                "appops set " + pkg + " ACCESS_RESTRICTED_SETTINGS allow",
                "appops set " + pkg + " WRITE_SETTINGS allow",
                "cmd notification allow_dnd " + pkg,
                "cmd notification allow_listener " + pkg + "/com.lexus2026.cdc.MediaListener",
                "dumpsys deviceidle whitelist +" + pkg,
                "settings put secure enabled_accessibility_services '" + list + "'",
                "settings put secure accessibility_enabled 1"
        );
        run(cmd, done);
    }
}
