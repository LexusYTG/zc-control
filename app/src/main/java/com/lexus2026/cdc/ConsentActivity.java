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
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

/** Pantalla de inicio: sin consentimiento expreso la app no hace nada y se cierra sin dejar rastros. */
public class ConsentActivity extends Activity {
    private boolean home, decided;
    private CheckBox c1, c2;
    private TextView yes;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        home = getIntent().getBooleanExtra("home", false);
        if (Consent.granted(this)) { go(); return; }

        LinearLayout root = ZUi.box(this, LinearLayout.VERTICAL);
        root.setBackgroundColor(ZUi.BG);

        LinearLayout body = ZUi.box(this, LinearLayout.VERTICAL);
        body.setPadding(ZUi.dp(this, 20), ZUi.dp(this, 24), ZUi.dp(this, 20), ZUi.dp(this, 16));
        body.addView(ZUi.text(this, Lang.t(1201), 20f, ZUi.WARN, true));
        body.addView(p(Lang.t(1202), 8));
        body.addView(p(Lang.t(1203), 10));
        body.addView(p(Lang.t(1204), 8));

        body.addView(h(Lang.t(1210)));
        body.addView(p(Lang.t(1211), 6));
        body.addView(p(Lang.t(1213), 4));
        body.addView(p(Lang.t(1214), 4));
        body.addView(p(Lang.t(1215), 4));
        body.addView(p(Lang.t(1216), 8));

        body.addView(h(Lang.t(1220)));
        body.addView(p(Lang.t(1221), 6));
        body.addView(p(Lang.t(1222), 8));
        body.addView(p(Lang.t(1223), 8));

        body.addView(h(Lang.t(1230)));
        body.addView(p(Lang.t(1231), 6));
        body.addView(p(Lang.t(1232), 6));
        body.addView(p(Lang.t(1233), 4));
        body.addView(p(Lang.t(1234), 4));
        body.addView(p(Lang.t(1235), 4));
        body.addView(p(Lang.t(1236), 4));
        body.addView(p(Lang.t(1237), 8));

        body.addView(h(Lang.t(1240)));
        body.addView(p(Lang.t(1241), 6));
        body.addView(p(Lang.t(1242), 8));
        body.addView(p(Lang.t(1243), 8));

        body.addView(h(Lang.t(1250)));
        body.addView(p(Lang.t(1251), 6));
        body.addView(p(Lang.t(1252), 8));
        body.addView(p(Lang.t(1253), 8));
        body.addView(p(Lang.t(1254), 8));

        body.addView(h(Lang.t(1260)));
        body.addView(p(Lang.t(1261), 6));

        body.addView(h(Lang.t(1270)));
        body.addView(p(Lang.t(1271), 6));

        body.addView(h(Lang.t(1280)));
        body.addView(p(Lang.t(1281), 6));
        body.addView(p(Lang.t(1282), 8));
        body.addView(p(Lang.t(1283), 8));
        body.addView(p(Lang.t(1284), 8));
        body.addView(p(Lang.t(1285), 8));

        body.addView(h(Lang.t(1290)));
        body.addView(p(Lang.t(1291), 6));
        body.addView(p(Lang.t(1292), 8));

        body.addView(h(Lang.t(1300)));
        body.addView(p(Lang.t(1301), 6));

        body.addView(h(Lang.t(1310)));
        body.addView(p(Lang.t(1311), 6));
        TextView decl = p(Lang.t(604), 10);
        decl.setTextColor(ZUi.ACC);
        decl.setTextIsSelectable(true);
        body.addView(decl);
        body.addView(p(Lang.t(1312), 8));
        body.addView(p(Lang.t(1313), 8));

        body.addView(h(Lang.t(1320)));
        body.addView(p(Lang.t(1321), 6));
        body.addView(p(Lang.t(1322), 8));

        body.addView(h(Lang.t(1330)));
        body.addView(p(Lang.t(1331), 6));
        c1 = chk(Lang.t(1332));
        c2 = chk(Lang.t(1333));
        body.addView(c1);
        body.addView(c2);
        body.addView(p(Lang.t(1334), 6));

        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = ZUi.box(this, LinearLayout.HORIZONTAL);
        bar.setPadding(ZUi.dp(this, 16), ZUi.dp(this, 12), ZUi.dp(this, 16), ZUi.dp(this, 16));
        bar.setBackgroundColor(ZUi.CARD);
        TextView no = btn(Lang.t(271), ZUi.CARD_HI, ZUi.TXT);
        no.setOnClickListener(v -> reject());
        yes = btn(Lang.t(272), ZUi.OK, 0xFF06281C);
        c1.setOnCheckedChangeListener((v, on) -> refresh());
        c2.setOnCheckedChangeListener((v, on) -> refresh());
        refresh();
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

    private CheckBox chk(String s) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(ZUi.TXT);
        c.setTextSize(14f);
        c.setPadding(ZUi.dp(this, 8), ZUi.dp(this, 10), 0, ZUi.dp(this, 10));
        return c;
    }

    private void refresh() {
        yes.setAlpha(c1.isChecked() && c2.isChecked() ? 1f : 0.4f);
    }

    private TextView btn(String s, int bg, int fg) {
        TextView t = ZUi.text(this, s, 15f, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(ZUi.dp(this, 12), ZUi.dp(this, 16), ZUi.dp(this, 12), ZUi.dp(this, 16));
        t.setBackground(ZUi.shape(this, bg, 16));
        return t;
    }

    private void accept() {
        if (decided || !c1.isChecked() || !c2.isChecked()) return;
        decided = true;
        Consent.accept(this);
        go();
    }

    private void go() {
        startActivity(new Intent(this, home ? LauncherActivity.class : HomeActivity.class));
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
        TextView a = ZUi.text(this, Lang.t(923), 22f, ZUi.TXT, true);
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
