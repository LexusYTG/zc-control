package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

/** ZC Action Hub: burbuja flotante (o gesto de borde, experimental) con accesos y notificaciones. */
public class ActionHubActivity extends Activity {
    private ZKit.Page page;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Consent.granted(this)) { Consent.require(this, false); return; }
        page = new ZKit.Page(this, Lang.t(S.MOD_HUB), Lang.t(S.MOD_HUB_D), true);
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
        final boolean on = Features.hub(a);
        final int mode = SideConfig.getMode(a);

        page.section(Lang.t(S.F_STATUS));
        LinearLayout st = page.card();
        page.toggle(st, Lang.t(S.HUB_EN), Lang.t(S.HUB_EN_D), on, v -> {
            Features.setHub(a, v);
            if (v && !Features.accessibilityOn()) Toast.makeText(a, Lang.t(S.F_NEEDS_SVC), Toast.LENGTH_LONG).show();
            render();
        });
        page.divider(st);
        page.status(st, Lang.t(S.F_SVC) + ": " + (Features.accessibilityOn() ? Lang.t(S.ST_ON) : Lang.t(S.ST_OFF)), Features.accessibilityOn());
        if (on && !Features.accessibilityOn()) {
            st.addView(page.button(Lang.t(S.F_PERMS_GO), true, () -> startActivity(new Intent(a, PermissionCenterActivity.class))),
                ZKit.lp(a, -1, -2, 0f, 0, 4, 0, 10));
        }

        if (!on) {
            page.guide(Lang.t(S.F_GUIDE), Lang.t(S.HUB_GUIDE));
            return;
        }

        // ---- modo
        page.section(Lang.t(S.HUB_MODE));
        LinearLayout md = page.card();
        page.chips(md, null, new String[]{Lang.t(S.HUB_BUBBLE), Lang.t(S.HUB_GESTURE)},
            mode == SideConfig.MODE_GESTURE ? 1 : 0, i -> {
                if (i == 1 && !SideConfig.threeButtonNav(a)) {
                    Toast.makeText(a, Lang.t(S.HUB_GESTURE_N), Toast.LENGTH_LONG).show();
                } else {
                    Features.setHubMode(a, i == 1 ? SideConfig.MODE_GESTURE : SideConfig.MODE_BUBBLE);
                }
                render();
            });
        page.note(md, Lang.t(S.HUB_GESTURE_N), ZKit.SUB);

        // ---- apariencia y comportamiento
        page.section(Lang.t(S.F_SETTINGS));
        LinearLayout ap = page.card();
        page.slider(ap, Lang.t(S.HUB_SIZE), 32, 72, " dp", () -> SideConfig.get(a, "bubbleSize"), v -> SideConfig.set(a, "bubbleSize", v));
        page.slider(ap, Lang.t(S.HUB_ALPHA), 30, 100, " %", () -> SideConfig.get(a, "bubbleAlpha"), v -> SideConfig.set(a, "bubbleAlpha", v));
        page.divider(ap);
        page.toggle(ap, Lang.t(S.HUB_NOTES), null, SideConfig.get(a, "noteOn") == 1, v -> SideConfig.set(a, "noteOn", v ? 1 : 0));
        page.divider(ap);
        page.toggle(ap, Lang.t(S.HUB_FLOAT), null, SideConfig.get(a, "floatWin") == 1, v -> SideConfig.set(a, "floatWin", v ? 1 : 0));

        // ---- acciones
        LinearLayout ac = page.card();
        page.nav(ac, Lang.t(S.HUB_OPEN), null, () -> {
            SideHub h = Features.sideHub();
            if (h != null) { h.open(); moveTaskToBack(true); }
            else Toast.makeText(a, Lang.t(S.HUB_NOT_READY), Toast.LENGTH_SHORT).show();
        });
        page.divider(ac);
        page.nav(ac, Lang.t(S.HUB_MOVE), null, () -> {
            SideHub h = Features.sideHub();
            if (h != null && SideConfig.getMode(a) == SideConfig.MODE_BUBBLE) { h.enterCanvas(); moveTaskToBack(true); }
            else Toast.makeText(a, Lang.t(S.HUB_NOT_READY), Toast.LENGTH_SHORT).show();
        });
        page.divider(ac);
        page.nav(ac, Lang.t(S.HUB_RESET), null, () -> {
            SideConfig.resetPos(a);
            Toast.makeText(a, Lang.t(S.T_DONE), Toast.LENGTH_SHORT).show();
        });

        // ---- avanzado
        page.section(Lang.t(S.F_ADV));
        LinearLayout adv = page.card();
        page.nav(adv, Lang.t(S.F_ADV), Lang.t(S.F_ADV_D), () -> {
            SettingsTree.Node n = SettingsTree.find("personalizacion/lateral");
            if (n != null) SettingsPageActivity.open(a, n);
        });

        page.guide(Lang.t(S.F_GUIDE), Lang.t(S.HUB_GUIDE));
    }
}
