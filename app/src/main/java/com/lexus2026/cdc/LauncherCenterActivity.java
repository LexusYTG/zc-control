package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Toast;

/** ZC Launcher: usarlo (o no) como pantalla de inicio y las opciones de uso diario; lo fino queda en «Categorías y radial». */
public class LauncherCenterActivity extends Activity {
    private ZKit.Page page;
    private LauncherSettings st;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Consent.granted(this)) { Consent.require(this, false); return; }
        st = new LauncherSettings(this);
        page = new ZKit.Page(this, Lang.t(S.MOD_LA), Lang.t(S.MOD_LA_D), true);
        page.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page != null) render();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (page != null) render();
    }

    private void render() {
        final Activity a = this;
        page.clear();
        final boolean home = Features.launcherIsHome(a);

        page.section(Lang.t(S.F_STATUS));
        LinearLayout sc = page.card();
        page.toggle(sc, Lang.t(S.LA_EN), Lang.t(S.LA_EN_D), home, v -> {
            if (v == Features.launcherIsHome(a)) return;
            // Con Shizuku el cambio tarda un instante en verse; sin Shizuku lo confirma el usuario y se refresca al volver.
            Features.setLauncher(a, v, () -> new Handler(Looper.getMainLooper()).postDelayed(this::render, 900));
        });
        page.divider(sc);
        page.status(sc, String.format(Lang.t(S.LA_CUR), Features.homeLabel(a)), home);
        if (!Privileged.available()) page.note(sc, Lang.t(S.LA_NO_SHZ), ZKit.SUB);

        LinearLayout go = page.card();
        page.nav(go, Lang.t(S.LA_OPEN), null, () -> startActivity(new Intent(a, LauncherActivity.class)));
        page.divider(go);
        page.nav(go, Lang.t(S.LA_PICK), null, () -> {
            try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
            catch (Throwable e) { Toast.makeText(a, Lang.t(515), Toast.LENGTH_SHORT).show(); }
        });

        // ---- uso diario
        page.section(Lang.t(S.F_SETTINGS));
        LinearLayout op = page.card();
        page.chips(op, Lang.t(S.LA_SHAPE),
            new String[]{Lang.t(S.LA_SH0), Lang.t(S.LA_SH1), Lang.t(S.LA_SH2), Lang.t(S.LA_SH3)},
            Math.max(0, Math.min(3, st.get(LauncherSettings.SHAPE))), i -> {
                st.set(LauncherSettings.SHAPE, i);
                render();
            });
        page.slider(op, Lang.t(S.LA_SIZE), 24, 96, " dp", () -> st.get(LauncherSettings.BUBBLE), v -> st.set(LauncherSettings.BUBBLE, v));
        page.slider(op, Lang.t(S.LA_ANIM), 80, 600, " ms", () -> st.get(LauncherSettings.ANIM), v -> st.set(LauncherSettings.ANIM, v));
        page.slider(op, Lang.t(S.LA_DIM), 0, 80, " %", () -> st.get(LauncherSettings.DIM), v -> st.set(LauncherSettings.DIM, v));
        page.divider(op);
        page.toggle(op, Lang.t(S.LA_DESK), null, st.get(LauncherSettings.DESKTOP) == 1, v -> st.set(LauncherSettings.DESKTOP, v ? 1 : 0));
        page.chips(op, Lang.t(S.LA_NEWAPPS), new String[]{Lang.t(S.LA_NEW0), Lang.t(S.LA_NEW1)},
            st.get(LauncherSettings.NEW_MODE) == 1 ? 1 : 0, i -> {
                st.set(LauncherSettings.NEW_MODE, i);
                render();
            });

        // ---- constructor visual
        page.section(SL.t(a, "Constructor visual", "Visual builder"));
        LinearLayout vb = page.card();
        page.nav(vb, SL.t(a, "Personalizar todo el launcher", "Customize the whole launcher"),
            SL.t(a, "Vista previa en vivo, paneles, botones, reglas y macros", "Live preview, panels, buttons, rules and macros"),
            () -> startActivity(new Intent(a, LauncherBuilderActivity.class)));

        // ---- avanzado
        page.section(Lang.t(S.F_ADV));
        LinearLayout adv = page.card();
        page.nav(adv, Lang.t(S.LA_ADV), Lang.t(S.LA_ADV_D), () -> startActivity(new Intent(a, LauncherSettingsActivity.class)));

        page.guide(Lang.t(S.F_GUIDE), Lang.t(S.LA_GUIDE));
    }
}
