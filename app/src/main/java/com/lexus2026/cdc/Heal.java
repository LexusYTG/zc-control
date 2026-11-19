package com.lexus2026.cdc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Autorreparación. Al nacer el proceso: (1) deja en marcha el vigilante de Shizuku, que relanza el servicio si el proceso
 * muere (por ejemplo al "cerrar todo" en recientes); (2) si el servicio no quedó conectado, quita y vuelve a dar los permisos.
 */
final class Heal {
    private Heal() {}

    private static boolean started;

    static void start(Context c) {
        if (started) return;
        started = true;
        final Context app = c.getApplicationContext();
        final Handler h = new Handler(Looper.getMainLooper());
        final int[] waits = {0};
        final int[] revives = {0};
        final boolean[] watching = {false};
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!Consent.granted(app)) return;
                if (!Privileged.available()) {                                          // Shizuku todavía no responde
                    if (++waits[0] < 20) h.postDelayed(this, 1500);
                    return;
                }
                if (!watching[0]) {
                    watching[0] = true;
                    Privileged.startWatcher(app);
                }
                if (ControlService.instance != null) return;                           // ya conectado
                if (revives[0]++ >= 3) return;
                Privileged.revive(app, () -> h.postDelayed(this, 3000));
            }
        }, 2500);
    }
}
