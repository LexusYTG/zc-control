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
        body.addView(ZUi.text(this, "⚠️ ATENCIÓN: CONSENTIMIENTO REQUERIDO POR EL USUARIO", 20f, ZUi.WARN, true));
        body.addView(p("Este software ha sido desarrollado y se mantiene bajo el marco de la legislación vigente de la República Argentina a la fecha de su publicación y mantenimiento.", 14));
        body.addView(p("Su desarrollo, distribución y tratamiento de datos se rigen por dicha jurisdicción y no pretenden acogerse a normativas o legislaciones de otros países.", 10));

        body.addView(h("Tratamiento y privacidad de los datos"));
        body.addView(p("Esta herramienta:", 6));
        body.addView(p("•  No recopila datos con fines comerciales, publicitarios ni lucrativos.", 4));
        body.addView(p("•  No transmite información personal ni datos de uso a terceros.", 4));
        body.addView(p("•  Cualquier dato recopilado se utiliza única y exclusivamente para el funcionamiento de la propia herramienta.", 4));
        body.addView(p("•  Todo procesamiento de datos se realiza localmente y la información permanece almacenada en este dispositivo.", 4));
        body.addView(p("Por defecto, esta herramienta no solicita ni dispone de acceso a Internet.", 10));

        body.addView(h("Verificación de permisos"));
        body.addView(p("Por motivos de transparencia y seguridad, le recomendamos verificar que la aplicación no disponga de permisos de acceso a la red.", 6));
        body.addView(p("Si cuenta con herramientas capaces de visualizar y analizar el manifiesto XML de una aplicación Android (\"AndroidManifest.xml\"), abra el manifiesto correspondiente a esta herramienta y compruebe que NO exista la siguiente declaración:", 10));
        TextView decl = p("\"<uses-permission android:name=\"android.permission.INTERNET\" />\"", 10);
        decl.setTextColor(ZUi.ACC);
        decl.setTextIsSelectable(true);
        body.addView(decl);
        body.addView(p("Si encuentra esta declaración:", 10));
        body.addView(p("1. Desinstale inmediatamente la herramienta.", 4));
        body.addView(p("2. No vuelva a ejecutarla.", 4));
        body.addView(p("3. Analice el dispositivo en busca de posibles programas maliciosos o modificaciones no autorizadas que pudieran haberse instalado simultáneamente.", 4));
        body.addView(p("Si no dispone de herramientas para examinar archivos XML, puede realizar una comprobación alternativa:", 12));
        body.addView(p("1. Mantenga pulsado el icono de la aplicación en el menú de inicio.", 4));
        body.addView(p("2. Seleccione «Información de la aplicación».", 4));
        body.addView(p("3. Acceda al apartado «Uso de datos» o «Uso de datos móviles y Wi-Fi», si está disponible.", 4));
        body.addView(p("Compruebe que no exista actividad de red atribuible a esta aplicación.", 10));
        body.addView(p("Si el apartado existe, verifique que no se haya registrado tráfico de datos.", 6));

        body.addView(h("Consentimiento para el tratamiento local de datos"));
        body.addView(p("En cumplimiento de las obligaciones aplicables en materia de protección de datos personales y transparencia sobre su tratamiento, solicitamos su consentimiento expreso antes de habilitar las funciones que requieran acceder a datos de uso.", 6));
        TextView q = ZUi.text(this, "¿ACEPTA QUE ESTA HERRAMIENTA ACCEDA A DATOS DE USO DEL DISPOSITIVO Y DE LAS APLICACIONES PARA SU PROCESAMIENTO EXCLUSIVAMENTE LOCAL, SIN FINES COMERCIALES NI TRANSMISIÓN A TERCEROS?", 15f, ZUi.TXT, true);
        q.setPadding(0, ZUi.dp(this, 14), 0, 0);
        body.addView(q);
        body.addView(p("Su aceptación autoriza únicamente el tratamiento local de los datos necesarios para el funcionamiento de las características correspondientes.", 10));

        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = ZUi.box(this, LinearLayout.HORIZONTAL);
        bar.setPadding(ZUi.dp(this, 16), ZUi.dp(this, 12), ZUi.dp(this, 16), ZUi.dp(this, 16));
        bar.setBackgroundColor(ZUi.CARD);
        TextView no = btn("NO ACEPTO", ZUi.CARD_HI, ZUi.TXT);
        no.setOnClickListener(v -> reject());
        TextView yes = btn("ACEPTO", ZUi.OK, 0xFF06281C);
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
        TextView c = ZUi.text(this, "Al no otorgar el consentimiento, la aplicación no solicitó ni modificó nada. Se cerrará por completo ahora.", 14f, ZUi.SUB, false);
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
