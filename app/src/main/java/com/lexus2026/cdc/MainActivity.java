package com.lexus2026.cdc;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean asked = false;
    private boolean granting = false;
    private final Shizuku.OnRequestPermissionResultListener shizukuListener = (code, result) -> {
        if (result == PackageManager.PERMISSION_GRANTED) autoGrant();
    };

    private LinearLayout root;
    private float d;

    private void btn(String label, Runnable onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> onClick.run());
        root.addView(b);
    }

    private TextView label(String text, float size, int top) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setPadding(0, top, 0, 0);
        return t;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (24 * d), (int) (48 * d), (int) (24 * d), (int) (24 * d));
        TextView title = new TextView(this);
        title.setText("Centro de control");
        title.setTextSize(28f);
        root.addView(title);
        status = new TextView(this);
        status.setTextSize(15f);
        status.setPadding(0, (int) (16 * d), 0, (int) (16 * d));
        root.addView(status);
        btn("🎨 Make your action bar", () -> startActivity(new Intent(this, BuilderActivity.class)));
        btn("⚡ Activar todo con Shizuku", () -> {
            if (Privileged.available()) autoGrant();
            else try { Shizuku.requestPermission(1); } catch (Throwable e) {
                Toast.makeText(this, "Abrí Shizuku e iniciá el servicio primero", Toast.LENGTH_LONG).show();
            }
        });
        btn("▶ Probar centro de control ahora", () -> {
            ControlService svc = ControlService.instance;
            if (svc != null) svc.open();
            else Toast.makeText(this, "El servicio todavía no está activo", Toast.LENGTH_LONG).show();
        });
        root.addView(label("Sin Shizuku, permisos manuales:", 13f, (int) (20 * d)));
        btn("Accesibilidad", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        btn("Modificar ajustes (brillo, giro)", () ->
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))));
        btn("Acceso a No molestar", () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        TextView help = label("Uso: arrastrá hacia abajo desde la barrita blanca del borde superior. Primera bajada: notificaciones; bajá de nuevo para expandir los accesos. Deslizá a los costados para cambiar de pestaña. Mantené apretado un acceso para abrir sus ajustes; para agregar, quitar u ordenar accesos usá el botón ✎ o el botón de la pestaña de controles.",
            14f, (int) (16 * d));
        help.setGravity(Gravity.START);
        root.addView(help);
        root.addView(label("Panel del sistema:", 13f, (int) (20 * d)));
        btn("Suplantar sistema", () -> lock(true));
        btn("Restablecer sistema", () -> lock(false));
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
        try { Shizuku.addRequestPermissionResultListener(shizukuListener); } catch (Throwable e) {}
    }

    private void lock(boolean on) {
        if (!Privileged.available()) {
            Toast.makeText(this, "Necesita Shizuku activo", Toast.LENGTH_LONG).show();
            return;
        }
        Privileged.run("cmd statusbar send-disable-flag " + (on ? "statusbar-expansion" : "none"), () -> {
            getSharedPreferences("cc", MODE_PRIVATE).edit().putBoolean("lock", on).apply();
            Toast.makeText(this, on ? "Panel del sistema reemplazado" : "Panel del sistema restablecido",
                Toast.LENGTH_SHORT).show();
        });
    }

    /** Concede por Shizuku todos los permisos, incluida la accesibilidad. */
    private void autoGrant() {
        if (granting) return;
        granting = true;
        Privileged.grantAll(this, () -> ui.postDelayed(() -> {
            granting = false;
            showStatus();
            // Primera vez: si nuestro panel ya está vivo, se bloquea el panel del sistema.
            SharedPreferences prefs = getSharedPreferences("cc", MODE_PRIVATE);
            if (ControlService.instance != null && !prefs.contains("lock")) {
                Privileged.run("cmd statusbar send-disable-flag statusbar-expansion");
                prefs.edit().putBoolean("lock", true).apply();
            }
        }, 1500));
    }

    private boolean allDone() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (listeners == null) listeners = "";
        return ControlService.instance != null && Settings.System.canWrite(this) &&
            nm.isNotificationPolicyAccessGranted() && listeners.contains(getPackageName() + "/com.lexus2026.cdc.MediaListener");
    }

    @Override
    protected void onResume() {
        super.onResume();
        showStatus();
        // Con Shizuku corriendo, todo se configura solo.
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    if (!allDone()) autoGrant();
                } else if (!asked) { asked = true; Shizuku.requestPermission(1); }
            }
        } catch (Throwable e) {}
    }

    private static String m(boolean ok) { return ok ? "listo" : "pendiente"; }

    private void showStatus() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean acc = enabled != null && enabled.contains(getPackageName());
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        status.setText("Servicio corriendo: " + (ControlService.instance != null ? "sí" : "no") + "\n" +
            "Accesibilidad: " + m(acc) + "\nAjustes: " + m(Settings.System.canWrite(this)) + "\n" +
            "No molestar: " + m(nm.isNotificationPolicyAccessGranted()) + "\nShizuku: " + m(Privileged.available()));
    }

    @Override
    protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener); } catch (Throwable e) {}
        super.onDestroy();
    }
}
