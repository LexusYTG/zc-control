package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * Configuración inicial (se muestra una sola vez, después del consentimiento): idioma → paquete completo o manual →
 * (manual) qué módulos → resumen. Los idiomas salen de {@link Lang#languages()}, así que agregar uno nuevo
 * al archivo de textos lo suma acá sin tocar nada.
 */
public class SetupActivity extends Activity {
    private static final int P_LANG = 0, P_MODE = 1, P_PICK = 2, P_SUMMARY = 3;

    private ZKit.Page page;
    private int step;                 // índice dentro de steps()
    private Boolean full;             // null = todavía no eligió
    private boolean cc = true, hub = true, game = true, launcher = false;
    private boolean applying;

    private final Shizuku.OnRequestPermissionResultListener shizukuListener = (code, result) -> runOnUiThread(this::render);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Consent.granted(this)) { Consent.require(this, false); return; }
        if (Features.setupDone(this)) { go(); return; }
        page = new ZKit.Page(this, null, null, false);
        page.show();
        try { Shizuku.addRequestPermissionResultListener(shizukuListener); } catch (Throwable e) { }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page != null) render();   // por si volvió de activar Shizuku
    }

    private List<Integer> steps() {
        List<Integer> s = new ArrayList<>();
        s.add(P_LANG);
        s.add(P_MODE);
        if (full != null && !full) s.add(P_PICK);
        s.add(P_SUMMARY);
        return s;
    }

    private void render() {
        if (page == null || applying) return;
        final Activity a = this;
        page.clear();
        List<Integer> steps = steps();
        if (step >= steps.size()) step = steps.size() - 1;
        int cur = steps.get(step);

        TextView tag = ZUi.text(a, String.format(Lang.t(S.SU_STEP), step + 1, steps.size()), 12f, ZKit.ACC, true);
        tag.setLetterSpacing(0.08f);
        page.content.addView(tag);

        switch (cur) {
            case P_LANG: renderLang(); break;
            case P_MODE: renderMode(); break;
            case P_PICK: renderPick(); break;
            default: renderSummary(); break;
        }
        renderNav(cur, steps.size());
    }

    private void head(String title, String sub) {
        TextView t = ZUi.text(this, title, 26f, ZKit.TXT, true);
        t.setPadding(0, ZKit.dp(this, 10), 0, 0);
        page.content.addView(t);
        if (sub != null) {
            TextView s = ZUi.text(this, sub, 14f, ZKit.SUB, false);
            s.setLineSpacing(0f, 1.15f);
            s.setPadding(0, ZKit.dp(this, 6), 0, ZKit.dp(this, 14));
            page.content.addView(s);
        }
    }

    /** Tarjeta grande elegible (idioma, paquete). */
    private void choice(String title, String sub, boolean selected, Runnable pick) {
        final Activity a = this;
        LinearLayout c = ZUi.box(a, LinearLayout.VERTICAL);
        c.setPadding(ZKit.dp(a, 18), ZKit.dp(a, 16), ZKit.dp(a, 18), ZKit.dp(a, 16));
        android.graphics.drawable.GradientDrawable g = ZUi.shape(a, selected ? ZKit.CARD_HI : ZKit.CARD, 20);
        if (selected) g.setStroke(ZKit.dp(a, 2), ZKit.ACC);
        c.setBackground(g);
        c.setClickable(true);
        c.setOnClickListener(v -> pick.run());
        c.addView(ZUi.text(a, (selected ? "●  " : "○  ") + title, 17f, ZKit.TXT, true));
        if (sub != null) {
            TextView s = ZUi.text(a, sub, 13f, ZKit.SUB, false);
            s.setLineSpacing(0f, 1.15f);
            s.setPadding(0, ZKit.dp(a, 6), 0, 0);
            c.addView(s);
        }
        page.content.addView(c, ZKit.lp(a, -1, -2, 0f, 0, 0, 0, 10));
    }

    private void renderLang() {
        head(Lang.t(S.SU_LANG_T), Lang.t(S.SU_LANG_D));
        for (final String[] l : Lang.languages()) {
            choice(l[0], null, l[1].equals(Lang.currentId()), () -> {
                if (!l[1].equals(Lang.currentId())) Lang.setLanguage(this, l[1]);
                render();
            });
        }
    }

    private void renderMode() {
        head(Lang.t(S.SU_MODE_T), null);
        choice(Lang.t(S.SU_FULL), Lang.t(S.SU_FULL_D) + "\n\n" + Lang.t(S.SU_FULL_N), full != null && full, () -> { full = true; render(); });
        choice(Lang.t(S.SU_MANUAL), Lang.t(S.SU_MANUAL_D), full != null && !full, () -> { full = false; render(); });
    }

    private void renderPick() {
        head(Lang.t(S.SU_PICK_T), Lang.t(S.SU_PICK_D));
        LinearLayout card = page.card();
        page.toggle(card, Lang.t(S.MOD_CC), Lang.t(S.MOD_CC_D), cc, v -> cc = v);
        page.divider(card);
        page.toggle(card, Lang.t(S.MOD_HUB), Lang.t(S.MOD_HUB_D), hub, v -> hub = v);
        page.divider(card);
        page.toggle(card, Lang.t(S.MOD_GAME), Lang.t(S.MOD_GAME_D), game, v -> game = v);
        page.divider(card);
        page.toggle(card, Lang.t(S.MOD_LA), Lang.t(S.MOD_LA_D), launcher, v -> launcher = v);
    }

    private void renderSummary() {
        final Activity a = this;
        head(Lang.t(S.SU_SUM_T), Lang.t(S.SU_SUM_D));
        boolean f = full != null && full;
        LinearLayout card = page.card();
        card.setPadding(ZKit.dp(a, 16), ZKit.dp(a, 12), ZKit.dp(a, 16), ZKit.dp(a, 12));
        boolean any = false;
        if (f || cc) { line(card, Lang.t(S.MOD_CC)); any = true; }
        if (f || hub) { line(card, Lang.t(S.MOD_HUB)); any = true; }
        if (f || game) { line(card, Lang.t(S.MOD_GAME)); any = true; }
        if (f || launcher) { line(card, Lang.t(S.MOD_LA)); any = true; }
        if (!any) page.note(card, Lang.t(S.SU_SUM_NONE), ZKit.SUB);
        if (f) page.note(card, Lang.t(S.SU_FULL_N), ZKit.SUB);

        // Shizuku: estado y, si hace falta, pedir el permiso
        boolean needsShz = f || cc || hub || game;
        if (needsShz) {
            LinearLayout sc = page.card();
            sc.setPadding(ZKit.dp(a, 16), ZKit.dp(a, 12), ZKit.dp(a, 16), ZKit.dp(a, 12));
            boolean ok = Privileged.available();
            page.status(sc, ok ? Lang.t(S.SU_SHZ_OK) : Lang.t(S.SU_SHZ_NO), ok);
            boolean binder = false;
            try { binder = Shizuku.pingBinder(); } catch (Throwable e) { }
            if (!ok && binder) {
                sc.addView(page.button(Lang.t(S.SU_SHZ_ASK), false, () -> {
                    try { Shizuku.requestPermission(2); } catch (Throwable e) { }
                }), ZKit.lp(a, -1, -2, 0f, 0, 4, 0, 10));
            } else if (!ok) {
                sc.addView(page.button(Lang.t(520), false, () -> ShizukuNotice.show(a)), ZKit.lp(a, -1, -2, 0f, 0, 4, 0, 10));
            }
        }
    }

    private void line(LinearLayout card, String s) {
        TextView t = ZUi.text(this, "✓  " + s, 15f, ZKit.TXT, true);
        t.setPadding(0, ZKit.dp(this, 6), 0, ZKit.dp(this, 6));
        card.addView(t);
    }

    private void renderNav(int cur, int total) {
        final Activity a = this;
        boolean last = step == total - 1;
        boolean canNext = cur != P_MODE || full != null;
        LinearLayout nav = ZUi.box(a, LinearLayout.HORIZONTAL);
        if (step > 0) {
            nav.addView(page.button(Lang.t(S.PREV), false, () -> { step--; render(); }), ZKit.lp(a, 0, -2, 1f, 0, 0, 6, 0));
        }
        TextView next = page.button(last ? Lang.t(S.FINISH) : Lang.t(S.NEXT), true, () -> {
            if (!canNext) return;
            if (last) finishSetup(); else { step++; render(); }
        });
        next.setAlpha(canNext ? 1f : 0.4f);
        nav.addView(next, ZKit.lp(a, 0, -2, 2f, step > 0 ? 6 : 0, 0, 0, 0));
        page.content.addView(nav, ZKit.lp(a, -1, -2, 0f, 0, 18, 0, 0));
    }

    private void finishSetup() {
        if (applying) return;
        boolean f = full != null && full;
        applying = true;
        page.clear();
        TextView t = ZUi.text(this, Lang.t(S.SU_APPLYING), 18f, ZKit.TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, ZKit.dp(this, 120), 0, 0);
        page.content.addView(t, new LinearLayout.LayoutParams(-1, -2));
        Features.apply(this, f, f || cc, f || hub, f || game, f || launcher, this::go);
    }

    private void go() {
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (applying) return;
        if (step > 0) { step--; render(); }
        else moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener); } catch (Throwable e) { }
        super.onDestroy();
    }
}
