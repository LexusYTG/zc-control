package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * Pantalla principal («Ajustes»): buscador, las secciones de {@link SettingsTree} y, abajo, un botón que abre los
 * ajustes del sistema. Los permisos y el estado del servicio viven en Administración > Permisos.
 */
public class SettingsHomeActivity extends Activity {
    private LinearLayout tree, results, banner;
    private TextView bannerText;
    private boolean asked;

    private final Shizuku.OnRequestPermissionResultListener shizukuListener = (code, result) -> {
        if (result == PackageManager.PERMISSION_GRANTED) SetupHelper.autoGrant(this, this::refreshBanner);
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Consent.granted(this)) { Consent.require(this, false); return; }
        LinearLayout root = ZUi.box(this, LinearLayout.VERTICAL);
        root.setPadding(ZUi.dp(this, 20), ZUi.dp(this, 28), ZUi.dp(this, 20), ZUi.dp(this, 32));

        root.addView(ZUi.text(this, Lang.t(240), 30f, ZUi.TXT, true));
        TextView sub = ZUi.text(this, Lang.t(511), 14f, ZUi.SUB, false);
        sub.setPadding(0, ZUi.dp(this, 4), 0, ZUi.dp(this, 16));
        root.addView(sub);

        // aviso de permisos (solo si falta algo)
        banner = ZUi.box(this, LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(ZUi.dp(this, 16), ZUi.dp(this, 14), ZUi.dp(this, 16), ZUi.dp(this, 14));
        banner.setBackground(ZUi.shape(this, 0x22FBBF24, 16));
        bannerText = ZUi.text(this, Lang.t(512), 14f, ZUi.WARN, true);
        banner.addView(bannerText, new LinearLayout.LayoutParams(0, -2, 1f));
        banner.addView(ZUi.text(this, "›", 22f, ZUi.WARN, false));
        banner.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.bottomMargin = ZUi.dp(this, 14);
        root.addView(banner, bp);

        // buscador
        final EditText search = new EditText(this);
        search.setHint(Lang.t(513));
        search.setSingleLine(true);
        search.setTextColor(ZUi.TXT);
        search.setHintTextColor(ZUi.SUB);
        search.setTextSize(15f);
        search.setPadding(ZUi.dp(this, 16), ZUi.dp(this, 12), ZUi.dp(this, 16), ZUi.dp(this, 12));
        search.setBackground(ZUi.shape(this, ZUi.CARD, 16));
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));

        // resultados de búsqueda (ocultos hasta escribir)
        results = ZUi.box(this, LinearLayout.VERTICAL);
        results.setVisibility(View.GONE);
        root.addView(results);

        // árbol: cada sección de primer nivel y, debajo, sus hijos en el orden del árbol
        tree = ZUi.box(this, LinearLayout.VERTICAL);
        for (SettingsTree.Node section : SettingsTree.ROOT.kids) {
            tree.addView(ZUi.sectionLabel(this, section.title));
            LinearLayout card = ZUi.box(this, LinearLayout.VERTICAL);
            card.setBackground(ZUi.shape(this, ZUi.CARD, 20));
            for (int i = 0; i < section.kids.size(); i++) {
                final SettingsTree.Node n = section.kids.get(i);
                if (i > 0) card.addView(ZUi.divider(this), new LinearLayout.LayoutParams(-1, 1));
                card.addView(ZUi.row(this, null, n.title, null, false, v -> openNode(n)));
            }
            tree.addView(card);
        }
        root.addView(tree);

        TextView lang = ZUi.text(this, "🌐  " + Lang.t(872), 15f, ZUi.TXT, true);
        lang.setGravity(Gravity.CENTER);
        lang.setPadding(ZUi.dp(this, 12), ZUi.dp(this, 16), ZUi.dp(this, 12), ZUi.dp(this, 16));
        lang.setBackground(ZUi.shape(this, ZUi.CARD, 16));
        lang.setOnClickListener(v -> Lang.pick(this));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, -2);
        lp2.topMargin = ZUi.dp(this, 20);
        root.addView(lang, lp2);

        // botón al Ajustes del sistema
        TextView sys = ZUi.text(this, Lang.t(514), 15f, ZUi.TXT, true);
        sys.setGravity(Gravity.CENTER);
        sys.setPadding(ZUi.dp(this, 12), ZUi.dp(this, 16), ZUi.dp(this, 12), ZUi.dp(this, 16));
        sys.setBackground(ZUi.shape(this, ZUi.CARD_HI, 16));
        sys.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Throwable e) { Toast.makeText(this, Lang.t(515), Toast.LENGTH_SHORT).show(); }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = ZUi.dp(this, 28);
        root.addView(sys, sp);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { showResults(s.toString()); }
            @Override public void afterTextChanged(Editable e) { }
        });

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(ZUi.BG);
        sv.addView(root);
        sv.setClipToPadding(false);
        sv.setFillViewport(true);
        Insets.apply(sv);
        setContentView(sv);

        if (!Privileged.available()) ShizukuNotice.showOnce(this);
        try { Shizuku.addRequestPermissionResultListener(shizukuListener); } catch (Throwable e) { }
    }

    /** Abre un nodo: acción → su pantalla; rama u hoja suelta → su página. */
    private void openNode(SettingsTree.Node n) {
        if (n.isAction()) n.target.open(this);
        else SettingsPageActivity.open(this, n);
    }

    /** Un resultado de búsqueda abre la página que contiene el ajuste (las hojas de una sección, ellas mismas). */
    private void openHit(SettingsTree.Node n) {
        if (n.isLeaf() && n.parent != null && n.parent.parent != null && n.parent.parent.parent != null) openNode(n.parent);
        else openNode(n);
    }

    private void showResults(String q) {
        results.removeAllViews();
        boolean searching = !q.trim().isEmpty();
        tree.setVisibility(searching ? View.GONE : View.VISIBLE);
        results.setVisibility(searching ? View.VISIBLE : View.GONE);
        if (!searching) return;
        List<SettingsTree.Hit> hits = SettingsTree.search(q);
        results.addView(ZUi.sectionLabel(this, hits.isEmpty() ? Lang.t(516) : Lang.t(517)));
        if (hits.isEmpty()) return;
        LinearLayout card = ZUi.box(this, LinearLayout.VERTICAL);
        card.setBackground(ZUi.shape(this, ZUi.CARD, 20));
        for (int i = 0; i < hits.size(); i++) {
            final SettingsTree.Node n = hits.get(i).node;
            if (i > 0) card.addView(ZUi.divider(this), new LinearLayout.LayoutParams(-1, 1));
            String where = n.crumbs();
            card.addView(ZUi.row(this, null, n.title, where.isEmpty() ? null : where, false, v -> openHit(n)));
        }
        results.addView(card);
    }

    private void refreshBanner() {
        if (banner != null) banner.setVisibility(SetupHelper.allDone(this) ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Consent.granted(this)) return;
        refreshBanner();
        // Con Shizuku corriendo, todo se configura solo.
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    if (!SetupHelper.allDone(this)) SetupHelper.autoGrant(this, this::refreshBanner);
                } else if (!asked) { asked = true; Shizuku.requestPermission(1); }
            }
        } catch (Throwable e) { }
    }

    @Override
    protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener); } catch (Throwable e) { }
        super.onDestroy();
    }
}
