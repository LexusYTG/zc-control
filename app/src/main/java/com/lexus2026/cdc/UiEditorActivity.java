package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

/**
 * Editor del launcher en script: acá se escribe el launcher entero (elementos, posiciones, estilos, gestos y lógica).
 * Tiene vista previa en vivo del resultado, verificación con errores por línea, ejemplos completos y la referencia del lenguaje.
 */
public class UiEditorActivity extends Activity {
    private static final int BG = ZUi.BG, CARD = ZUi.CARD, ACC = ZUi.ACC, OKC = ZUi.OK, ERC = 0xFFF87171, DARK = 0xFF0B0F1A;

    private float d;
    private boolean es;
    private EditText srcEt;
    private TextView errTv;
    private Switch onSw;
    private FrameLayout previewBox;
    private LauncherEngine engine;

    private String T(String a, String b) { return es ? a : b; }

    private int dp(float v) { return (int) (v * d + 0.5f); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Consent.granted(this)) { Consent.require(this, false); return; }
        d = getResources().getDisplayMetrics().density;
        es = SL.es(this);
        LinearLayout root = ZUi.box(this, LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout head = ZUi.box(this, LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(10), dp(12), dp(4));
        TextView back = ZUi.text(this, "‹", 28, ZUi.TXT, false);
        back.setPadding(0, 0, dp(12), dp(2));
        back.setOnClickListener(v -> goBack());
        head.addView(back);
        head.addView(ZUi.text(this, T("Mi launcher en código", "My launcher as code"), 18, ZUi.TXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView eye = ZUi.text(this, "👁", 18, ZUi.TXT, false);
        eye.setPadding(dp(10), dp(4), dp(10), dp(4));
        eye.setOnClickListener(v -> previewBox.setVisibility(previewBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        head.addView(eye);
        onSw = new Switch(this);
        onSw.setChecked(UiStore.enabled(this));
        onSw.setOnCheckedChangeListener((v, on) -> UiStore.save(this, srcEt.getText().toString(), on));
        head.addView(onSw);
        root.addView(head);

        // vista previa del launcher con el script aplicado
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int W = dm.widthPixels, H = dm.heightPixels, ph = (int) (H * 0.30f);
        float sc = ph / (float) H;
        previewBox = new FrameLayout(this);
        previewBox.setBackground(ZUi.shape(this, 0xFF000000, 16));
        previewBox.setClipToOutline(true);
        FrameLayout host = new FrameLayout(this);
        host.setPivotX(0);
        host.setPivotY(0);
        host.setScaleX(sc);
        host.setScaleY(sc);
        View wall = new View(this);
        wall.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1B2A6B, 0xFF5B2A7B, 0xFF0B0F1A}));
        host.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        engine = new LauncherEngine(this, true);
        engine.setPreviewScale(sc);
        host.addView(engine.view(), new FrameLayout.LayoutParams(-1, -1));
        View shield = new View(this);
        shield.setClickable(true);
        host.addView(shield, new FrameLayout.LayoutParams(-1, -1));
        previewBox.addView(host, new FrameLayout.LayoutParams(W, H));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams((int) (W * sc), ph);
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        pl.topMargin = dp(4);
        root.addView(previewBox, pl);

        srcEt = new EditText(this);
        srcEt.setText(UiStore.src(this));
        srcEt.setTypeface(Typeface.MONOSPACE);
        srcEt.setTextSize(13);
        srcEt.setTextColor(0xFFE8ECFF);
        srcEt.setHint(T("Escribí tu launcher acá. Tocá «Ejemplos» para ver uno completo.", "Write your launcher here. Tap 'Examples' to see a full one."));
        srcEt.setHintTextColor(0x55FFFFFF);
        srcEt.setGravity(Gravity.TOP | Gravity.START);
        srcEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        srcEt.setHorizontallyScrolling(false);
        srcEt.setBackground(ZUi.shape(this, CARD, 14));
        srcEt.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, 0, 1);
        sl.setMargins(dp(12), dp(6), dp(12), 0);
        root.addView(srcEt, sl);

        errTv = ZUi.text(this, "", 12, OKC, false);
        errTv.setPadding(dp(16), dp(6), dp(16), dp(2));
        errTv.setMaxLines(5);
        root.addView(errTv);

        LinearLayout bar = ZUi.box(this, LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), dp(4), dp(8), dp(8));
        bar.addView(btn(T("Verificar", "Check"), false, this::check), w());
        bar.addView(btn(T("Ejemplos", "Examples"), false, this::examples), w());
        bar.addView(btn(T("Referencia", "Reference"), false, this::reference), w());
        bar.addView(btn(T("Guardar", "Save"), true, () -> save(true)), w());
        root.addView(bar);

