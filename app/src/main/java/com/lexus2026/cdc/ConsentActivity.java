package com.lexus2026.cdc;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

/** Pantalla de inicio: sin consentimiento expreso la app no hace nada y se cierra sin dejar rastros. */
public class ConsentActivity extends Activity {
    private boolean home, decided;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        home = getIntent().getBooleanExtra("home", false);
        if (Consent.granted(this)) { go(); return; }

        LinearLayout root = ZUi.box(this, LinearLayout.VERTICAL);
        root.setBackgroundColor(ZUi.BG);

        LinearLayout body = ZUi.box(this, LinearLayout.VERTICAL);
        body.setPadding(ZUi.dp(this, 20), ZUi.dp(this, 24), ZUi.dp(this, 20), ZUi.dp(this, 16));
        body.addView(ZUi.text(this, Lang.t(247), 20f, ZUi.WARN, true));
        body.addView(p("Este software ha sido desarrollado y se mantiene bajo el marco de la legislación vigente de la República Argentina a la fecha de su publicación y mantenimiento.", 14));
        body.addView(p(Lang.t(248), 10));

        body.addView(h(Lang.t(249)));
        body.addView(p(Lang.t(250), 6));
        body.addView(p(Lang.t(251), 4));
        body.addView(p(Lang.t(252), 4));
        body.addView(p(Lang.t(253), 4));
        body.addView(p(Lang.t(254), 4));
        body.addView(p(Lang.t(255), 10));

        body.addView(h(Lang.t(256)));
        body.addView(p(Lang.t(257), 6));
        body.addView(p(Lang.t(258), 10));
        TextView decl = p(Lang.t(604), 10);
        decl.setTextColor(ZUi.ACC);
        decl.setTextIsSelectable(true);
        body.addView(decl);
        body.addView(p(Lang.t(259), 10));
        body.addView(p(Lang.t(260), 4));
        body.addView(p(Lang.t(605), 4));
        body.addView(p(Lang.t(261), 4));
        body.addView(p(Lang.t(262), 12));
        body.addView(p(Lang.t(263), 4));
        body.addView(p(Lang.t(264), 4));
        body.addView(p(Lang.t(265), 4));
        body.addView(p(Lang.t(266), 10));
        body.addView(p(Lang.t(267), 6));

        body.addView(h(Lang.t(268)));
        body.addView(p("En cumplimiento de las obligaciones aplicables en materia de protección de datos personales y transparencia sobre su tratamiento, solicitamos su consentimiento expreso antes de habilitar las funciones que requieran acceder a datos de uso.", 6));
        TextView q = ZUi.text(this, Lang.t(269), 15f, ZUi.TXT, true);
        q.setPadding(0, ZUi.dp(this, 14), 0, 0);
        body.addView(q);
        body.addView(p(Lang.t(270), 10));

        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = ZUi.box(this, LinearLayout.HORIZONTAL);
        bar.setPadding(ZUi.dp(this, 16), ZUi.dp(this, 12), ZUi.dp(this, 16), ZUi.dp(this, 16));
        bar.setBackgroundColor(ZUi.CARD);
        TextView no = btn(Lang.t(271), ZUi.CARD_HI, ZUi.TXT);
        no.setOnClickListener(v -> reject());
        TextView yes = btn(Lang.t(272), ZUi.OK, 0xFF06281C);
        yes.setOnClickListener(v -> accept());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = ZUi.dp(this, 8);
        bar.addView(no, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        lp2.leftMargin = ZUi.dp(this, 8);
        bar.addView(yes, lp2);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        Insets.apply(root);
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::reject);
        }
    }

    private TextView h(String s) {
        TextView t = ZUi.text(this, s, 17f, ZUi.TXT, true);
        t.setPadding(0, ZUi.dp(this, 22), 0, 0);
        return t;
    }

    private TextView p(String s, int topDp) {
        TextView t = ZUi.text(this, s, 14f, ZUi.SUB, false);
        t.setLineSpacing(0f, 1.15f);
        t.setPadding(0, ZUi.dp(this, topDp), 0, 0);
        return t;
    }

    private TextView btn(String s, int bg, int fg) {
        TextView t = ZUi.text(this, s, 15f, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(ZUi.dp(this, 12), ZUi.dp(this, 16), ZUi.dp(this, 12), ZUi.dp(this, 16));
        t.setBackground(ZUi.shape(this, bg, 16));
        return t;
    }

    private void accept() {
        if (decided) return;
        decided = true;
        Consent.accept(this);
        go();
    }

    private void go() {
        startActivity(new Intent(this, home ? LauncherActivity.class : SettingsHomeActivity.class));
        finish();
    }

    /** Sin consentimiento: se agradece, se cierra la tarea y se borran los datos propios de la app. */
    private void reject() {
        if (decided) return;
        decided = true;
        LinearLayout t = ZUi.box(this, LinearLayout.VERTICAL);
        t.setBackgroundColor(ZUi.BG);
        t.setGravity(Gravity.CENTER);
        t.setPadding(ZUi.dp(this, 28), ZUi.dp(this, 28), ZUi.dp(this, 28), ZUi.dp(this, 28));
        TextView a = ZUi.text(this, "Gracias por su comprensión", 22f, ZUi.TXT, true);
        a.setGravity(Gravity.CENTER);
        TextView c = ZUi.text(this, Lang.t(273), 14f, ZUi.SUB, false);
        c.setGravity(Gravity.CENTER);
        c.setPadding(0, ZUi.dp(this, 12), 0, 0);
        t.addView(a);
        t.addView(c);
        setContentView(t);
        new Handler(Looper.getMainLooper()).postDelayed(this::wipeAndClose, 2500);
    }

    private void wipeAndClose() {
        finishAndRemoveTask();
        boolean ok = false;
        try { ok = ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData(); } catch (Throwable e) { }
        if (!ok) android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override public void onBackPressed() { reject(); }
}
