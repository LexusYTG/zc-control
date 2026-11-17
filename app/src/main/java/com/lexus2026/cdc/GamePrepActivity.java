package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class GamePrepActivity extends Activity implements GameSession.Listener {
    private String pkg;
    private GameSession session;
    private boolean done = false;
    private ProgressBar bar;
    private TextView pctView;
    private TextView phase;
    private TextView status;
    private TextView badge;
    private LinearLayout procs;
    private LinearLayout actions;
    private final Map<String, TextView> procBadges = new HashMap<String, TextView>();
    private final TextView[] marks = new TextView[GameSession.STEPS.length];
    private final TextView[] texts = new TextView[GameSession.STEPS.length];
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int dp(float v) {
        return GcUi.dp(this, v);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pkg = getIntent().getStringExtra("pkg");
        if (pkg == null || b != null) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        GameWatchdog.prepActive(true);
        String label = pkg;
        Drawable icon = null;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            label = pm.getApplicationLabel(ai).toString();
            icon = pm.getApplicationIcon(ai);
        } catch (Exception e) {
        }

        LinearLayout root = GcUi.col(this);
        root.setBackgroundColor(GcUi.BG);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        LinearLayout top = GcUi.row(this);
        top.addView(GcUi.tv(this, "ZCTRL · GAME CONTROL", 11, GcUi.SUB, true), new LinearLayout.LayoutParams(0, GcUi.WRAP, 1f));
        badge = GcUi.badge(this, "PREPARANDO", GcUi.GREEN);
        top.addView(badge);
        root.addView(top);

        ImageView iv = new ImageView(this);
        if (icon != null) iv.setImageDrawable(icon);
        iv.setBackground(GcUi.round(this, 0xFFFFFFFF, 20));
        iv.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(92), dp(92));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        ilp.topMargin = dp(28);
        root.addView(iv, ilp);

        TextView name = GcUi.tv(this, label, 26, GcUi.TEXT, true);
        name.setGravity(Gravity.CENTER);
        root.addView(name, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 12));
        status = GcUi.tv(this, "Preparando entorno de juego…", 13, GcUi.SUB, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 4));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(GcUi.GREEN));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(GcUi.BOX));
        root.addView(bar, GcUi.lp(this, GcUi.MATCH, dp(8), 0f, 22));
        LinearLayout pr = GcUi.row(this);
        phase = GcUi.tv(this, "Iniciando…", 12, GcUi.SUB, false);
        pr.addView(phase, new LinearLayout.LayoutParams(0, GcUi.WRAP, 1f));
        pctView = GcUi.tv(this, "0 %", 12, GcUi.TEXT, false);
        pr.addView(pctView);
        root.addView(pr, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 6));
        root.addView(GcUi.line(this), GcUi.lp(this, GcUi.MATCH, dp(1), 0f, 14));

        root.addView(GcUi.tv(this, "Procesos detectados", 17, GcUi.TEXT, true), GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 16));
        procs = GcUi.col(this);
        procs.addView(GcUi.tv(this, "Esperando lectura…", 13, GcUi.GRAY, false));
        root.addView(procs, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 8));
        root.addView(GcUi.line(this), GcUi.lp(this, GcUi.MATCH, dp(1), 0f, 14));

        root.addView(GcUi.tv(this, "Preparación", 17, GcUi.TEXT, true), GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 16));
        for (int i = 0; i < GameSession.STEPS.length; i++) {
            LinearLayout r = GcUi.row(this);
            marks[i] = GcUi.tv(this, "●", 15, GcUi.GRAY, true);
            marks[i].setGravity(Gravity.CENTER);
            r.addView(marks[i], new LinearLayout.LayoutParams(dp(26), GcUi.WRAP));
            texts[i] = GcUi.tv(this, GameSession.STEPS[i], 14, GcUi.GRAY, false);
            r.addView(texts[i], new LinearLayout.LayoutParams(0, GcUi.WRAP, 1f));
            root.addView(r, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 10));
        }
        TextView foot = GcUi.tv(this, "No cierres esta pantalla mientras se prepara el dispositivo.", 12, GcUi.SUB, false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 18));
        actions = GcUi.col(this);
        root.addView(actions, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 8));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(GcUi.BG);
        sv.addView(root);
        Insets.apply(sv);
        setContentView(sv);

        session = new GameSession(this, pkg, this);
        new Thread(session).start();
    }

    @Override
    public void step(int i, int s, String dtl) {
        String m;
        int col;
        switch (s) {
            case GameSession.DONE: m = "✔"; col = GcUi.GREEN; break;
            case GameSession.RUNNING: m = "◌"; col = 0xFFFBBF24; break;
            case GameSession.FAILED: m = "✖"; col = GcUi.RED; break;
            case GameSession.SKIPPED: m = "–"; col = GcUi.GRAY; break;
            default: m = "●"; col = GcUi.GRAY; break;
        }
        marks[i].setText(m);
        marks[i].setTextColor(col);
        texts[i].setText(GameSession.STEPS[i] + (dtl == null || dtl.isEmpty() ? "" : " · " + dtl));
        texts[i].setTextColor(s == GameSession.PENDING ? GcUi.GRAY : 0xFFE5E7EB);
    }

    @Override
    public void progress(int p, String label) {
        bar.setProgress(p);
        pctView.setText(p + " %");
        phase.setText(label);
    }

    @Override
    public void proc(String p, int s) {
        if (procBadges.isEmpty()) procs.removeAllViews();
        TextView bd = procBadges.get(p);
        if (bd == null) {
            LinearLayout r = GcUi.row(this);
            r.addView(GcUi.tv(this, p, 14, GcUi.TEXT, false), new LinearLayout.LayoutParams(0, GcUi.WRAP, 1f));
            bd = GcUi.badge(this, "", GcUi.GRAY);
            r.addView(bd);
            procs.addView(r, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 8));
            procBadges.put(p, bd);
        }
        switch (s) {
            case GameSession.RUNNING: GcUi.paint(bd, "Procesando", GcUi.ORANGE); break;
            case GameSession.DONE: GcUi.paint(bd, "Finalizado", GcUi.GREEN); break;
            case GameSession.SKIPPED: GcUi.paint(bd, "Omitido", GcUi.GRAY); break;
            case GameSession.FAILED: GcUi.paint(bd, "Error", GcUi.RED); break;
            default: GcUi.paint(bd, "En cola", GcUi.GRAY); break;
        }
    }

    @Override
    public void finished(boolean ok, String msg) {
        if (isFinishing()) return;
        if (ok) {
            GcUi.paint(badge, "LISTO", GcUi.GREEN);
            go();
        } else {
            GcUi.paint(badge, "ERROR", GcUi.RED);
            status.setText(msg);
            showActions();
        }
    }

    private void go() {
        progress(100, "Iniciando juego…");
        step(GameSession.S_LAUNCH, GameSession.RUNNING, "");
        ui.postDelayed(() -> {
            if (isFinishing()) return;
            Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
            if (li == null) {
                GameWatchdog.endAsync(this);
                step(GameSession.S_LAUNCH, GameSession.FAILED, "");
                GcUi.paint(badge, "ERROR", GcUi.RED);
                status.setText("No se pudo abrir el juego");
                showActions();
                return;
            }
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(li);
            GameWatchdog.markLaunched(this, pkg, session.profile().panel);
            done = true;
            finish();
        }, 500);
    }

    @Override
    protected void onDestroy() {
        GameWatchdog.prepActive(false);
        super.onDestroy();
    }

    private void showActions() {
        actions.removeAllViews();
        Button a = new Button(this);
        a.setText("Iniciar sin optimizar");
        a.setAllCaps(false);
        a.setOnClickListener(v -> {
            GameWatchdog.bypass(pkg);
            GameWatchdog.endAsync(this);
            Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
            if (li != null) {
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(li);
            }
            done = true;
            finish();
        });
        Button c = new Button(this);
        c.setText("Cerrar");
        c.setAllCaps(false);
        c.setOnClickListener(v -> { GameWatchdog.bypass(pkg); finish(); });
        actions.addView(a);
        actions.addView(c);
    }

    @Override
    public void onBackPressed() {
        if (!done) GameWatchdog.bypass(pkg);
        if (!done && session != null) session.cancel();
        super.onBackPressed();
    }
}