        Insets.apply(root);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33)
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        check();
    }

    private LinearLayout.LayoutParams w() { return ZKit.lp(this, 0, -2, 1, 3, 0, 3, 0); }

    private TextView btn(String s, boolean primary, Runnable r) {
        TextView t = ZUi.text(this, s, 13, primary ? DARK : ZUi.TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(12), dp(6), dp(12));
        t.setBackground(ZUi.shape(this, primary ? ACC : ZUi.CARD_HI, 14));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    @Override protected void onResume() {
        super.onResume();
        if (engine != null) engine.onResume();
    }

    @Override protected void onPause() {
        super.onPause();
        if (engine != null) engine.onPause();
    }

    @Override protected void onDestroy() {
        if (engine != null) engine.onDestroy();
        super.onDestroy();
    }

    @Override public void onBackPressed() { goBack(); }

    private void goBack() {
        if (srcEt != null && !srcEt.getText().toString().equals(UiStore.src(this))) save(false);
        finish();
    }

    private static int count(UiLang.Node n) {
        int c = 1;
        for (UiLang.Node k : n.kids) c += count(k);
        return c;
    }

    private void check() {
        UiLang.Scene sc = UiLang.parse(srcEt.getText().toString(), es);
        if (sc.errors.isEmpty()) {
            int el = 0, ge = 0;
            for (UiLang.Node n : sc.roots) el += count(n);
            for (java.util.Map.Entry<String, java.util.List<String>> e : sc.macros.entrySet()) if (e.getKey().startsWith("__ui_h")) ge++;
            errTv.setTextColor(OKC);
            errTv.setText("✓ " + T("Sin errores: ", "No errors: ") + el + " " + T("elementos", "elements") + ", " + ge + " " + T("gestos", "gestures"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        int k = 0;
        for (ScriptLang.Err e : sc.errors) {
            if (k++ >= 5) { sb.append("…"); break; }
            sb.append(T("Línea ", "Line ")).append(e.line).append(": ").append(e.msg).append('\n');
        }
        errTv.setTextColor(ERC);
        errTv.setText(sb.toString().trim());
    }

    private void save(boolean feedback) {
        String src = srcEt.getText().toString();
        boolean wasOn = UiStore.enabled(this);
        boolean on = wasOn || (feedback && !src.trim().isEmpty());
        UiStore.save(this, src, on);
        if (on != onSw.isChecked()) {
            onSw.setOnCheckedChangeListener(null);
            onSw.setChecked(on);
            onSw.setOnCheckedChangeListener((v, o) -> UiStore.save(this, srcEt.getText().toString(), o));
        }
        check();
        if (engine != null) engine.onResume();
        if (feedback) Toast.makeText(this, on && !wasOn ? T("Guardado y activado", "Saved and enabled") : T("Guardado", "Saved"), Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ ejemplos

    private static final String[][] EX = {
        {"Reloj, fijadas y dock propio", "Clock, pinned apps and own dock",
            "// Reemplazo completo del escritorio y del dock\nocultar dock\nocultar escritorio\n\nreloj\n  formato \"HH:mm\"\n  posicion arriba izquierda\n  x 24\n  y 60\n  tamaño texto 64\n  negrita\nfin\n\ntexto \"{dia}/{mes}  ·  bateria {bateria} %\"\n  posicion arriba izquierda\n  x 26\n  y 150\n  tamaño texto 14\n  color #AAFFFFFF\nfin\n\ngrilla \"fijadas\"\n  muestra fijadas\n  columnas 4\n  posicion arriba izquierda\n  y 210\n  alto 420\n  tamaño iconos 56\nfin\n\ncaja \"dock\"\n  disposicion fila\n  posicion abajo centro\n  y 16\n  ancho 92 %\n  alto 76\n  fondo #990C0C1E\n  redondeo 32\n  relleno 10\n  icono \"Chrome\"\n    peso 1\n  fin\n  icono \"WhatsApp\"\n    peso 1\n  fin\n  boton \"☰\"\n    peso 1\n    tamaño texto 22\n    fondo #FF3D7BFF\n    redondeo circulo\n    al tocar\n      abrir cajon\n    fin\n  fin\n  icono \"Camara\"\n    peso 1\n  fin\n  icono \"Ajustes\"\n    peso 1\n  fin\nfin",
            "// Full replacement of the desktop and dock\nhide dock\nhide desktop\n\nclock\n  format \"HH:mm\"\n  position top left\n  x 24\n  y 60\n  text size 64\n  bold\nend\n\ntext \"{day}/{month}  ·  battery {battery} %\"\n  position top left\n  x 26\n  y 150\n  text size 14\n  color #AAFFFFFF\nend\n\ngrid \"pinned\"\n  show pinned\n  columns 4\n  position top left\n  y 210\n  height 420\n  icon size 56\nend\n\nbox \"dock\"\n  layout row\n  position bottom center\n  y 16\n  width 92 %\n  height 76\n  background #990C0C1E\n  radius 32\n  padding 10\n  icon \"Chrome\"\n    weight 1\n  end\n  icon \"WhatsApp\"\n    weight 1\n  end\n  button \"☰\"\n    weight 1\n    text size 22\n    background #FF3D7BFF\n    radius circle\n    on tap\n      open drawer\n    end\n  end\n  icon \"Camera\"\n    weight 1\n  end\n  icon \"Settings\"\n    weight 1\n  end\nend"},
        {"Panel lateral que se despliega", "Side panel that slides out",
            "// Botón flotante + gesto de borde que despliegan un panel\nboton \"☰\"\n  posicion abajo derecha\n  x 20\n  y 110\n  tamaño 56\n  tamaño texto 22\n  redondeo circulo\n  al tocar\n    alternar elemento \"lateral\" desde izquierda con rebote durante 350 ms\n  fin\nfin\n\ncaja \"borde\"\n  posicion arriba izquierda\n  ancho 24\n  alto todo\n  al deslizar derecha\n    mostrar elemento \"lateral\" desde izquierda con rebote durante 350 ms\n  fin\nfin\n\ncaja \"lateral\"\n  visible no\n  posicion arriba izquierda\n  ancho 270\n  alto todo\n  fondo #F0121228\n  relleno 16\n  disposicion columna\n  texto \"Mis apps\"\n    tamaño texto 20\n    negrita\n  fin\n  grilla\n    muestra apps\n    columnas 3\n    peso 1\n  fin\n  boton \"Cerrar\"\n    al tocar\n      ocultar elemento \"lateral\" hacia izquierda durante 250 ms\n    fin\n  fin\nfin",
            "// Floating button + edge gesture that slide a panel out\nbutton \"☰\"\n  position bottom right\n  x 20\n  y 110\n  size 56\n  text size 22\n  radius circle\n  on tap\n    toggle element \"side\" from left with bounce for 350 ms\n  end\nend\n\nbox \"edge\"\n  position top left\n  width 24\n  height fill\n  on swipe right\n    show element \"side\" from left with bounce for 350 ms\n  end\nend\n\nbox \"side\"\n  visible no\n  position top left\n  width 270\n  height fill\n  background #F0121228\n  padding 16\n  layout column\n  text \"My apps\"\n    text size 20\n    bold\n  end\n  grid\n    show apps\n    columns 3\n    weight 1\n  end\n  button \"Close\"\n    on tap\n      hide element \"side\" to left for 250 ms\n    end\n  end\nend"},
        {"Botones con lógica y esperas", "Buttons with logic and waits",
            "// Variables, condiciones, esperas y cambios de estilo en vivo\nmodo = 0\n\ntexto \"Modo {modo}\"\n  nombre \"estado\"\n  posicion centro\n  tamaño texto 28\n  negrita\nfin\n\nboton \"Cambiar modo\"\n  posicion abajo centro\n  y 160\n  al tocar\n    modo = modo + 1\n    si modo > 2\n      modo = 0\n    fin\n    cambiar elemento \"estado\" color rojo\n    esperar 1 s\n    cambiar elemento \"estado\" color blanco\n  fin\n  al mantener\n    poner columnas escritorio 5\n    aviso \"Columnas: 5\"\n  fin\nfin",
            "// Variables, conditions, waits and live style changes\nmodo = 0\n\ntext \"Mode {modo}\"\n  name \"state\"\n  position center\n  text size 28\n  bold\nend\n\nbutton \"Change mode\"\n  position bottom center\n  y 160\n  on tap\n    modo = modo + 1\n    if modo > 2\n      modo = 0\n    end\n    set element \"state\" color red\n    wait 1 s\n    set element \"state\" color white\n  end\n  on hold\n    set desktop columns 5\n    toast \"Columns: 5\"\n  end\nend"}
    };

    private void examples() {
        String[] names = new String[EX.length];
        for (int i = 0; i < EX.length; i++) names[i] = es ? EX[i][0] : EX[i][1];
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(T("Ejemplos completos", "Full examples"))
            .setItems(names, (dg, which) -> {
                String code = es ? EX[which][2] : EX[which][3];
                String cur = srcEt.getText().toString();
                if (cur.trim().isEmpty()) srcEt.setText(code);
                else new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage(T("¿Reemplazar lo que escribiste o agregar al final?", "Replace what you wrote or append to the end?"))
                    .setPositiveButton(T("Reemplazar", "Replace"), (x, y) -> srcEt.setText(code))
                    .setNegativeButton(T("Agregar", "Append"), (x, y) -> srcEt.setText(cur + "\n\n" + code)).show();
                check();
            }).setNegativeButton(T("Cerrar", "Close"), null).show();
    }

    // ------------------------------------------------------------------ referencia

    private void reference() {
        String r = es
            ? "UN SCRIPT ES EL LAUNCHER\nLo que escribas se dibuja encima del launcher. Con «ocultar dock» y «ocultar escritorio» reemplazás las piezas originales por las tuyas.\n\n"
            + "ELEMENTOS (cada uno termina con «fin»)\ncaja [fila|columna|apilado] · texto \"…\" · reloj · boton \"etiqueta\" · icono \"App\" · grilla · espacio\nSe pueden anidar dentro de una caja o un boton. Una línea puede tener propiedades y cerrar con «fin»: texto \"Hola\" tamaño texto 20 fin\n\n"
            + "PROPIEDADES (en líneas propias o en la misma línea)\nposicion arriba|abajo|izquierda|derecha|centro · x · y · ancho · alto · tamaño (ambos) · margen · relleno\nfondo · degradado c1 c2 [abajo|derecha…] · color (texto/ícono) · redondeo N|circulo · borde color grosor · sombra · opacidad 0-100 · rotacion\ntamaño texto · negrita · alineacion · formato \"HH:mm\" (reloj) · contenido \"…\" · nombre \"x\" · visible si|no · peso (reparto en fila/columna)\ngrilla: muestra apps|fijadas|nuevas|categoria \"X\" · columnas · espaciado · tamaño iconos · etiquetas si|no\nicono: app \"Nombre\"\nMedidas: números en dp; «90 %» de la pantalla; «todo» llena; «auto» se ajusta.\n\n"
            + "GESTOS (dentro de un elemento, terminan con «fin»)\nal tocar · al mantener · al doble toque · al deslizar arriba|abajo|izquierda|derecha\nAdentro va cualquier acción del lenguaje de scripts: abrir app/cajon/panel, poner <ajuste> <valor>, esperar 2 s, si…sino…fin, repetir N veces, mientras, ejecutar \"macro\", shell, variables (x = x + 1), aviso, vibrar…\n\n"
            + "SOBRE ELEMENTOS (por nombre)\nmostrar|ocultar|alternar elemento \"x\" [desde|hacia izquierda|derecha|arriba|abajo] [con zoom|fundido|rebote|deslizar] [durante 300 ms]\ncambiar elemento \"x\" fondo rojo x 100 texto…  (cualquier propiedad, en vivo)\n\n"
            + "TEXTO DINÁMICO\n{hora} {minuto} {segundo} {dia} {mes} {ano} {fecha} {bateria} {cargando} y tus variables {mi_variable}\n\n"
            + "FUERA DE LOS ELEMENTOS\nCualquier acción suelta se ejecuta al cargar el launcher: poner columnas escritorio 5, mi_var = 0…\nComentarios: // o --"
            : "A SCRIPT IS THE LAUNCHER\nWhat you write is drawn on top of the launcher. With 'hide dock' and 'hide desktop' you replace the original pieces with your own.\n\n"
            + "ELEMENTS (each ends with 'end')\nbox [row|column|stack] · text \"…\" · clock · button \"label\" · icon \"App\" · grid · spacer\nThey can nest inside a box or a button. A line can hold properties and close with 'end': text \"Hi\" text size 20 end\n\n"
            + "PROPERTIES (on their own lines or on the same line)\nposition top|bottom|left|right|center · x · y · width · height · size (both) · margin · padding\nbackground · gradient c1 c2 [down|right…] · color (text/icon) · radius N|circle · border color width · shadow · opacity 0-100 · rotation\ntext size · bold · align · format \"HH:mm\" (clock) · content \"…\" · name \"x\" · visible yes|no · weight (share space in row/column)\ngrid: show apps|pinned|new|category \"X\" · columns · gap · icon size · labels yes|no\nicon: app \"Name\"\nSizes: numbers in dp; '90 %' of the screen; 'fill' fills; 'auto' wraps.\n\n"
            + "GESTURES (inside an element, end with 'end')\non tap · on hold · on double tap · on swipe up|down|left|right\nInside goes any action of the scripting language: open app/drawer/panel, set <setting> <value>, wait 2 s, if…else…end, repeat N times, while, run \"macro\", shell, variables (x = x + 1), toast, vibrate…\n\n"
            + "ON ELEMENTS (by name)\nshow|hide|toggle element \"x\" [from|to left|right|up|down] [with zoom|fade|bounce|slide] [for 300 ms]\nset element \"x\" background red x 100 …  (any property, live)\n\n"
            + "DYNAMIC TEXT\n{hour} {minute} {second} {day} {month} {year} {date} {battery} {charging} and your variables {my_variable}\n\n"
            + "OUTSIDE ELEMENTS\nAny loose action runs when the launcher loads: set desktop columns 5, my_var = 0…\nComments: // or --";
        ScrollView sv = new ScrollView(this);
        TextView t = ZUi.text(this, r, 12, 0xFFE8ECFF, false);
        t.setTypeface(Typeface.MONOSPACE);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        sv.addView(t, new ViewGroup.LayoutParams(-1, -2));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(T("Referencia del lenguaje", "Language reference"))
            .setView(sv).setPositiveButton("OK", null).show();
    }
}
