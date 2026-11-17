package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class GameProfileActivity extends Activity {
    private String pkg;
    private GameSettings.Profile p;
    private int bw;
    private int bh;
    private int bdpi;
    private TextView pctLabel;
    private TextView preview;
    private TextView modeBtn;

    private int dp(float v) {
        return GcUi.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pkg = getIntent().getStringExtra("pkg");
        p = pkg == null ? GameSettings.defaults(this) : GameSettings.profile(this, pkg);

        Point pt = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(pt);
        bw = Math.min(pt.x, pt.y);
        bh = Math.max(pt.x, pt.y);
        bdpi = getResources().getDisplayMetrics().densityDpi;
        GameWatchdog.Orig o = GameWatchdog.orig(this);
        if (o != null) {
            bw = o.w;
            bh = o.h;
            bdpi = o.dpi;
        }

        String title = "Configuración general";
        String sub = "Se aplica a los juegos sin perfil propio";
        if (pkg != null) {
            try {
                PackageManager pm = getPackageManager();
                title = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (Exception e) {
                title = pkg;
            }
            sub = pkg + (GameSettings.hasProfile(this, pkg) ? "" : " · usando la configuración general");
        }

        LinearLayout root = GcUi.col(this);
        root.setBackgroundColor(GcUi.BG);
        root.setPadding(dp(16), dp(20), dp(16), dp(24));
        root.addView(GcUi.tv(this, title, 22, GcUi.TEXT, true));
        root.addView(GcUi.tv(this, sub, 12, GcUi.SUB, false), GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 4));

        LinearLayout card = GcUi.col(this);
        card.setBackground(GcUi.round(this, GcUi.CARD, 18));
        card.setPadding(dp(16), dp(8), dp(16), dp(12));

        sw(card, "Limpiar procesos antes de jugar", null, p.procs, v -> p.procs = v);

        LinearLayout rb = GcUi.col(this);
        rb.setPadding(0, dp(12), 0, dp(12));
        pctLabel = GcUi.tv(this, "", 15, GcUi.TEXT, false);
        rb.addView(pctLabel);
        SeekBar sk = new SeekBar(this);
        sk.setMax(10);
        sk.setProgress(Math.max(0, Math.min(10, (p.pct - 50) / 5)));
        sk.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                p.pct = 50 + v * 5;
                updatePct();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        rb.addView(sk, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 6));
        preview = GcUi.tv(this, "", 12, GcUi.SUB, false);
        rb.addView(preview);
        card.addView(rb);
        card.addView(GcUi.line(this));
        updatePct();

        sw(card, "Compilar con ART antes de jugar", "Solo cuando el juego es nuevo o se actualizó", p.compile, v -> p.compile = v);
        sw(card, "Panel flotante in-game", "Burbuja en el lateral izquierdo", p.panel, v -> p.panel = v);
        sw(card, "Modo de ahorro de batería", null, p.saver, v -> p.saver = v);
        sw(card, "No mantener actividades", "Temporal durante la sesión", p.noKeep, v -> p.noKeep = v);
        sw(card, "Sin procesos en segundo plano", "Temporal durante la sesión", p.bgLimit, v -> p.bgLimit = v);

        if (pkg == null) {
            modeBtn = GcUi.tv(this, "", 15, GcUi.GREEN, true);
            modeBtn.setPadding(0, dp(14), 0, dp(6));
            modeBtn.setOnClickListener(v -> pickMode());
            card.addView(modeBtn);
            updateMode();
            TextView ex = GcUi.tv(this, "Apps que nunca se cierran…", 15, GcUi.GREEN, true);
            ex.setPadding(0, dp(10), 0, dp(6));
            ex.setOnClickListener(v -> exceptions());
            card.addView(ex);
        }
        root.addView(card, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 16));

        Button save = btn("Guardar", v -> save());
        root.addView(save, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 16));
        if (pkg != null) {
            Button rs = btn("Usar la configuración general", v -> {
                GameSettings.resetProfile(this, pkg);
                Toast.makeText(this, "Perfil eliminado", Toast.LENGTH_SHORT).show();
                finish();
            });
            root.addView(rs);
        }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(GcUi.BG);
        sv.addView(root);
        Insets.apply(sv);
        setContentView(sv);
    }

    private Button btn(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void sw(LinearLayout card, String label, String hint, boolean v, final Consumer<Boolean> set) {
        LinearLayout r = GcUi.row(this);
        r.setPadding(0, dp(12), 0, dp(12));
        LinearLayout t = GcUi.col(this);
        t.addView(GcUi.tv(this, label, 15, GcUi.TEXT, false));
        if (hint != null) t.addView(GcUi.tv(this, hint, 12, GcUi.SUB, false));
        r.addView(t, new LinearLayout.LayoutParams(0, GcUi.WRAP, 1f));
        Switch s = new Switch(this);
        s.setChecked(v);
        s.setOnCheckedChangeListener((x, on) -> set.accept(on));
        r.addView(s);
        card.addView(r);
        card.addView(GcUi.line(this));
    }

    private void updatePct() {
        int[] s = GameOps.size(bw, bh, p.pct);
        pctLabel.setText("Resolución: " + p.pct + " %");
        preview.setText(s[0] + " × " + s[1] + " · " + GameOps.dpi(bdpi, p.pct) + " dpi");
    }

    private void updateMode() {
        modeBtn.setText("Modo de compilación: " + GameSettings.compileMode(this));
    }

    private void pickMode() {
        final String[] modes = {"speed-profile", "speed", "everything"};
        int cur = 1;
        String m = GameSettings.compileMode(this);
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(m)) cur = i;
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Modo de compilación")
            .setSingleChoiceItems(modes, cur, (d, which) -> {
                GameSettings.setCompileMode(this, modes[which]);
                updateMode();
                d.dismiss();
            })
            .show();
    }

    private void exceptions() {
        final PackageManager pm = getPackageManager();
        List<ApplicationInfo> all = new ArrayList<ApplicationInfo>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && !ai.packageName.equals(getPackageName())) all.add(ai);
        }
        Collections.sort(all, (x, y) -> pm.getApplicationLabel(x).toString().compareToIgnoreCase(pm.getApplicationLabel(y).toString()));
        final List<String> pk = new ArrayList<String>();
        final List<String> lb = new ArrayList<String>();
        Set<String> cur = GameSettings.exceptions(this);
        final boolean[] chk = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            pk.add(all.get(i).packageName);
            lb.add(pm.getApplicationLabel(all.get(i)).toString());
            chk[i] = cur.contains(all.get(i).packageName);
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Apps que nunca se cierran")
            .setMultiChoiceItems(lb.toArray(new String[0]), chk, (d, i, c) -> chk[i] = c)
            .setPositiveButton("Guardar", (d, w) -> {
                Set<String> n = new HashSet<String>();
                for (int i = 0; i < chk.length; i++) if (chk[i]) n.add(pk.get(i));
                GameSettings.setExceptions(this, n);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void save() {
        if (pkg == null) GameSettings.saveDefaults(this, p);
        else GameSettings.saveProfile(this, pkg, p);
        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show();
        finish();
    }
}
