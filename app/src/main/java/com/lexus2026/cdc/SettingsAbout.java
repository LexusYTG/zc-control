package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageInfo;

/** Diálogo «Acerca de». */
final class SettingsAbout {
    private SettingsAbout() {}

    static void show(Activity a) {
        String v = "";
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            v = "Versión " + pi.versionName;
        } catch (Throwable ignored) { }
        new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("ZC Control")
            .setMessage(v + "\n\nCapa de personalización alternativa para Android. Los ajustes propios se aplican con Shizuku "
                + "y se complementan con los del sistema.")
            .setPositiveButton("Cerrar", null)
            .show();
    }
}
