package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedDispatcher;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Editor de scripts del launcher: lista, edición, verificación, ejemplos y referencia. */
public class ScriptEditorActivity extends Activity {
    private static final int BG = 0xFF0B0B1A, CARD = 0xFF1A1A33, ACC = 0xFF3D7BFF, OKC = 0xFF66E08A, ERC = 0xFFFF6B6B;

    private float d;
    private FrameLayout host;
    private ScriptStore.Item editing;
    private EditText nameEt, srcEt;
    private TextView errTv;
    private boolean inEdit;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        host = new FrameLayout(this);
        host.setBackgroundColor(BG);
        host.setFitsSystemWindows(true);
        setContentView(host);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        }
        showList();
    }

    @Override public void onBackPressed() { goBack(); }

    private void goBack() {
        if (inEdit) { save(false); showList(); } else finish();
    }

    private String T(String es, String en) { return SL.t(this, es, en); }

    private int dp(float v) { return (int) (v * d + 0.5f); }

    private GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r * d);
        return g;
    }

    private TextView tv(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView btn(String s, final Runnable r) {
        TextView t = tv(s, 14, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setBackground(round(ACC, 14));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        return lp;
    }

    private String check(String src) {
        ScriptLang.ES = SL.es(this);
        ScriptLang.Program pg = new ScriptLang.Program();
        ScriptLang.parse(src, pg);
        if (pg.errors.isEmpty())
            return "✓ " + T("Sin errores: ", "No errors: ") + pg.rules.size() + " " + T("reglas", "rules") + ", " + pg.macros.size() + " macros, "
                + pg.panels.size() + " " + T("paneles", "panels") + ", " + pg.buttons.size() + " " + T("botones", "buttons");
        StringBuilder sb = new StringBuilder();
        for (ScriptLang.Err e : pg.errors) sb.append(T("Línea ", "Line ")).append(e.line).append(": ").append(e.msg).append('\n');
        return sb.toString().trim();
    }

    private int errorCount(String src) {
        ScriptLang.ES = SL.es(this);
        ScriptLang.Program pg = new ScriptLang.Program();
        ScriptLang.parse(src, pg);
        return pg.errors.size();
    }

    // ---------------- lista ----------------
    private void showList() {
        inEdit = false;
        editing = null;
        host.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = tv("</>  " + T("Scripts del launcher", "Launcher scripts"), 20, Color.WHITE);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Switch master = new Switch(this);
        master.setChecked(ScriptStore.master(this));
        master.setOnCheckedChangeListener((v, on) -> ScriptStore.setMaster(this, on));
        head.addView(master);
        TextView close = tv("✕", 20, Color.WHITE);
        close.setPadding(dp(14), dp(4), dp(2), dp(4));
        close.setOnClickListener(v -> finish());
        head.addView(close);
        col.addView(head);

        TextView hint = tv(T("Escribí en español o inglés: «cuando toco el boton central 2 veces → abrir panel…». El interruptor de arriba activa o apaga todos los scripts.",
            "Write in Spanish or English: 'when I tap the center button 2 times → open panel…'. The switch above turns all scripts on or off."), 12, 0xAAFFFFFF);
        hint.setPadding(0, dp(6), 0, dp(8));
        col.addView(hint);

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        List<ScriptStore.Item> items = ScriptStore.list(this);
        if (items.isEmpty()) {
            TextView e = tv(T("Todavía no hay scripts. Tocá «+ Nuevo» o «Ejemplos».", "No scripts yet. Tap '+ New' or 'Examples'."), 14, 0xBBFFFFFF);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(10), dp(40), dp(10), dp(40));
            list.addView(e);
        }
        for (final ScriptStore.Item it : items) list.addView(row(it));
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(0, dp(8), 0, 0);
        bar.addView(btn("+ " + T("Nuevo", "New"), () -> {
            ScriptStore.Item n = new ScriptStore.Item();
            n.id = ScriptStore.newId();
            n.name = T("Script ", "Script ") + (ScriptStore.list(this).size() + 1);
            showEdit(n, true);
        }), weight());
        bar.addView(btn(T("Ejemplos", "Examples"), () -> pickExample(null)), weight());
        bar.addView(btn(T("Referencia", "Reference"), this::showReference), weight());
        col.addView(bar);
        host.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    private View row(final ScriptStore.Item it) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(12), dp(10), dp(12));
        r.setBackground(round(CARD, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        r.setLayoutParams(lp);
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(tv(it.name.isEmpty() ? T("(sin nombre)", "(unnamed)") : it.name, 16, Color.WHITE));
        int errs = errorCount(it.src);
        int lines = it.src.isEmpty() ? 0 : it.src.split("\n").length;
        TextView sub = tv(errs > 0 ? "⚠ " + errs + " " + T("errores", "errors") : lines + " " + T("líneas", "lines"), 12, errs > 0 ? ERC : 0x99FFFFFF);
        txt.addView(sub);
        r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(it.on);
        sw.setOnCheckedChangeListener((v, on) -> { it.on = on; ScriptStore.save(this, it); });
        r.addView(sw);
        r.setOnClickListener(v -> showEdit(it, false));
        r.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(it.name)
                .setMessage(T("¿Borrar este script?", "Delete this script?"))
                .setPositiveButton(T("Borrar", "Delete"), (dg, w) -> { ScriptStore.delete(this, it.id); showList(); })
                .setNegativeButton(T("Cancelar", "Cancel"), null).show();
            return true;
        });
        return r;
    }

    // ---------------- edición ----------------
    private void showEdit(ScriptStore.Item it, boolean isNew) {
        inEdit = true;
        editing = it;
        host.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = tv("‹", 30, Color.WHITE);
        back.setPadding(dp(2), 0, dp(14), dp(4));
        back.setOnClickListener(v -> goBack());
        head.addView(back);
        nameEt = new EditText(this);
        nameEt.setText(it.name);
        nameEt.setHint(T("Nombre", "Name"));
        nameEt.setSingleLine();
        nameEt.setTextColor(Color.WHITE);
        nameEt.setHintTextColor(0x66FFFFFF);
        nameEt.setTextSize(18);
        head.addView(nameEt, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(it.on);
        sw.setOnCheckedChangeListener((v, on) -> it.on = on);
        head.addView(sw);
        col.addView(head);

        srcEt = new EditText(this);
        srcEt.setText(it.src);
        srcEt.setTypeface(Typeface.MONOSPACE);
        srcEt.setTextSize(13);
        srcEt.setTextColor(0xFFE8ECFF);
        srcEt.setHint(T("cuando toco el boton central 2 veces\n  abrir cajon\nfin", "when I tap the center button 2 times\n  open drawer\nend"));
        srcEt.setHintTextColor(0x44FFFFFF);
        srcEt.setGravity(Gravity.TOP | Gravity.START);
        srcEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        srcEt.setHorizontallyScrolling(false);
        srcEt.setBackground(round(CARD, 14));
        srcEt.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, 0, 1);
        sl.topMargin = dp(8);
        col.addView(srcEt, sl);

        errTv = tv("", 12, OKC);
        errTv.setPadding(dp(4), dp(6), dp(4), dp(2));
        errTv.setMaxLines(6);
        col.addView(errTv);

        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(0, dp(6), 0, 0);
        bar.addView(btn(T("Verificar", "Check"), this::runCheck), weight());
        bar.addView(btn(T("Ejemplos", "Examples"), () -> pickExample(srcEt)), weight());
        bar.addView(btn(T("Ref.", "Ref."), this::showReference), weight());
        bar.addView(btn(T("Guardar", "Save"), () -> save(true)), weight());
        col.addView(bar);
        host.addView(col, new FrameLayout.LayoutParams(-1, -1));
        if (isNew && it.src.isEmpty()) srcEt.requestFocus();
    }

    private void runCheck() {
        String r = check(srcEt.getText().toString());
        errTv.setTextColor(r.startsWith("✓") ? OKC : ERC);
        errTv.setText(r);
    }

    private void save(boolean feedback) {
        if (editing == null || srcEt == null) return;
        String src = srcEt.getText().toString();
        String nm = nameEt.getText().toString().trim();
        if (src.trim().isEmpty() && ScriptStore.get(this, editing.id) == null) return; // no guardar uno nuevo vacío
        editing.name = nm.isEmpty() ? T("Script", "Script") : nm;
        editing.src = src;
        ScriptStore.save(this, editing);
        if (feedback) {
            runCheck();
            Toast.makeText(this, T("Guardado", "Saved"), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- ejemplos ----------------
    private static final String[][] EX = {
        {"Doble toque en el botón central", "Double tap on the center button",
            "cuando toco el boton central 2 veces\n  abrir cajon\nfin",
            "when I tap the center button 2 times\n  open drawer\nend"},
        {"Panel rápido (mantener botón central)", "Quick panel (hold center button)",
            "panel \"Rapido\"\n  desde abajo\n  columnas 4\n  titulo \"Acceso rápido\"\n  app \"Chrome\"\n  app \"WhatsApp\"\n  boton \"Ajustes\" icono \"⚙\" macro \"ajustes\"\nfin\n\nmacro \"ajustes\"\n  abrir ajustes\nfin\n\ncuando mantengo el boton central 600 ms\n  bloquear\n  abrir panel \"Rapido\" desde abajo con rebote\nfin",
            "panel \"Quick\"\n  from down\n  columns 4\n  title \"Quick access\"\n  app \"Chrome\"\n  app \"WhatsApp\"\n  button \"Settings\" icon \"⚙\" macro \"settings\"\nend\n\nmacro \"settings\"\n  open settings\nend\n\nwhen I hold the center button 600 ms\n  block\n  open panel \"Quick\" from down with bounce\nend"},
        {"Aviso a una hora fija", "Notice at a fixed time",
            "a las 08:00\n  aviso \"Buenos dias\"\n  vibrar 200 ms\nfin",
            "at 08:00\n  toast \"Good morning\"\n  vibrate 200 ms\nend"},
        {"Batería baja → bajar brillo", "Low battery → lower brightness",
            "cada 5 minutos\n  si bateria < 20 y no cargando\n    brillo 30\n  fin\nfin",
            "every 5 minutes\n  if battery < 20 and not charging\n    brightness 30\n  end\nend"},
        {"Cambiar el dock al abrir una app", "Change the dock when opening an app",
            "cuando abro la app \"YouTube\"\n  poner dock color rojo\nfin",
            "when I open the app \"YouTube\"\n  set dock color red\nend"},
        {"Botón flotante que abre un panel", "Floating button that toggles a panel",
            "boton \"Menu\"\n  icono \"☰\"\n  posicion abajo derecha\n  tamaño 52\n  color azul\nfin\n\ncuando toco el boton \"Menu\"\n  alternar panel \"Rapido\"\nfin",
            "button \"Menu\"\n  icon \"☰\"\n  position down right\n  size 52\n  color blue\nend\n\nwhen I tap the button \"Menu\"\n  toggle panel \"Quick\"\nend"},
        {"Deslizar desde el borde izquierdo", "Swipe from the left edge",
            "cuando deslizo desde el borde izquierdo\n  abrir cajon\nfin",
            "when I swipe from the left edge\n  open drawer\nend"},
        {"Ajustar el launcher (columnas, colores)", "Tune the launcher (columns, colors)",
            "cuando hago doble toque en el fondo\n  poner columnas escritorio 5\n  poner color cajon #CC101030\n  poner tamaño etiquetas 10\nfin",
            "when I double tap the background\n  set desktop columns 5\n  set drawer color #CC101030\n  set label size 10\nend"},
    };

    private void pickExample(final EditText target) {
        final boolean es = SL.es(this);
        String[] names = new String[EX.length];
        for (int i = 0; i < EX.length; i++) names[i] = es ? EX[i][0] : EX[i][1];
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(T("Ejemplos", "Examples"))
            .setItems(names, (dg, which) -> {
                String code = es ? EX[which][2] : EX[which][3];
                if (target != null) {
                    int pos = Math.max(0, target.getSelectionStart());
                    String cur = target.getText().toString();
                    String ins = (cur.isEmpty() ? "" : "\n\n") + code + "\n";
                    target.getText().insert(cur.isEmpty() ? 0 : cur.length(), ins);
                } else {
                    ScriptStore.Item n = new ScriptStore.Item();
                    n.id = ScriptStore.newId();
                    n.name = names[which];
                    n.src = code;
                    ScriptStore.save(this, n);
                    showList();
                }
            }).setNegativeButton(T("Cerrar", "Close"), null).show();
    }

    // ---------------- referencia ----------------
    private void showReference() {
        String es = "EVENTOS (primera línea de una regla; termina con «fin»)\n"
            + "• cuando toco el boton central [N veces] / mantengo ... [600 ms] / deslizo ...\n"
            + "• cuando toco / mantengo el fondo · toco el boton \"X\"\n"
            + "• cuando abro la app \"X\" · instalo / desinstalo la app\n"
            + "• cuando abro / cierro el cajon · la categoria \"X\" · el panel \"X\"\n"
            + "• cuando deslizo desde el borde izquierdo|derecho|arriba|abajo\n"
            + "• cuando arranca · vuelvo · pulso home\n"
            + "• cada 30 minutos · a las 08:00\n\n"
            + "ACCIONES\n"
            + "abrir app \"X\" · abrir cajon · abrir categoria \"X\" · abrir ajustes · abrir editor · abrir \"https://…\"\n"
            + "abrir panel \"X\" desde abajo con rebote durante 5 s · alternar panel \"X\" · cerrar panel \"X\" · cerrar todo\n"
            + "poner <ajuste> <valor> · sumar|restar <ajuste> N · alternar <ajuste> · restablecer <ajuste> | todo\n"
            + "ocultar|mostrar app \"X\" · fijar|desfijar app \"X\" · poner app \"X\" en dock 2 · quitar dock 2\n"
            + "crear categoria \"X\" · poner app \"X\" en categoria \"Y\" · quitar app \"X\" de categoria \"Y\"\n"
            + "ocultar|mostrar|alternar boton \"X\" · aviso \"texto {variable}\" · vibrar 100 ms · brillo 50 · volumen 30\n"
            + "esperar 2 s · repetir 3 veces … fin · mientras … … fin · si … sino … fin · detener · bloquear · ejecutar \"macro\"\n"
            + "shell \"comando\" (la salida queda en la variable salida) · x = x + 1 · recargar\n\n"
            + "AJUSTES: radial, apertura, burbuja, separacion, sensibilidad, tiempo mantener, animacion, forma, icono dock, icono app, fondo, oscurecer, acento, alto/ancho dock, posicion dock, columnas escritorio, columnas cajon, alto cajon, color cajon, redondeo cajon, color dock, redondeo dock, etiquetas, tamaño etiquetas, tamaño boton central, mostrar dock, color texto, espaciado escritorio, animacion cajon, modo escritorio…\n\n"
            + "PANEL: desde, animacion, duracion, columnas, alto, ancho, fondo, redondeo, titulo, color texto, tamaño, cerrar fuera, cerrar al elegir; elementos: app \"X\" · categoria \"X\" · boton \"Nombre\" icono \"⚙\" macro|shell|app|panel \"…\"\n"
            + "BOTÓN: icono, etiqueta, app, posicion (abajo derecha…), x, y, tamaño, opacidad, forma, fondo, color texto, visible\n\n"
            + "VARIABLES: bateria, cargando, hora, minuto, dia, fecha, mes, ano, cajon, app, veces, aleatorio, o las tuyas. Comparar: <, >, =, «es mayor que», «contiene».\n"
            + "Comentarios: // o --";
        String en = "EVENTS (first line of a rule; ends with 'end')\n"
            + "• when I tap the center button [N times] / hold ... [600 ms] / swipe ...\n"
            + "• when I tap / hold the background · tap the button \"X\"\n"
            + "• when I open the app \"X\" · install / uninstall the app\n"
            + "• when I open / close the drawer · the category \"X\" · the panel \"X\"\n"
            + "• when I swipe from the left|right|top|bottom edge\n"
            + "• when it starts · I return · I press home\n"
            + "• every 30 minutes · at 08:00\n\n"
            + "ACTIONS\n"
            + "open app \"X\" · open drawer · open category \"X\" · open settings · open editor · open \"https://…\"\n"
            + "open panel \"X\" from down with bounce for 5 s · toggle panel \"X\" · close panel \"X\" · close all\n"
            + "set <setting> <value> · add|sub <setting> N · toggle <setting> · reset <setting> | all\n"
            + "hide|show app \"X\" · pin|unpin app \"X\" · set app \"X\" in dock 2 · remove dock 2\n"
            + "create category \"X\" · set app \"X\" in category \"Y\" · remove app \"X\" from category \"Y\"\n"
            + "hide|show|toggle button \"X\" · toast \"text {variable}\" · vibrate 100 ms · brightness 50 · volume 30\n"
            + "wait 2 s · repeat 3 times … end · while … … end · if … else … end · stop · block · run \"macro\"\n"
            + "shell \"command\" (output goes to the variable output) · x = x + 1 · refresh\n\n"
            + "SETTINGS: ring, arc, bubble, gap, sensitivity, hold time, animation, shape, dock icon, app icon, background, dim, accent, dock height/width, dock position, desktop columns, drawer columns, drawer height, drawer color, drawer radius, dock color, dock radius, labels, label size, center button size, show dock, text color, desktop spacing, drawer animation, desktop mode…\n\n"
            + "PANEL: from, animation, duration, columns, height, width, background, radius, title, text color, size, close outside, close on select; items: app \"X\" · category \"X\" · button \"Name\" icon \"⚙\" macro|shell|app|panel \"…\"\n"
            + "BUTTON: icon, label, app, position (down right…), x, y, size, opacity, shape, background, text color, visible\n\n"
            + "VARIABLES: battery, charging, hour, minute, day, date, month, year, drawer, app, times, random, or your own. Compare: <, >, =, 'is greater than', 'contains'.\n"
            + "Comments: // or --";
        ScrollView sv = new ScrollView(this);
        TextView t = tv(SL.es(this) ? es : en, 12, 0xFFE8ECFF);
        t.setTypeface(Typeface.MONOSPACE);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        sv.addView(t, new ViewGroup.LayoutParams(-1, -2));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(T("Referencia", "Reference"))
            .setView(sv)
            .setPositiveButton("OK", null).show();
    }
}
