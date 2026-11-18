package com.lexus2026.cdc;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0B0F1A, CARD = 0xFF151B2C, CARD_HI = 0xFF1D2540, TXT = 0xFFF1F5F9,
        SUB = 0xFF94A3B8, OK = 0xFF34D399, WARN = 0xFFFBBF24, ACC = 0xFF6C8CFF;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean asked = false;
    private boolean granting = false;
    private final Shizuku.OnRequestPermissionResultListener shizukuListener = (code, result) -> {
        if (result == PackageManager.PERMISSION_GRANTED) autoGrant();
    };

    private LinearLayout root;
    private float d;
    private final Map<String, TextView> chips = new LinkedHashMap<>();
    private TextView heroTitle;
    private TextView heroSub;
    private TextView activate;

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }

    private GradientDrawable shape(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout box(int orientation) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(orientation);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private void section(String title) {
        TextView t = text(title.toUpperCase(), 11f, SUB, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(4), dp(26), 0, dp(10));
        root.addView(t);
    }

    private View tile(String icon, String title, String sub, Runnable onClick, int weightMargin) {
        LinearLayout c = box(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(shape(CARD, 20));
        c.setClickable(true);
        c.setOnClickListener(v -> onClick.run());
        TextView ic = text(icon, 24f, TXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(shape(CARD_HI, 14));
        c.addView(ic, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView t = text(title, 15f, TXT, true);
        t.setPadding(0, dp(12), 0, 0);
        c.addView(t);
        TextView s = text(sub, 12f, SUB, false);
        s.setPadding(0, dp(2), 0, 0);
        c.addView(s);
        return c;
    }

    private void tileRow(View a, View b) {
        LinearLayout r = box(LinearLayout.HORIZONTAL);
        r.addView(a, lp(0, -2, 1f, 0, 0, 6, 0));
        r.addView(b, lp(0, -2, 1f, 6, 0, 0, 0));
        root.addView(r, lp(-1, -2, 0f, 0, 0, 0, 12));
    }

    private View listRow(String title, String sub, Runnable onClick) {
        LinearLayout r = box(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(18), dp(14), dp(18), dp(14));
        r.setClickable(true);
        r.setOnClickListener(v -> onClick.run());
        LinearLayout col = box(LinearLayout.VERTICAL);
        col.addView(text(title, 15f, TXT, false));
        if (sub != null) col.addView(text(sub, 12f, SUB, false));
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(text("›", 22f, SUB, false));
        return r;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0x1AFFFFFF);
        return v;
    }

    private TextView pill(String label, int fill, int color, Runnable onClick) {
        TextView t = text(label, 14f, color, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(14), dp(12), dp(14));
        t.setBackground(shape(fill, 16));
        t.setClickable(true);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    private TextView chip(String key, String label) {
        TextView t = text(label, 12f, SUB, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        chips.put(key, t);
        return t;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        root = box(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(32));

        root.addView(text(Lang.t(638), 30f, TXT, true));
        TextView subtitle = text(Lang.t(450), 14f, SUB, false);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        root.addView(subtitle);

        LinearLayout hero = box(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(14));
        hero.setBackground(shape(CARD, 24));
        heroTitle = text("", 20f, TXT, true);
        hero.addView(heroTitle);
        heroSub = text("", 13f, SUB, false);
        heroSub.setPadding(0, dp(4), 0, dp(14));
        hero.addView(heroSub);
        LinearLayout r1 = box(LinearLayout.HORIZONTAL);
        r1.addView(chip("svc", Lang.t(451)), lp(0, -2, 1f, 0, 0, 4, 8));
        r1.addView(chip("acc", Lang.t(865)), lp(0, -2, 1f, 4, 0, 4, 8));
        r1.addView(chip("wr", Lang.t(240)), lp(0, -2, 1f, 4, 0, 0, 8));
        hero.addView(r1);
        LinearLayout r2 = box(LinearLayout.HORIZONTAL);
        r2.addView(chip("dnd", Lang.t(889)), lp(0, -2, 1f, 0, 0, 4, 0));
        r2.addView(chip("shz", Lang.t(452)), lp(0, -2, 1f, 4, 0, 0, 0));
        hero.addView(r2);
        root.addView(hero);

        activate = pill(Lang.t(453), ACC, 0xFF0B0F1A, () -> {
            if (Privileged.available()) autoGrant();
            else try { Shizuku.requestPermission(1); } catch (Throwable e) {
                Toast.makeText(this, Lang.t(454), Toast.LENGTH_LONG).show();
            }
        });
        root.addView(activate, lp(-1, -2, 0f, 0, 12, 0, 0));

        section(Lang.t(640));
        tileRow(
            tile("🎨", Lang.t(641), Lang.t(642), () -> SettingsPageActivity.open(this, SettingsTree.find("sistema/panel")), 0),
            tile("🫧", Lang.t(643), Lang.t(455), () ->
                SettingsPageActivity.open(this, SettingsTree.find("personalizacion/lateral")), 0));
        tileRow(
            tile("🚀", Lang.t(623), Lang.t(644), () -> startActivity(new Intent(this, LauncherSettingsActivity.class)), 0),
            tile("🎮", Lang.t(645), Lang.t(646), () -> startActivity(new Intent(this, GamesActivity.class)), 0));

        section(Lang.t(456));
        tileRow(
            tile("▶", Lang.t(457), Lang.t(458), () -> {
                ControlService svc = ControlService.instance;
                if (svc != null) svc.open();
                else Toast.makeText(this, Lang.t(10), Toast.LENGTH_LONG).show();
            }, 0),
            tile("🏠", Lang.t(647), Lang.t(648), () -> startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)), 0));

        section(Lang.t(459));
        LinearLayout perms = box(LinearLayout.VERTICAL);
        perms.setBackground(shape(CARD, 20));
        perms.addView(listRow(Lang.t(865), Lang.t(649), () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        perms.addView(divider(), new LinearLayout.LayoutParams(-1, 1));
        perms.addView(listRow(Lang.t(460), Lang.t(461), () ->
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())))));
        perms.addView(divider(), new LinearLayout.LayoutParams(-1, 1));
        perms.addView(listRow(Lang.t(889), Lang.t(650), () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))));
        root.addView(perms);

        section(Lang.t(866));
        LinearLayout lockRow = box(LinearLayout.HORIZONTAL);
        lockRow.addView(pill(Lang.t(462), CARD_HI, TXT, () -> lock(true)), lp(0, -2, 1f, 0, 0, 6, 0));
        lockRow.addView(pill(Lang.t(463), CARD_HI, TXT, () -> lock(false)), lp(0, -2, 1f, 6, 0, 0, 0));
        root.addView(lockRow);

        section(Lang.t(464));
        TextView help = text("Uso: arrastrá hacia abajo desde la barrita blanca del borde superior. Primera bajada: notificaciones; bajá de nuevo para expandir los accesos. Deslizá a los costados para cambiar de pestaña. Mantené apretado un acceso para abrir sus ajustes; para agregar, quitar u ordenar accesos usá el botón ✎ o el botón de la pestaña de controles.", 13f, SUB, false);
        help.setLineSpacing(0f, 1.2f);
        help.setPadding(dp(18), dp(16), dp(18), dp(16));
        help.setBackground(shape(CARD, 20));
        root.addView(help);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.addView(root);
        sv.setClipToPadding(false);
        Insets.apply(sv);
        setContentView(sv);
        if (!Privileged.available()) ShizukuNotice.showOnce(this);
        try { Shizuku.addRequestPermissionResultListener(shizukuListener); } catch (Throwable e) {}
    }

    private void lock(boolean on) {
        if (!Privileged.available()) {
            Toast.makeText(this, Lang.t(59), Toast.LENGTH_LONG).show();
            return;
        }
        Privileged.run("cmd statusbar send-disable-flag " + (on ? "statusbar-expansion" : "none"), () -> {
            getSharedPreferences("cc", MODE_PRIVATE).edit().putBoolean("lock", on).apply();
            Toast.makeText(this, on ? Lang.t(465) : Lang.t(466),
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

    private void mark(String key, boolean ok) {
        TextView t = chips.get(key);
        if (t == null) return;
        t.setTextColor(ok ? OK : WARN);
        t.setBackground(shape(ok ? 0x2234D399 : 0x22FBBF24, 12));
        t.setText((ok ? "✓ " : "• ") + t.getText().toString().replaceFirst("^[✓•] ", ""));
    }

    private void showStatus() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean acc = enabled != null && enabled.contains(getPackageName());
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean svc = ControlService.instance != null;
        boolean wr = Settings.System.canWrite(this);
        boolean dnd = nm.isNotificationPolicyAccessGranted();
        boolean shz = Privileged.available();
        mark("svc", svc);
        mark("acc", acc);
        mark("wr", wr);
        mark("dnd", dnd);
        mark("shz", shz);
        boolean all = svc && acc && wr && dnd;
        heroTitle.setText(all ? Lang.t(467) : Lang.t(468));
        heroTitle.setTextColor(all ? OK : WARN);
        heroSub.setText(all ? Lang.t(469) : shz ? Lang.t(470) : Lang.t(471));
        activate.setVisibility(all ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener); } catch (Throwable e) {}
        super.onDestroy();
    }
}
