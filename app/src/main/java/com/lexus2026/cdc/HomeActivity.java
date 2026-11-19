package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/**
 * Pantalla principal: cuatro módulos (Control Center, Action Hub, Game Center, Launcher) y, abajo, Permisos y
 * Ajustes del sistema. Es una sola app por dentro; por fuera se maneja como cuatro herramientas independientes.
 */
public class HomeActivity extends Activity {
    private ZKit.Page page;
    private boolean asked;

    private final Shizuku.OnRequestPermissionResultListener shizukuListener = (code, result) -> {
        if (result == PackageManager.PERMISSION_GRANTED) SetupHelper.autoGrant(this, this::render);
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Consent.granted(this)) { Consent.require(this, false); return; }
        if (!Features.setupDone(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        page = new ZKit.Page(this, "ZC Control", Lang.t(S.HOME_SUB), false);
        page.show();
        try { Shizuku.addRequestPermissionResultListener(shizukuListener); } catch (Throwable e) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page == null) return;
        render();
        // Con Shizuku corriendo y algún módulo activo, los permisos se completan solos.
        try {
            if (Shizuku.pingBinder() && (Features.controlCenter(this) || Features.hub(this) || Features.game(this))) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    if (Features.needsAttention(this)) SetupHelper.autoGrant(this, this::render);
                    else Features.finishPending(this);
                } else if (!asked) { asked = true; Shizuku.requestPermission(1); }
            }
        } catch (Throwable e) { }
    }

    private void render() {
        if (page == null) return;
        page.clear();
        final Activity a = this;

        // idioma
        TextView lang = ZUi.text(a, "🌐  " + Lang.t(S.LANG_BTN), 13f, ZKit.SUB, true);
        lang.setGravity(Gravity.CENTER);
        lang.setPadding(ZKit.dp(a, 14), ZKit.dp(a, 8), ZKit.dp(a, 14), ZKit.dp(a, 8));
        lang.setBackground(ZUi.shape(a, ZKit.CARD, 20));
        lang.setOnClickListener(v -> Lang.pick(a));
        page.content.addView(lang, ZKit.lp(a, -2, -2, 0f, 0, 0, 0, 14));

        // aviso general
        if (Features.needsAttention(a)) {
            LinearLayout banner = ZUi.box(a, LinearLayout.HORIZONTAL);
            banner.setGravity(Gravity.CENTER_VERTICAL);
            banner.setPadding(ZKit.dp(a, 16), ZKit.dp(a, 14), ZKit.dp(a, 16), ZKit.dp(a, 14));
            banner.setBackground(ZUi.shape(a, 0x22FBBF24, 16));
            banner.addView(ZUi.text(a, Lang.t(S.HOME_ATTN), 14f, ZKit.WARN, true), new LinearLayout.LayoutParams(0, -2, 1f));
            banner.addView(ZUi.text(a, "›", 22f, ZKit.WARN, false));
            banner.setOnClickListener(v -> startActivity(new Intent(a, PermissionCenterActivity.class)));
            page.content.addView(banner, ZKit.lp(a, -1, -2, 0f, 0, 0, 0, 14));
        }

        // las cuatro tarjetas
        LinearLayout r1 = ZUi.box(a, LinearLayout.HORIZONTAL);
        r1.addView(module("🎛", Lang.t(S.MOD_CC), Lang.t(S.MOD_CC_D),
            Features.controlCenter(a), Features.ccNeedsAttention(a), ControlCenterActivity.class), ZKit.lp(a, 0, -1, 1f, 0, 0, 6, 0));
        r1.addView(module("🫧", Lang.t(S.MOD_HUB), Lang.t(S.MOD_HUB_D),
            Features.hub(a), Features.hubNeedsAttention(a), ActionHubActivity.class), ZKit.lp(a, 0, -1, 1f, 6, 0, 0, 0));
        page.content.addView(r1, ZKit.lp(a, -1, -2, 0f, 0, 0, 0, 12));
        LinearLayout r2 = ZUi.box(a, LinearLayout.HORIZONTAL);
        r2.addView(module("🎮", Lang.t(S.MOD_GAME), Lang.t(S.MOD_GAME_D),
            Features.game(a), Features.gameNeedsAttention(a), GameCenterActivity.class), ZKit.lp(a, 0, -1, 1f, 0, 0, 6, 0));
        r2.addView(module("🚀", Lang.t(S.MOD_LA), Lang.t(S.MOD_LA_D),
            Features.launcherIsHome(a), false, LauncherCenterActivity.class), ZKit.lp(a, 0, -1, 1f, 6, 0, 0, 0));
        page.content.addView(r2, ZKit.lp(a, -1, -2, 0f, 0, 0, 0, 4));

        // permisos y ajustes del sistema
        page.section("");
        LinearLayout card = page.card();
        card.addView(ZUi.row(a, "🔐", Lang.t(S.PERMS), Lang.t(S.PERMS_D), false,
            v -> startActivity(new Intent(a, PermissionCenterActivity.class))));
        page.divider(card);
        card.addView(ZUi.row(a, "⚙", Lang.t(S.SYS), Lang.t(S.SYS_D), false, v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Throwable e) { Toast.makeText(a, Lang.t(515), Toast.LENGTH_SHORT).show(); }
        }));

        page.guide(Lang.t(S.HOME_GUIDE_T), Lang.t(S.HOME_GUIDE));
    }

    /** Tarjeta de un módulo: ícono, nombre, descripción y estado (activo / inactivo / requiere atención). */
    private LinearLayout module(String icon, String title, String desc, boolean on, boolean attn, Class<? extends Activity> target) {
        final Activity a = this;
        LinearLayout c = ZUi.box(a, LinearLayout.VERTICAL);
        c.setPadding(ZKit.dp(a, 16), ZKit.dp(a, 16), ZKit.dp(a, 16), ZKit.dp(a, 16));
        c.setBackground(ZUi.shape(a, ZKit.CARD, 22));
        c.setClickable(true);
        c.setOnClickListener(v -> startActivity(new Intent(a, target)));

        TextView ic = ZUi.text(a, icon, 24f, ZKit.TXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(ZUi.shape(a, ZKit.CARD_HI, 14));
        c.addView(ic, new LinearLayout.LayoutParams(ZKit.dp(a, 48), ZKit.dp(a, 48)));

        TextView t = ZUi.text(a, title, 15f, ZKit.TXT, true);
        t.setPadding(0, ZKit.dp(a, 12), 0, 0);
        c.addView(t);
        TextView d = ZUi.text(a, desc, 12f, ZKit.SUB, false);
        d.setPadding(0, ZKit.dp(a, 4), 0, ZKit.dp(a, 12));
        d.setLineSpacing(0f, 1.1f);
        c.addView(d, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView st = attn ? ZKit.pill(a, Lang.t(S.ST_ATTN), ZKit.WARN)
            : on ? ZKit.pill(a, Lang.t(S.ST_ON), ZKit.OK) : ZKit.pill(a, Lang.t(S.ST_OFF), ZKit.SUB);
        c.addView(st, new LinearLayout.LayoutParams(-2, -2));
        return c;
    }

    @Override
    protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener); } catch (Throwable e) { }
        super.onDestroy();
    }
}
