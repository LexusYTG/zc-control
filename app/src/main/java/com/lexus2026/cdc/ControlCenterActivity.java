package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

/** ZC Control Center: interruptor del módulo, reemplazo del panel del sistema, apertura y atajos a la personalización. */
public class ControlCenterActivity extends Activity {
    private static final String[] ZONES = {"left", "center", "right", "full"};
    private ZKit.Page page;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Consent.granted(this)) { Consent.require(this, false); return; }
        page = new ZKit.Page(this, Lang.t(S.MOD_CC), Lang.t(S.MOD_CC_D), true);
        page.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page != null) render();
    }

    private void render() {
        final Activity a = this;
        page.clear();
        final boolean on = Features.controlCenter(a);

        // ---- estado y interruptor
        page.section(Lang.t(S.F_STATUS));
        LinearLayout st = page.card();
        page.toggle(st, Lang.t(S.F_ENABLE), Lang.t(S.CC_EN_D), on, v -> {
            Features.setControlCenter(a, v);
            if (v && !Features.accessibilityOn()) Toast.makeText(a, Lang.t(S.F_NEEDS_SVC), Toast.LENGTH_LONG).show();
            render();
        });
        page.divider(st);
        page.status(st, Lang.t(S.F_SVC) + ": " + (Features.accessibilityOn() ? Lang.t(S.ST_ON) : Lang.t(S.ST_OFF)), Features.accessibilityOn());
        page.status(st, Features.systemPanelBlocked(a) ? Lang.t(S.CC_SYS_BLOCKED) : Lang.t(S.CC_SYS_FREE), Features.systemPanelBlocked(a));
        if (on && !Features.accessibilityOn()) {
            st.addView(page.button(Lang.t(S.F_PERMS_GO), true, () -> startActivity(new Intent(a, PermissionCenterActivity.class))),
                ZKit.lp(a, -1, -2, 0f, 0, 4, 0, 10));
        }

        if (!on) {
            page.guide(Lang.t(S.F_GUIDE), Lang.t(S.CC_GUIDE));
            return;
        }

        // ---- reemplazo del panel del sistema
        page.section(Lang.t(S.MOD_CC));
        LinearLayout rep = page.card();
        page.toggle(rep, Lang.t(S.CC_REPLACE), Lang.t(S.CC_REPLACE_D), Features.systemPanelBlocked(a), v -> {
            if (!Privileged.available()) {
                Toast.makeText(a, Lang.t(S.T_NEED_SHZ), Toast.LENGTH_LONG).show();
                render();
                return;
            }
            Features.setSystemPanelBlocked(a, v, this::render);
        });
        page.divider(rep);
        page.nav(rep, Lang.t(S.CC_TEST), null, () -> {
            ControlService svc = ControlService.instance;
            if (svc != null) svc.open();
            else Toast.makeText(a, Lang.t(10), Toast.LENGTH_LONG).show();
        });

        // ---- apertura
        page.section(Lang.t(S.CC_ZONE));
        LinearLayout op = page.card();
        String cz = Skin.prefs(a).getString("zone", "full");
        int sel = 3;
        for (int i = 0; i < ZONES.length; i++) if (ZONES[i].equals(cz)) sel = i;
        page.chips(op, null, new String[]{Lang.t(S.CC_ZONE_L), Lang.t(S.CC_ZONE_C), Lang.t(S.CC_ZONE_R), Lang.t(S.CC_ZONE_F)}, sel, i -> {
            Skin.prefs(a).edit().putString("zone", ZONES[i]).apply();
            ControlService s = ControlService.instance;
            if (s != null) s.rebuildHandle();
            render();
        });
        page.slider(op, Lang.t(S.CC_PILL), 0, 100, " %",
            () -> Skin.prefs(a).getInt("pill_alpha", 60),
            v -> {
                Skin.prefs(a).edit().putInt("pill_alpha", v).apply();
                ControlService s = ControlService.instance;
                if (s != null) s.applyPillAlpha();
            });

        // ---- personalización avanzada (las pantallas detalladas ya existentes)
        page.section(Lang.t(S.F_ADV));
        LinearLayout adv = page.card();
        advRow(adv, "sistema/panel/apariencia");
        page.divider(adv);
        advRow(adv, "sistema/panel/accesos");
        page.divider(adv);
        advRow(adv, "sistema/panel/apertura");
        page.divider(adv);
        advRow(adv, "sistema/panel/temas");
        page.divider(adv);
        page.nav(adv, Lang.t(S.F_ADV), Lang.t(S.F_ADV_D), () -> {
            SettingsTree.Node n = SettingsTree.find("sistema/panel");
            if (n != null) SettingsPageActivity.open(a, n);
        });

        page.guide(Lang.t(S.F_GUIDE), Lang.t(S.CC_GUIDE));
    }

    private void advRow(LinearLayout card, String path) {
        final SettingsTree.Node n = SettingsTree.find(path);
        if (n == null) return;
        page.nav(card, n.title, null, () -> SettingsPageActivity.open(this, n));
    }
}
