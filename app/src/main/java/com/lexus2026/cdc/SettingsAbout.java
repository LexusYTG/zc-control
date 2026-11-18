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
            v = Lang.t(506) + pi.versionName;
        } catch (Throwable ignored) { }
        new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(Lang.t(871))
            .setMessage(v + Lang.t(507)
                + Lang.t(508))
            .setPositiveButton(Lang.t(290), null)
            .show();
    }
}
