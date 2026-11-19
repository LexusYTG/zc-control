package com.lexus2026.cdc;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Consentimiento del usuario: mientras no exista, ningún componente de la app lee, pide ni modifica nada. */
final class Consent {
    private Consent() {}

    private static final String FILE = "zc_consent", KEY = "accepted_v2", VER = "2.0";

    static boolean granted(Context c) {
        try {
            return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false);
        } catch (Throwable e) {
            return false;
        }
    }

    /** Guarda la aceptación y despierta los servicios que el sistema ya había conectado en modo inerte. */
    static void accept(Context c) {
        c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).putString("eula_version", VER).putLong("accepted_at", System.currentTimeMillis()).commit();
        ControlService s = ControlService.connected;
        if (s != null) s.onConsentGranted();
        MediaListener.activate();
    }

    /** Lleva a la pantalla de consentimiento y cierra la actividad que la pidió. */
    static void require(Activity a, boolean home) {
        a.startActivity(new Intent(a, ConsentActivity.class).putExtra("home", home));
        a.finish();
    }

    static void withdraw(final Activity a) {
        String msg = Lang.t(1341);
        if (Features.systemPanelBlocked(a) && !Privileged.available()) msg = msg + "\n\n" + Lang.t(1348);
        new AlertDialog.Builder(a)
            .setTitle(Lang.t(1340))
            .setMessage(msg)
            .setPositiveButton(Lang.t(1342), (d, w) -> runWithdraw(a))
            .setNegativeButton(Lang.t(1343), null)
            .show();
    }

    private static void runWithdraw(final Activity a) {
        final Context app = a.getApplicationContext();
        final String pkg = app.getPackageName();
        final String not = pkg + "/com.lexus2026.cdc.MediaListener";
        LinearLayout t = ZUi.box(a, LinearLayout.VERTICAL);
        t.setBackgroundColor(ZUi.BG);
        t.setGravity(Gravity.CENTER);
        int pad = ZUi.dp(a, 28);
        t.setPadding(pad, pad, pad, pad);
        TextView h = ZUi.text(a, Lang.t(1344), 22f, ZUi.TXT, true);
        h.setGravity(Gravity.CENTER);
        TextView s = ZUi.text(a, Lang.t(1345), 14f, ZUi.SUB, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, ZUi.dp(a, 12), 0, 0);
        t.addView(h);
        t.addView(s);
        a.setContentView(t);

        final boolean shz = Privileged.available();
        final boolean sec = Features.secureSettingsOn(app);
        app.getSharedPreferences("cc", Context.MODE_PRIVATE).edit().putBoolean("lock", false).commit();
        app.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().commit();
        String accLeft = "";
        try { accLeft = filtered(app, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, pkg); } catch (Throwable e) { }
        if (sec) {
            strip(app, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, pkg);
            strip(app, "enabled_notification_listeners", pkg);
        }
        if (!shz && !sec) {
            try { a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable e) { }
        }
        if (shz) {
            String acc = accLeft.isEmpty()
                ? "settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0"
                : "settings put secure enabled_accessibility_services '" + accLeft + "'";
            Privileged.run(String.join("; ",
                "cmd statusbar send-disable-flag none",
                "cmd notification disallow_listener " + not,
                "cmd notification disallow_dnd " + pkg,
                "appops set " + pkg + " WRITE_SETTINGS default",
                "appops set " + pkg + " SYSTEM_ALERT_WINDOW default",
                "dumpsys deviceidle whitelist -" + pkg,
                acc,
                "pm revoke " + pkg + " android.permission.WRITE_SECURE_SETTINGS",
                "pm clear " + pkg));
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> wipe(a), shz ? 6000 : 1500);
    }

    private static String filtered(Context c, String key, String pkg) {
        String cur = Settings.Secure.getString(c.getContentResolver(), key);
        if (cur == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String e : cur.split(":")) {
            if (e.isEmpty() || e.startsWith(pkg + "/")) continue;
            if (sb.length() > 0) sb.append(':');
            sb.append(e);
        }
        return sb.toString();
    }

    private static void strip(Context c, String key, String pkg) {
        try { Settings.Secure.putString(c.getContentResolver(), key, filtered(c, key, pkg)); } catch (Throwable e) { }
    }

    private static void wipe(Activity a) {
        a.finishAndRemoveTask();
        boolean ok = false;
        try { ok = ((ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE)).clearApplicationUserData(); } catch (Throwable e) { }
        if (!ok) android.os.Process.killProcess(android.os.Process.myPid());
    }
}
