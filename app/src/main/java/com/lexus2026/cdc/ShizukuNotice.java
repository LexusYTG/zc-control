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
            .setTitle(Lang.t(518))
            .setMessage(Lang.t(519))
            .setPositiveButton(Lang.t(520), (d, w) -> open(a))
            .setNegativeButton(Lang.t(521), null)
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
