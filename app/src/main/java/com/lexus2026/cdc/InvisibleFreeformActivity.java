package com.lexus2026.cdc;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Actividad auxiliar invisible (el "freeform hack" de Taskbar): se lanza primero en modo ventana para que el
 * sistema entre en contexto de ventanas libres antes de abrir la app real. Se cierra sola enseguida.
 */
public class InvisibleFreeformActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        overridePendingTransition(0, 0);
        h.postDelayed(this::finish, 6000);
    }

    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
