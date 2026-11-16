package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;

public final class ShizukuNotice {
    private ShizukuNotice() {}

    private static boolean shown = false;

    public static void showOnce(Activity a) {
        if (shown) return;
        shown = true;
        show(a);
    }

    public static void show(final Activity a) {
        new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Requiere Shizuku")
            .setMessage("ZC Control usa Shizuku (permisos de shell/ADB) para cambiar ajustes del sistema, cerrar procesos, cambiar la resolución, compilar apps y restaurar todo al terminar. Sin Shizuku instalado, iniciado y con permiso concedido, estas funciones no funcionan.\n\nAbrí Shizuku, iniciá el servicio y volvé a esta app.")
            .setPositiveButton("Abrir Shizuku", (d, w) -> open(a))
            .setNegativeButton("Entendido", null)
            .show();
    }

    private static void open(Activity a) {
        Intent i = a.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"));
        try {
            a.startActivity(i);
        } catch (Exception e) {
        }
    }
}
