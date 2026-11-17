package com.lexus2026.cdc;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/** Comprobación y concesión automática de permisos (compartida por la pantalla principal y la de permisos). */
final class SetupHelper {
    private SetupHelper() {}

    private static boolean granting;

    static boolean allDone(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        String listeners = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        if (listeners == null) listeners = "";
        return ControlService.instance != null && Settings.System.canWrite(c)
            && nm.isNotificationPolicyAccessGranted() && listeners.contains(c.getPackageName() + "/com.lexus2026.cdc.MediaListener");
    }

    /** Concede por Shizuku todos los permisos; la primera vez, si el panel ya está vivo, bloquea el del sistema. */
    static void autoGrant(final Context c, final Runnable done) {
        if (granting) return;
        granting = true;
        final Context app = c.getApplicationContext();
        Privileged.grantAll(app, () -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
            granting = false;
            SharedPreferences prefs = app.getSharedPreferences("cc", Context.MODE_PRIVATE);
            if (ControlService.instance != null && !prefs.contains("lock")) {
                Privileged.run("cmd statusbar send-disable-flag statusbar-expansion");
                prefs.edit().putBoolean("lock", true).apply();
            }
            if (done != null) done.run();
        }, 1500));
    }
}
