package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/** Consentimiento del usuario: mientras no exista, ningún componente de la app lee, pide ni modifica nada. */
final class Consent {
    private Consent() {}

    private static final String FILE = "zc_consent", KEY = "accepted_v1";

    static boolean granted(Context c) {
        try {
            return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false);
        } catch (Throwable e) {
            return false;
        }
    }

    /** Guarda la aceptación y despierta los servicios que el sistema ya había conectado en modo inerte. */
    static void accept(Context c) {
        c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).commit();
        ControlService s = ControlService.connected;
        if (s != null) s.onConsentGranted();
        MediaListener.activate();
    }

    /** Lleva a la pantalla de consentimiento y cierra la actividad que la pidió. */
    static void require(Activity a, boolean home) {
        a.startActivity(new Intent(a, ConsentActivity.class).putExtra("home", home));
        a.finish();
    }
}
