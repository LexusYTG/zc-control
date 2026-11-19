package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/** ZC Permission Control: qué permisos tiene la app, cuáles faltan, cómo darlos y qué módulo usa cada uno. */
public class PermissionCenterActivity extends Activity {
    private ZKit.Page page;
    private boolean asked;

    private final Shizuku.OnRequestPermissionResultListener shizukuListener = (code, result) -> {
        if (result == PackageManager.PERMISSION_GRANTED) runOnUiThread(this::grantAll);
        else runOnUiThread(this::render);
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Consent.granted(this)) { Consent.require(this, false); return; }
        page = new ZKit.Page(this, Lang.t(S.PERMS), Lang.t(S.PERMS_D), true);
        page.show();
        try { Shizuku.addRequestPermissionResultListener(shizukuListener); } catch (Throwable e) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page != null) render();
    }

    private boolean binderAlive() {
        try { return Shizuku.pingBinder(); } catch (Throwable e) { return false; }
    }

    /** Pide a Shizuku el permiso si hace falta y concede todo lo demás por él. */
    private void grantAll() {
        if (Privileged.available()) {
            SetupHelper.autoGrant(this, () -> { render(); });
            Toast.makeText(this, Lang.t(S.SU_APPLYING), Toast.LENGTH_SHORT).show();
        } else if (binderAlive()) {
            try { Shizuku.requestPermission(3); } catch (Throwable e) { }
        } else {
            ShizukuNotice.show(this);
        }
    }

    private void render() {
        final Activity a = this;
        page.clear();
        boolean acc = Features.accessibilityOn(), not = Features.notificationListenerOn(a),
            wr = Features.writeSettingsOn(a), dnd = Features.dndOn(a), sec = Features.secureSettingsOn(a),
            shz = Privileged.available();
        boolean core = acc && not && wr && dnd;

        // ---- resumen + botón principal
        LinearLayout top = page.card();
        top.setPadding(ZKit.dp(a, 16), ZKit.dp(a, 12), ZKit.dp(a, 16), ZKit.dp(a, 14));
        page.status(top, core ? Lang.t(S.PM_ALL_OK) : Lang.t(S.PM_ALL_NO), core);
        if (!core || !sec) {
            TextView d = ZUi.text(a, Lang.t(S.PM_ALL_D), 12f, ZKit.SUB, false);
            d.setPadding(0, 0, 0, ZKit.dp(a, 10));
            top.addView(d);
            top.addView(page.button(Lang.t(S.PM_ALL), true, this::grantAll));
        }

        // ---- permisos uno por uno
        page.section(Lang.t(S.PERMS));
        LinearLayout list = page.card();
        list.setPadding(ZKit.dp(a, 16), ZKit.dp(a, 4), ZKit.dp(a, 16), ZKit.dp(a, 4));

        perm(list, Lang.t(S.PM_SHZ), shz ? Lang.t(S.PM_SHZ_D) : binderAlive() ? Lang.t(S.PM_SHZ_D) : Lang.t(S.PM_SHZ_OFF), shz,
            binderAlive() ? Lang.t(S.PM_GRANT) : Lang.t(S.PM_OPEN), () -> {
                if (binderAlive()) { try { Shizuku.requestPermission(3); } catch (Throwable e) { } }
                else ShizukuNotice.show(a);
            });
        page.divider(list);
        perm(list, Lang.t(S.PM_SEC), Lang.t(S.PM_SEC_D), sec, Lang.t(S.PM_GRANT), () -> {
            if (shz) SetupHelper.autoGrant(a, this::render);
            else Toast.makeText(a, Lang.t(S.T_NEED_SHZ), Toast.LENGTH_LONG).show();
        });
        page.divider(list);
        perm(list, Lang.t(S.PM_ACC), Lang.t(S.PM_ACC_D), acc, shz ? Lang.t(S.PM_GRANT) : Lang.t(S.PM_OPEN), () -> {
            if (shz) SetupHelper.autoGrant(a, this::render);
            else startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        page.divider(list);
        perm(list, Lang.t(S.PM_NOT), Lang.t(S.PM_NOT_D), not, shz ? Lang.t(S.PM_GRANT) : Lang.t(S.PM_OPEN), () -> {
            if (shz) SetupHelper.autoGrant(a, this::render);
            else startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        });
        page.divider(list);
        perm(list, Lang.t(S.PM_WRITE), Lang.t(S.PM_WRITE_D), wr, Lang.t(S.PM_OPEN), () ->
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))));
        page.divider(list);
        perm(list, Lang.t(S.PM_DND), Lang.t(S.PM_DND_D), dnd, Lang.t(S.PM_OPEN), () ->
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        page.divider(list);
        perm(list, Lang.t(S.PM_HOME), Lang.t(S.PM_HOME_D), Features.launcherIsHome(a), Lang.t(S.PM_OPEN), () ->
            startActivity(new Intent(a, LauncherCenterActivity.class)));

        // ---- mantenimiento
        page.section(Lang.t(S.F_SETTINGS));
        LinearLayout tools = page.card();
        page.nav(tools, Lang.t(S.PM_REVIVE), Lang.t(S.PM_REVIVE_D), () -> {
            if (!shz) { Toast.makeText(a, Lang.t(S.T_NEED_SHZ), Toast.LENGTH_LONG).show(); return; }
            Privileged.revive(a, () -> new Handler(Looper.getMainLooper()).postDelayed(this::render, 1500));
            Toast.makeText(a, Lang.t(S.SU_APPLYING), Toast.LENGTH_SHORT).show();
        });
        page.divider(tools);
        page.nav(tools, Lang.t(S.PM_SYS_ACC), null, () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        page.divider(tools);
        page.nav(tools, Lang.t(1346), Lang.t(1347), () -> Consent.withdraw(a));

        // ---- qué se ejecuta y con qué
        page.guide(Lang.t(S.PM_MAP_T),
            Lang.t(S.PM_MAP_CC) + "\n\n" + Lang.t(S.PM_MAP_HUB) + "\n\n" + Lang.t(S.PM_MAP_GAME) + "\n\n" + Lang.t(S.PM_MAP_LA));
    }

    /** Fila de permiso: nombre y estado, descripción y, si falta, el botón para resolverlo. */
    private void perm(LinearLayout card, String title, String desc, boolean ok, String action, Runnable fix) {
        final Activity a = this;
        LinearLayout r = ZUi.box(a, LinearLayout.VERTICAL);
        r.setPadding(0, ZKit.dp(a, 12), 0, ZKit.dp(a, 12));
        LinearLayout head = ZUi.box(a, LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ZUi.text(a, title, 15f, ZKit.TXT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(ZKit.pill(a, ok ? Lang.t(S.PM_OK) : Lang.t(S.PM_NO), ok ? ZKit.OK : ZKit.WARN));
        r.addView(head);
        TextView d = ZUi.text(a, desc, 12f, ZKit.SUB, false);
        d.setLineSpacing(0f, 1.1f);
        d.setPadding(0, ZKit.dp(a, 4), 0, 0);
        r.addView(d);
        if (!ok) {
            TextView b = page.button(action, false, fix);
            b.setPadding(ZKit.dp(a, 12), ZKit.dp(a, 10), ZKit.dp(a, 12), ZKit.dp(a, 10));
            r.addView(b, ZKit.lp(a, -1, -2, 0f, 0, 10, 0, 0));
        }
        card.addView(r);
    }

    @Override
    protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener); } catch (Throwable e) { }
        super.onDestroy();
    }
}
