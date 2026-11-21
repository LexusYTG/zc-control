package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Constructor visual del launcher: vista previa en vivo arriba y, abajo, páginas para cada zona (escritorio, dock, cajón, radial, fondo),
 * más paneles, botones flotantes, reglas («cuando X → haz Y») y macros con esperas, bucles y condiciones.
 * Todo lo que no es un ajuste simple se guarda en {@link VisualModel}, que genera el script legible que ya ejecuta el motor.
 */
public class LauncherBuilderActivity extends Activity {
    private static final int BG = ZUi.BG, CARD = ZUi.CARD, HI = ZUi.CARD_HI, TXT = ZUi.TXT, SUB = ZUi.SUB, ACC = ZUi.ACC,
        DARK = 0xFF0B0F1A, BAD = 0xFFF87171, OKC = ZUi.OK;

    private LauncherSettings st;
    private VisualModel vm;
    private LauncherEngine engine;
    private ApplicationManager am;
    private CategoryManager cm;
    private FrameLayout previewBox, host, mock;
    private LinearLayout controls, tabs, modeRow;
    private ScrollView scroll;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int page;
    private boolean es, dirty;
    private float d;
    private VisualModel.Panel subPanel;
    private VisualModel.Btn subBtn;
    private VisualModel.Rule subRule;

    private final Runnable applyRun = () -> {
        if (engine == null) return;
        engine.applyAll();
        if (page == 3 && subPanel == null && subBtn == null && subRule == null) { engine.previewRadial(false); engine.previewRadial(true); }
        drawMock();
    };
    private final Runnable saveRun = this::flush;

    // ================================================================== ciclo de vida

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Consent.granted(this)) { Consent.require(this, false); return; }
        d = getResources().getDisplayMetrics().density;
        es = SL.es(this);
        st = new LauncherSettings(this);
        vm = VisualModel.load(this);
        am = new ApplicationManager(this);
        am.refreshAsync(() -> { });
        cm = new CategoryManager(this);

        LinearLayout root = ZUi.box(this, LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // ---- encabezado
        LinearLayout head = ZUi.box(this, LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(10), dp(12), dp(6));
        TextView back = ZUi.text(this, "‹", 28, TXT, false);
        back.setPadding(0, 0, dp(14), dp(2));
        back.setOnClickListener(v -> goBack());
        head.addView(back);
        head.addView(ZUi.text(this, T("Constructor del launcher", "Launcher builder"), 19, TXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView eye = ZUi.text(this, "👁", 18, TXT, false);
        eye.setPadding(dp(12), dp(4), dp(12), dp(4));
        eye.setOnClickListener(v -> {
            int vis = previewBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
            previewBox.setVisibility(vis);
            modeRow.setVisibility(vis);
        });
        head.addView(eye);
        TextView play = ZUi.text(this, "▶", 18, ACC, true);
        play.setPadding(dp(12), dp(4), dp(8), dp(4));
        play.setOnClickListener(v -> { flush(); startActivity(new Intent(this, LauncherActivity.class)); });
        head.addView(play);
        root.addView(head);

        // ---- vista previa: el launcher real a tamaño de pantalla, reducido
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int W = dm.widthPixels, H = dm.heightPixels;
        int ph = (int) (H * 0.33f);
        float sc = ph / (float) H;
        previewBox = new FrameLayout(this);
        previewBox.setBackground(ZUi.shape(this, 0xFF000000, 16));
        previewBox.setClipToOutline(true);
        host = new FrameLayout(this);
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
        mock = new FrameLayout(this);
        host.addView(mock, new FrameLayout.LayoutParams(-1, -1));
        View shield = new View(this);
        shield.setClickable(true);
        host.addView(shield, new FrameLayout.LayoutParams(-1, -1));
        previewBox.addView(host, new FrameLayout.LayoutParams(W, H));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams((int) (W * sc), ph);
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(previewBox, pl);

        modeRow = ZUi.box(this, LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER);
        modeRow.setPadding(dp(8), dp(6), dp(8), dp(2));
        String[] mn = {T("Escritorio", "Desktop"), T("Cajón", "Drawer"), T("Radial", "Radial")};
        for (int i = 0; i < 3; i++) {
            final int m = i;
            TextView c = ZUi.text(this, mn[i], 12, TXT, true);
            c.setPadding(dp(14), dp(6), dp(14), dp(6));
            c.setBackground(ZUi.shape(this, HI, 14));
            c.setOnClickListener(v -> previewMode(m));
            modeRow.addView(c, ZKit.lp(this, -2, -2, 0, 3, 0, 3, 0));
        }
        root.addView(modeRow);

        // ---- pestañas y contenido
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabs = ZUi.box(this, LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(12), dp(6), dp(12), dp(6));
        hs.addView(tabs);
        root.addView(hs);

        controls = ZUi.box(this, LinearLayout.VERTICAL);
        controls.setPadding(dp(16), dp(2), dp(16), dp(40));
        scroll = new ScrollView(this);
        scroll.addView(controls);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Insets.apply(root);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33)
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        render();
        drawMock();
    }

    @Override protected void onResume() {
        super.onResume();
        if (engine != null) engine.onResume();
    }

    @Override protected void onPause() {
        super.onPause();
        flush();
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (engine != null) engine.onDestroy();
        super.onDestroy();
    }

    @Override public void onBackPressed() { goBack(); }

    private void goBack() {
        if (subPanel != null || subBtn != null || subRule != null) { subPanel = null; subBtn = null; subRule = null; render(); return; }
        finish();
    }

    // ================================================================== utilidades

    private String T(String a, String b) { return es ? a : b; }

    private int dp(float v) { return (int) (v * d + 0.5f); }

    private static String q(String s) { return "\"" + (s == null ? "" : s.replace("\"", "'")) + "\""; }

    private AlertDialog.Builder dlg() { return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert); }

    private void flush() {
        ui.removeCallbacks(saveRun);
        if (dirty && vm != null) { dirty = false; vm.save(this); }
    }

    private void saveSoon() {
        dirty = true;
        ui.removeCallbacks(saveRun);
        ui.postDelayed(saveRun, 350);
    }

    /** Un ajuste del launcher cambió: se redibuja la vista previa, juntando cambios seguidos. */
    private void changedSetting() {
        ui.removeCallbacks(applyRun);
        ui.postDelayed(applyRun, 90);
    }

    /** Cambio del modelo sin tocar la estructura de la pantalla (deslizadores, colores). */
    private void touched() {
        saveSoon();
        drawMock();
    }

    /** Cambio de estructura (agregar, borrar, mover): guarda y vuelve a dibujar la pantalla. */
    private void commit() {
        dirty = true;
        flush();
        drawMock();
        render();
    }

    private void previewMode(int m) {
        engine.previewRadial(false);
        engine.previewDrawer(false);
        if (m == 1) engine.previewDrawer(true);
        else if (m == 2) ui.postDelayed(() -> engine.previewRadial(true), 120);
    }

    /** Botones flotantes dibujados de mentira sobre la vista previa (el script real solo corre en el launcher). */
    private void drawMock() {
        if (mock == null) return;
        mock.removeAllViews();
        for (VisualModel.Btn b : vm.buttons) {
            if (!b.visible || b.name.trim().isEmpty()) continue;
            TextView t = new TextView(this);
            t.setText(b.icon);
            t.setTextColor(b.tcolor);
            t.setTextSize(b.size * 0.4f);
            t.setGravity(Gravity.CENTER);
            t.setAlpha(b.opacity / 100f);
            GradientDrawable g = new GradientDrawable();
            g.setColor(b.bg);
            g.setCornerRadius(dp(b.shape.equals("circle") ? b.size / 2f : b.shape.equals("round") ? b.size / 4f : 0));
            t.setBackground(g);
            int gv = (b.hz.equals("left") ? Gravity.START : b.hz.equals("right") ? Gravity.END : Gravity.CENTER_HORIZONTAL)
                | (b.v.equals("up") ? Gravity.TOP : b.v.equals("down") ? Gravity.BOTTOM : Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(b.size), dp(b.size), gv);
            lp.setMargins(dp(8 + Math.max(0, b.x)), dp(30 + Math.max(0, b.y)), dp(8 + Math.max(0, b.x)), dp(80 + Math.max(0, b.y)));
            mock.addView(t, lp);
        }
    }

    // ================================================================== piezas de interfaz

    private LinearLayout card() {
        LinearLayout c = ZUi.box(this, LinearLayout.VERTICAL);
        c.setBackground(ZUi.shape(this, CARD, 20));
        c.setPadding(dp(16), dp(6), dp(16), dp(6));
        controls.addView(c, ZKit.lp(this, -1, -2, 0, 0, 0, 0, 6));
        return c;
    }

    private void section(String s) { controls.addView(ZUi.sectionLabel(this, s)); }

    private void note(LinearLayout c, String s, int color) {
        TextView t = ZUi.text(this, s, 12.5f, color, false);
        t.setLineSpacing(0f, 1.15f);
        t.setPadding(0, dp(8), 0, dp(8));
        c.addView(t);
    }

    private TextView mini(String s, Runnable r) {
        TextView t = ZUi.text(this, s, 13, TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(6), dp(10), dp(6));
        t.setBackground(ZUi.shape(this, HI, 10));
        t.setOnClickListener(v -> r.run());
        t.setLayoutParams(ZKit.lp(this, -2, -2, 0, 3, 0, 0, 0));
        return t;
    }

    private void bigButton(String s, boolean primary, Runnable r) {
        TextView t = ZUi.text(this, s, 14, primary ? DARK : TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(13), dp(12), dp(13));
        t.setBackground(ZUi.shape(this, primary ? ACC : HI, 14));
        t.setOnClickListener(v -> r.run());
        controls.addView(t, ZKit.lp(this, -1, -2, 0, 0, 8, 0, 0));
    }

    private void sliderRow(LinearLayout c, String label, int lo, int hi, String unit, IntSupplier get, IntConsumer set) {
        final TextView t = ZUi.text(this, "", 14, TXT, false);
        t.setPadding(0, dp(12), 0, 0);
        c.addView(t);
        final int cur = Math.max(lo, Math.min(hi, get.getAsInt()));
        t.setText(label + ": " + cur + unit);
        SeekBar sb = new SeekBar(this);
        sb.setMax(hi - lo);
        sb.setProgress(cur - lo);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                t.setText(label + ": " + (p + lo) + unit);
                if (user) set.accept(p + lo);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { set.accept(s.getProgress() + lo); }
        });
        c.addView(sb, ZKit.lp(this, -1, -2, 0, 0, 2, 0, 8));
    }

    private void toggleRow(LinearLayout c, String label, boolean on, Consumer<Boolean> set) {
        LinearLayout r = ZUi.box(this, LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(12), 0, dp(12));
        r.addView(ZUi.text(this, label, 14, TXT, false), new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((v, b) -> set.accept(b));
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        c.addView(r);
    }

    private void chipsRow(LinearLayout c, String label, String[] opts, int sel, IntConsumer pick) {
        if (label != null) {
            TextView t = ZUi.text(this, label, 14, TXT, false);
            t.setPadding(0, dp(12), 0, dp(8));
            c.addView(t);
        }
        LinearLayout row = ZUi.box(this, LinearLayout.HORIZONTAL);
        for (int i = 0; i < opts.length; i++) {
            final int idx = i;
            boolean on = i == sel;
            TextView ch = ZUi.text(this, opts[i], 11.5f, on ? DARK : TXT, true);
            ch.setGravity(Gravity.CENTER);
            ch.setPadding(dp(3), dp(10), dp(3), dp(10));
            ch.setBackground(ZUi.shape(this, on ? ACC : HI, 12));
            ch.setOnClickListener(v -> pick.accept(idx));
            row.addView(ch, ZKit.lp(this, 0, -2, 1, i == 0 ? 0 : 3, 0, i == opts.length - 1 ? 0 : 3, 0));
        }
        c.addView(row, ZKit.lp(this, -1, -2, 0, 0, 0, 0, 8));
    }

    private void colorRow(LinearLayout c, String label, int cur, boolean alpha, IntConsumer set) {
        LinearLayout r = ZUi.box(this, LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(12), 0, dp(12));
        r.addView(ZUi.text(this, label, 14, TXT, false), new LinearLayout.LayoutParams(0, -2, 1));
        View sw = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(cur);
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), 0x55FFFFFF);
        sw.setBackground(g);
        r.addView(sw, new LinearLayout.LayoutParams(dp(44), dp(28)));
        r.setOnClickListener(v -> askColor(label, cur, alpha, col -> { set.accept(col); render(); }));
        c.addView(r);
    }

    private void textRow(LinearLayout c, String label, String cur, Consumer<String> set) {
        LinearLayout r = ZUi.box(this, LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(12), 0, dp(12));
        r.addView(ZUi.text(this, label, 14, TXT, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = ZUi.text(this, cur == null || cur.isEmpty() ? "—" : cur, 14, ACC, true);
        v.setMaxWidth(dp(190));
        v.setSingleLine();
        r.addView(v);
        r.setOnClickListener(x -> askText(label, cur, "", s -> { set.accept(s.trim()); render(); }));
        c.addView(r);
    }

    private void navRow(LinearLayout c, String title, String sub, Runnable go) {
        c.addView(ZUi.row(this, null, title, sub, false, v -> go.run()));
    }

    // ================================================================== diálogos

    private void askText(String title, String init, String hint, Consumer<String> cb) {
        final EditText e = new EditText(this);
        e.setText(init == null ? "" : init);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0x66FFFFFF);
        e.setSelection(e.getText().length());
        FrameLayout w = new FrameLayout(this);
        w.setPadding(dp(20), dp(8), dp(20), 0);
        w.addView(e);
        dlg().setTitle(title).setView(w).setPositiveButton("OK", (x, y) -> cb.accept(e.getText().toString()))
            .setNegativeButton(T("Cancelar", "Cancel"), null).show();
    }

    private void askNumber(String title, int lo, int hi, int def, IntConsumer cb) {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(String.valueOf(Math.max(lo, Math.min(hi, def))));
        e.setTextColor(Color.WHITE);
        e.setSelection(e.getText().length());
        FrameLayout w = new FrameLayout(this);
        w.setPadding(dp(20), dp(8), dp(20), 0);
        w.addView(e);
        dlg().setTitle(title + "  (" + lo + " – " + hi + ")").setView(w).setPositiveButton("OK", (x, y) -> {
            int v;
            try { v = Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { v = def; }
            cb.accept(Math.max(lo, Math.min(hi, v)));
        }).setNegativeButton(T("Cancelar", "Cancel"), null).show();
    }

    /** Duración: número + unidad (ms, s, min, h). Devuelve algo como «2 s». */
    private void askTime(String title, String defUnit, Consumer<String> cb) {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText("1");
        e.setTextColor(Color.WHITE);
        e.setSelection(1);
        final String[] units = {"ms", "s", "min", "h"};
        final int[] sel = {java.util.Arrays.asList(units).indexOf(defUnit)};
        if (sel[0] < 0) sel[0] = 1;
        final LinearLayout row = ZUi.box(this, LinearLayout.HORIZONTAL);
        final TextView[] chips = new TextView[units.length];
        for (int i = 0; i < units.length; i++) {
            final int idx = i;
            chips[i] = ZUi.text(this, units[i], 13, TXT, true);
            chips[i].setGravity(Gravity.CENTER);
            chips[i].setPadding(dp(4), dp(10), dp(4), dp(10));
            chips[i].setOnClickListener(v -> {
                sel[0] = idx;
                for (int k = 0; k < chips.length; k++) chips[k].setBackground(ZUi.shape(this, k == idx ? ACC : HI, 10));
            });
            row.addView(chips[i], ZKit.lp(this, 0, -2, 1, 3, 8, 3, 0));
        }
        for (int k = 0; k < chips.length; k++) chips[k].setBackground(ZUi.shape(this, k == sel[0] ? ACC : HI, 10));
        LinearLayout w = ZUi.box(this, LinearLayout.VERTICAL);
        w.setPadding(dp(20), dp(8), dp(20), 0);
        w.addView(e);
        w.addView(row);
        dlg().setTitle(title).setView(w).setPositiveButton("OK", (x, y) -> {
            int v;
            try { v = Math.max(0, Integer.parseInt(e.getText().toString().trim())); } catch (Exception ex) { v = 1; }
            cb.accept(v + " " + units[sel[0]]);
        }).setNegativeButton(T("Cancelar", "Cancel"), null).show();
    }

    private void askClock(Consumer<String> cb) {
        new TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert, (tp, h, m) ->
            cb.accept(String.format("%02d:%02d", h, m)), 8, 0, true).show();
    }

    private void askColor(String title, int cur, boolean alpha, IntConsumer cb) {
        final EditText hex = new EditText(this);
        hex.setHint(alpha ? "AARRGGBB" : "RRGGBB");
        hex.setText(alpha ? String.format("%08X", cur) : String.format("%06X", cur & 0xFFFFFF));
        hex.setTextColor(Color.WHITE);
        hex.setHintTextColor(0x66FFFFFF);
        LinearLayout w = ZUi.box(this, LinearLayout.VERTICAL);
        w.setPadding(dp(20), dp(8), dp(20), 0);
        w.addView(hex);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout sw = ZUi.box(this, LinearLayout.HORIZONTAL);
        for (int i = 0; i < BuilderCatalog.COLOR_VAL.length; i++) {
            final int col = BuilderCatalog.COLOR_VAL[i];
            if (col == 0 && !alpha) continue;
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(col);
            g.setCornerRadius(dp(8));
            g.setStroke(dp(1), 0x66FFFFFF);
            v.setBackground(g);
            v.setOnClickListener(x -> hex.setText(alpha ? String.format("%08X", col) : String.format("%06X", col & 0xFFFFFF)));
            sw.addView(v, ZKit.lp(this, dp(36), dp(36), 0, 0, 10, 8, 0));
        }
        hs.addView(sw);
        w.addView(hs);
        dlg().setTitle(title).setView(w).setPositiveButton("OK", (x, y) -> {
            try {
                String h = hex.getText().toString().trim().replace("#", "");
                int c = Color.parseColor("#" + h);
                cb.accept(alpha ? c : (0xFF000000 | (c & 0xFFFFFF)));
            } catch (Exception ex) { Toast.makeText(this, T("Color inválido", "Invalid color"), Toast.LENGTH_SHORT).show(); }
        }).setNegativeButton(T("Cancelar", "Cancel"), null).show();
    }

    private void pickList(String title, String[] items, IntConsumer cb) {
        dlg().setTitle(title).setItems(items, (x, i) -> cb.accept(i)).setNegativeButton(T("Cancelar", "Cancel"), null).show();
    }

    /** Elegir un nombre de una lista, o escribir otro. */
    private void pickName(String title, List<String> names, Consumer<String> cb) {
        if (names.isEmpty()) { askText(title, "", "", cb); return; }
        final String[] items = new String[names.size() + 1];
        for (int i = 0; i < names.size(); i++) items[i] = names.get(i);
        items[names.size()] = "✎ " + T("Escribir otro…", "Type another…");
        pickList(title, items, i -> { if (i < names.size()) cb.accept(names.get(i)); else askText(title, "", "", cb); });
    }

    private void pickApp(Consumer<String> cb) {
        List<String> names = new ArrayList<>();
        for (ApplicationManager.App a : am.all()) if (a.label != null) names.add(a.label);
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        pickName(T("Elegí una app", "Pick an app"), names, cb);
    }

    private List<String> catNames() {
        List<String> l = new ArrayList<>();
        for (CategoryManager.Category c : cm.list()) l.add(c.name);
        return l;
    }

    private List<String> panelNames() {
        List<String> l = new ArrayList<>();
        for (VisualModel.Panel p : vm.panels) if (!p.name.isEmpty()) l.add(p.name);
        return l;
    }

    private List<String> macroNames() {
        List<String> l = new ArrayList<>();
        for (VisualModel.Rule r : vm.rules) if (r.macro && !r.name.isEmpty()) l.add(r.name);
        return l;
    }

    private List<String> btnNames() {
        List<String> l = new ArrayList<>();
        for (VisualModel.Btn b : vm.buttons) if (!b.name.isEmpty()) l.add(b.name);
        return l;
    }

    // ---------------------------------------------------------------- parámetros de plantillas

    private void pickSetting(String kinds, Consumer<BuilderCatalog.Set> cb) {
        final List<BuilderCatalog.Set> l = new ArrayList<>();
        for (BuilderCatalog.Set x : BuilderCatalog.SETS) if (kinds.isEmpty() || kinds.contains(x.kind)) l.add(x);
        String[] n = new String[l.size()];
        for (int i = 0; i < n.length; i++) n[i] = BuilderCatalog.label(l.get(i), es);
        pickList(T("Elegí el ajuste", "Pick the setting"), n, i -> cb.accept(l.get(i)));
    }

    private void askSetValue(final BuilderCatalog.Set x, final Consumer<String> cb) {
        String label = BuilderCatalog.label(x, es);
        switch (x.kind) {
            case "n": askNumber(label, x.lo, x.hi, st.get(x.key), v -> cb.accept(String.valueOf(v))); break;
            case "b": pickList(label, new String[]{T("Sí", "Yes"), "No"}, i -> cb.accept(i == 0 ? T("si", "yes") : "no")); break;
            case "e": pickList(label, es ? x.eEs : x.eEn, i -> cb.accept((es ? x.eEs : x.eEn)[i])); break;
            default: askColor(label, st.get(x.key), x.kind.equals("a"), col -> {
                String name = null;
                for (int i = 0; i < BuilderCatalog.COLOR_VAL.length; i++)
                    if (BuilderCatalog.COLOR_VAL[i] == col) name = es ? BuilderCatalog.COLOR_ES[i] : BuilderCatalog.COLOR_EN[i];
                cb.accept(name != null ? name : (x.kind.equals("a") ? String.format("#%08X", col) : String.format("#%06X", col & 0xFFFFFF)));
            });
        }
    }

    private void askCond(Consumer<String> cb) {
        final String[] vars = es ? BuilderCatalog.VARS_ES : BuilderCatalog.VARS_EN;
        String[] items = new String[vars.length + 1];
        System.arraycopy(vars, 0, items, 0, vars.length);
        items[vars.length] = "✎ " + T("Mi variable…", "My variable…");
        pickList(T("¿Qué comparar?", "What to compare?"), items, i -> {
            if (i == vars.length) { askText(T("Nombre de la variable", "Variable name"), "", "", n -> askOp(n.trim().replace(' ', '_'), cb)); return; }
            if (BuilderCatalog.VARS_EN[i].equals("charging"))
                pickList(vars[i], new String[]{T("Sí", "Yes"), "No"}, k -> cb.accept(k == 0 ? vars[i] : (es ? "no " : "not ") + vars[i]));
            else askOp(vars[i], cb);
        });
    }

    private void askOp(final String var, final Consumer<String> cb) {
        pickList(var, new String[]{T("menor que  <", "less than  <"), T("mayor que  >", "greater than  >"), T("igual a  =", "equal to  =")}, i ->
            askText(var + " " + (i == 0 ? "<" : i == 1 ? ">" : "="), "", T("valor", "value"), v -> cb.accept(var + " " + (i == 0 ? "<" : i == 1 ? ">" : "=") + " " + v.trim())));
    }

    private void askParam(String spec, final BuilderCatalog.Tpl tpl, final Consumer<String> cb) {
        String[] sp = spec.split(":");
        switch (sp[0]) {
            case "app": pickApp(n -> cb.accept(q(n))); break;
            case "cat": pickName(T("Categoría", "Category"), catNames(), n -> cb.accept(q(n))); break;
            case "panel": pickName(T("Panel", "Panel"), panelNames(), n -> cb.accept(q(n))); break;
            case "macro": pickName(T("Macro", "Macro"), macroNames(), n -> cb.accept(q(n))); break;
            case "btn": pickName(T("Botón flotante", "Floating button"), btnNames(), n -> cb.accept(q(n))); break;
            case "text": askText(T("Texto", "Text"), "", "", s -> cb.accept(q(s))); break;
            case "raw": askText(T("Línea de script", "Script line"), "", "", cb); break;
            case "num": {
                int lo = sp.length > 1 ? Integer.parseInt(sp[1]) : 0, hi = sp.length > 2 ? Integer.parseInt(sp[2]) : 9999;
                askNumber(T("Número", "Number"), lo, hi, lo, v -> cb.accept(String.valueOf(v)));
                break;
            }
            case "time": {
                String t = tpl.tEs.startsWith("cada") ? "min" : (tpl.tEs.contains("vibrar") || tpl.tEs.contains("mantengo")) ? "ms" : "s";
                askTime(T("¿Cuánto tiempo?", "How long?"), t, cb);
                break;
            }
            case "clock": askClock(cb); break;
            case "dir": pickList(T("Dirección", "Direction"), es ? BuilderCatalog.DIR_ES : BuilderCatalog.DIR_EN,
                i -> cb.accept((es ? BuilderCatalog.DIR_ES : BuilderCatalog.DIR_EN)[i])); break;
            case "edge": pickList(T("Borde", "Edge"), es ? BuilderCatalog.EDGE_ES : BuilderCatalog.EDGE_EN,
                i -> cb.accept((es ? BuilderCatalog.EDGE_ES : BuilderCatalog.EDGE_EN)[i])); break;
            case "anim": pickList(T("Animación", "Animation"), es ? BuilderCatalog.ANIM_ES : BuilderCatalog.ANIM_EN,
                i -> cb.accept((es ? BuilderCatalog.ANIM_ES : BuilderCatalog.ANIM_EN)[i])); break;
            case "name": askText(T("Nombre de la variable", "Variable name"), "", "", n -> cb.accept(n.trim().replaceAll("[^\\p{L}\\p{N}_]", "_"))); break;
            case "cond": askCond(cb); break;
            case "sv": pickSetting("", x -> askSetValue(x, v -> cb.accept((es ? x.es : x.en) + " " + v))); break;
            case "setn": pickSetting("n", x -> askNumber(BuilderCatalog.label(x, es), 0, 9999, 1, v -> cb.accept((es ? x.es : x.en) + " " + v))); break;
            case "setb": pickSetting("b", x -> cb.accept(es ? x.es : x.en)); break;
            default: pickSetting("", x -> cb.accept(es ? x.es : x.en));
        }
    }

    private void collect(final BuilderCatalog.Tpl tpl, final String[] specs, final int i, final String[] vals, final Consumer<String[]> done) {
        if (i >= specs.length || tpl.specs.isEmpty()) { done.accept(vals); return; }
        askParam(specs[i], tpl, v -> { vals[i] = v; collect(tpl, specs, i + 1, vals, done); });
    }

    /** Asistente: categoría → plantilla → parámetros → línea de script ya escrita. */
    private void wizard(final List<BuilderCatalog.Tpl> all, final String title, final Consumer<BuilderCatalog.Tpl> chosen, final Consumer<String> done, final boolean withTpl) {
        final List<String> cats = new ArrayList<>(new LinkedHashSet<String>() {{ for (BuilderCatalog.Tpl t : all) add(t.cat(es)); }});
        pickList(title, cats.toArray(new String[0]), ci -> {
            final List<BuilderCatalog.Tpl> list = new ArrayList<>();
            for (BuilderCatalog.Tpl t : all) if (t.cat(es).equals(cats.get(ci))) list.add(t);
            String[] n = new String[list.size()];
            for (int i = 0; i < n.length; i++) n[i] = list.get(i).label(es);
            pickList(cats.get(ci), n, ti -> {
                final BuilderCatalog.Tpl t = list.get(ti);
                String[] specs = t.specs.isEmpty() ? new String[0] : t.specs.split(",");
                collect(t, specs, 0, new String[Math.max(1, specs.length)], vals -> {
                    if (chosen != null) chosen.accept(t);
                    done.accept(t.build(es, vals));
                });
            });
        });
    }

    // ================================================================== pantallas

    private String[] tabNames() {
        return new String[]{T("Escritorio", "Desktop"), "Dock", T("Cajón", "Drawer"), "Radial", T("Fondo", "Background"),
            T("Paneles", "Panels"), T("Botones", "Buttons"), T("Reglas", "Rules"), "Macros", T("Más", "More")};
    }

    private void goPage(int p) {
        subPanel = null; subBtn = null; subRule = null;
        page = p;
        previewMode(p == 2 ? 1 : p == 3 ? 2 : 0);
        render();
        scroll.scrollTo(0, 0);
    }

    private void buildTabs() {
        tabs.removeAllViews();
        String[] n = tabNames();
        boolean inSub = subPanel != null || subBtn != null || subRule != null;
        for (int i = 0; i < n.length; i++) {
            final int idx = i;
            boolean on = idx == page && !inSub;
            TextView p = ZUi.text(this, n[i], 12.5f, on ? DARK : TXT, true);
            p.setPadding(dp(14), dp(9), dp(14), dp(9));
            p.setBackground(ZUi.shape(this, on ? ACC : HI, 16));
            p.setOnClickListener(v -> goPage(idx));
            tabs.addView(p, ZKit.lp(this, -2, -2, 0, 0, 0, 6, 0));
        }
    }

    private void render() {
        controls.removeAllViews();
        buildTabs();
        if (subPanel != null) { panelEditor(subPanel); return; }
        if (subBtn != null) { btnEditor(subBtn); return; }
        if (subRule != null) { ruleEditor(subRule); return; }
        switch (page) {
            case 0: settingsPage("desk", T("Cuántas columnas, qué tan grandes los iconos y cómo se ven los nombres.", "How many columns, how big the icons and how names look.")); break;
            case 1: settingsPage("dock", T("La barra de abajo: tamaño, posición, color y el botón central.", "The bottom bar: size, position, color and the center button.")); break;
            case 2: settingsPage("drawer", T("El cajón de apps y desde dónde aparece.", "The app drawer and where it comes from.")); break;
            case 3: settingsPage("radial", T("El menú radial de dos etapas que sale del botón central.", "The two-stage radial menu that comes out of the center button.")); break;
            case 4: settingsPage("bg", T("Fondo del escritorio y color de acento.", "Desktop background and accent color.")); break;
            case 5: panelList(); break;
            case 6: btnList(); break;
            case 7: ruleList(false); break;
            case 8: ruleList(true); break;
            default: morePage();
        }
    }

    private void settingsPage(String grp, String hint) {
        LinearLayout c = card();
        note(c, hint, SUB);
        for (final BuilderCatalog.Set x : BuilderCatalog.SETS) {
            if (!x.grp.equals(grp)) continue;
            final String label = BuilderCatalog.label(x, es);
            switch (x.kind) {
                case "n": sliderRow(c, label, x.lo, x.hi, x.unit, () -> st.get(x.key), v -> { st.set(x.key, v); changedSetting(); }); break;
                case "b": toggleRow(c, label, st.get(x.key) == 1, v -> { st.set(x.key, v ? 1 : 0); changedSetting(); }); break;
                case "e": chipsRow(c, label, es ? x.eEs : x.eEn, Math.max(0, Math.min(x.hi, st.get(x.key))), i -> { st.set(x.key, i); changedSetting(); render(); }); break;
                default: colorRow(c, label, st.get(x.key), x.kind.equals("a"), col -> { st.set(x.key, col); changedSetting(); });
            }
        }
        TextView reset = mini(T("Restablecer esta página", "Reset this page"), () -> {
            for (BuilderCatalog.Set x : BuilderCatalog.SETS) if (x.grp.equals(grp)) st.reset(x.key);
            changedSetting();
            render();
        });
        c.addView(reset);
    }

    // ---------------------------------------------------------------- paneles

    private void panelList() {
        LinearLayout c = card();
        note(c, T("Un panel es una bandeja de apps y acciones que se abre desde donde quieras, con la animación que elijas. Abrilo con una regla o un botón flotante.",
            "A panel is a tray of apps and actions that opens from wherever you want, with the animation you pick. Open it from a rule or a floating button."), SUB);
        for (int i = 0; i < vm.panels.size(); i++) {
            final VisualModel.Panel p = vm.panels.get(i);
            navRow(c, p.name.isEmpty() ? T("(sin nombre)", "(unnamed)") : p.name,
                p.items.size() + " " + T("elementos", "items") + " · " + p.from + " · " + p.anim, () -> { subPanel = p; render(); });
        }
        bigButton("+ " + T("Nuevo panel", "New panel"), true, () -> askText(T("Nombre del panel", "Panel name"), "", "", n -> {
            if (n.trim().isEmpty()) return;
            VisualModel.Panel p = new VisualModel.Panel();
            p.name = n.trim();
            vm.panels.add(p);
            subPanel = p;
            commit();
        }));
    }

    private void panelEditor(final VisualModel.Panel p) {
        backRow(T("Paneles", "Panels"));
        LinearLayout c = card();
        textRow(c, T("Nombre", "Name"), p.name, s -> { p.name = s; commit(); });
        textRow(c, T("Título", "Title"), p.title, s -> { p.title = s; commit(); });
        final String[] dk = {"auto", "left", "right", "up", "down", "center"};
        chipsRow(c, T("Aparece desde", "Comes from"), new String[]{"Auto", T("Izq", "Left"), T("Der", "Right"), T("Arr", "Top"), T("Aba", "Bottom"), T("Centro", "Center")},
            java.util.Arrays.asList(dk).indexOf(p.from), i -> { p.from = dk[i]; commit(); });
        chipsRow(c, T("Animación", "Animation"), es ? BuilderCatalog.ANIM_ES : BuilderCatalog.ANIM_EN,
            java.util.Arrays.asList(BuilderCatalog.ANIM_KEYS).indexOf(p.anim), i -> { p.anim = BuilderCatalog.ANIM_KEYS[i]; commit(); });
        sliderRow(c, T("Duración", "Duration"), 0, 1500, " ms", () -> p.dur, v -> { p.dur = v; touched(); });
        sliderRow(c, T("Columnas", "Columns"), 1, 8, "", () -> p.cols, v -> { p.cols = v; touched(); });
        sliderRow(c, T("Tamaño de iconos", "Icon size"), 24, 96, " dp", () -> p.isize, v -> { p.isize = v; touched(); });
        sliderRow(c, T("Redondeo", "Corner radius"), 0, 60, " dp", () -> p.radius, v -> { p.radius = v; touched(); });
        sliderRow(c, T("Ancho (0 = automático)", "Width (0 = auto)"), 0, 600, " dp", () -> Math.max(0, p.w), v -> { p.w = v == 0 ? -1 : v; touched(); });
        sliderRow(c, T("Alto (0 = automático)", "Height (0 = auto)"), 0, 800, " dp", () -> Math.max(0, p.h), v -> { p.h = v == 0 ? -1 : v; touched(); });
        colorRow(c, T("Color de fondo", "Background color"), p.bg, true, col -> { p.bg = col; touched(); });
        colorRow(c, T("Color del texto", "Text color"), p.tcolor, true, col -> { p.tcolor = col; touched(); });
        toggleRow(c, T("Cerrar al tocar fuera", "Close on outside tap"), p.outside, v -> { p.outside = v; touched(); });
        toggleRow(c, T("Cerrar al elegir algo", "Close after choosing"), p.autoclose, v -> { p.autoclose = v; touched(); });

        section(T("Elementos", "Items"));
        LinearLayout ic = card();
        if (p.items.isEmpty()) note(ic, T("Todavía no hay elementos.", "No items yet."), SUB);
        for (int i = 0; i < p.items.size(); i++) {
            final int idx = i;
            final VisualModel.Item it = p.items.get(i);
            LinearLayout r = ZUi.box(this, LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(8), 0, dp(8));
            String kind = it.kind.equals("app") ? "📱 " : it.kind.equals("category") ? "📁 " : (it.icon.isEmpty() ? "⚡ " : it.icon + " ");
            String sub = it.kind.equals("action") ? "  → " + it.tkind + (it.target.isEmpty() ? "" : " " + it.target) : "";
            r.addView(ZUi.text(this, kind + it.name + sub, 13.5f, TXT, false), new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(mini("▲", () -> { if (idx > 0) { Collections.swap(p.items, idx, idx - 1); commit(); } }));
            r.addView(mini("▼", () -> { if (idx < p.items.size() - 1) { Collections.swap(p.items, idx, idx + 1); commit(); } }));
            r.addView(mini("✕", () -> { p.items.remove(idx); commit(); }));
            ic.addView(r);
        }
        LinearLayout add = ZUi.box(this, LinearLayout.HORIZONTAL);
        add.setPadding(0, dp(6), 0, dp(8));
        add.addView(mini("+ App", () -> pickApp(n -> { VisualModel.Item it = new VisualModel.Item(); it.kind = "app"; it.name = n; p.items.add(it); commit(); })));
        add.addView(mini("+ " + T("Categoría", "Category"), () -> pickName(T("Categoría", "Category"), catNames(), n -> {
            VisualModel.Item it = new VisualModel.Item(); it.kind = "category"; it.name = n; p.items.add(it); commit(); })));
        add.addView(mini("+ " + T("Acción", "Action"), () -> addPanelAction(p)));
        ic.addView(add);
        bigButton(T("Borrar este panel", "Delete this panel"), false, () -> confirm(T("¿Borrar el panel?", "Delete this panel?"), () -> {
            vm.panels.remove(p);
            subPanel = null;
            commit();
        }));
    }

    private void addPanelAction(final VisualModel.Panel p) {
        askText(T("Nombre del botón", "Button name"), "", "", name -> askText(T("Ícono (un emoji o símbolo)", "Icon (an emoji or symbol)"), "⚡", "", icon ->
            pickList(T("¿Qué hace?", "What does it do?"), new String[]{T("Ejecuta una macro", "Runs a macro"), T("Abre una app", "Opens an app"),
                T("Abre otro panel", "Opens another panel"), T("Comando shell", "Shell command")}, k -> {
                final String[] kinds = {"macro", "app", "panel", "shell"};
                final String[] specs = {"macro", "app", "panel", "text"};
                BuilderCatalog.Tpl dummy = new BuilderCatalog.Tpl();
                dummy.tEs = ""; dummy.tEn = "";
                askParam(specs[k], dummy, v -> {
                    VisualModel.Item it = new VisualModel.Item();
                    it.kind = "action";
                    it.name = name.trim();
                    it.icon = icon.trim();
                    it.tkind = kinds[k];
                    it.target = v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2 ? v.substring(1, v.length() - 1) : v;
                    p.items.add(it);
                    commit();
                });
            })));
    }

    // ---------------------------------------------------------------- botones flotantes

    private void btnList() {
        LinearLayout c = card();
        note(c, T("Botones que flotan sobre el launcher, donde los pongas. Después usá una regla «cuando toco el botón…» para darles vida.",
            "Buttons that float over the launcher wherever you put them. Then use a rule 'when I tap the button…' to bring them to life."), SUB);
        for (final VisualModel.Btn b : vm.buttons)
            navRow(c, (b.icon.isEmpty() ? "●" : b.icon) + "  " + (b.name.isEmpty() ? T("(sin nombre)", "(unnamed)") : b.name),
                b.v + " " + b.hz + " · " + b.size + " dp" + (b.visible ? "" : " · " + T("oculto", "hidden")), () -> { subBtn = b; render(); });
        bigButton("+ " + T("Nuevo botón", "New button"), true, () -> askText(T("Nombre del botón", "Button name"), "", "", n -> {
            if (n.trim().isEmpty()) return;
            VisualModel.Btn b = new VisualModel.Btn();
            b.name = n.trim();
            vm.buttons.add(b);
            subBtn = b;
            commit();
        }));
    }

    private void btnEditor(final VisualModel.Btn b) {
        backRow(T("Botones", "Buttons"));
        LinearLayout c = card();
        textRow(c, T("Nombre", "Name"), b.name, s -> { b.name = s; commit(); });
        textRow(c, T("Ícono (emoji o símbolo)", "Icon (emoji or symbol)"), b.icon, s -> { b.icon = s; commit(); });
        textRow(c, T("Texto debajo del ícono", "Text under the icon"), b.label, s -> { b.label = s; commit(); });
        LinearLayout ar = ZUi.box(this, LinearLayout.HORIZONTAL);
        ar.setGravity(Gravity.CENTER_VERTICAL);
        ar.setPadding(0, dp(10), 0, dp(10));
        ar.addView(ZUi.text(this, T("Mostrar el ícono de una app", "Show an app's icon"), 14, TXT, false), new LinearLayout.LayoutParams(0, -2, 1));
        ar.addView(mini(b.app.isEmpty() ? T("Elegir", "Pick") : b.app, () -> pickApp(n -> { b.app = n; commit(); })));
        if (!b.app.isEmpty()) ar.addView(mini("✕", () -> { b.app = ""; commit(); }));
        c.addView(ar);
        final String[] vk = {"up", "center", "down"}, hk = {"left", "center", "right"};
        chipsRow(c, T("Vertical", "Vertical"), new String[]{T("Arriba", "Top"), T("Centro", "Center"), T("Abajo", "Bottom")},
            java.util.Arrays.asList(vk).indexOf(b.v), i -> { b.v = vk[i]; commit(); });
        chipsRow(c, T("Horizontal", "Horizontal"), new String[]{T("Izquierda", "Left"), T("Centro", "Center"), T("Derecha", "Right")},
            java.util.Arrays.asList(hk).indexOf(b.hz), i -> { b.hz = hk[i]; commit(); });
        sliderRow(c, T("Desplazamiento X", "Offset X"), 0, 300, " dp", () -> b.x, v -> { b.x = v; touched(); });
        sliderRow(c, T("Desplazamiento Y", "Offset Y"), 0, 600, " dp", () -> b.y, v -> { b.y = v; touched(); });
        sliderRow(c, T("Tamaño", "Size"), 24, 120, " dp", () -> b.size, v -> { b.size = v; touched(); });
        sliderRow(c, T("Opacidad", "Opacity"), 10, 100, " %", () -> b.opacity, v -> { b.opacity = v; touched(); });
        final String[] sk = {"circle", "round", "square"};
        chipsRow(c, T("Forma", "Shape"), new String[]{T("Círculo", "Circle"), T("Suave", "Rounded"), T("Cuadrado", "Square")},
            java.util.Arrays.asList(sk).indexOf(b.shape), i -> { b.shape = sk[i]; commit(); });
        colorRow(c, T("Color de fondo", "Background color"), b.bg, true, col -> { b.bg = col; touched(); });
        colorRow(c, T("Color del ícono", "Icon color"), b.tcolor, true, col -> { b.tcolor = col; touched(); });
        toggleRow(c, T("Visible", "Visible"), b.visible, v -> { b.visible = v; touched(); });
        bigButton(T("Crear regla: cuando toco este botón", "Create rule: when I tap this button"), true, () -> {
            VisualModel.Rule r = new VisualModel.Rule();
            r.head = (es ? "cuando toco el boton " : "when I tap the button ") + q(b.name);
            vm.rules.add(r);
            subBtn = null;
            subRule = r;
            commit();
        });
        bigButton(T("Borrar este botón", "Delete this button"), false, () -> confirm(T("¿Borrar el botón?", "Delete this button?"), () -> {
            vm.buttons.remove(b);
            subBtn = null;
            commit();
        }));
    }

    // ---------------------------------------------------------------- reglas y macros

    private void checkCard() {
        ScriptLang.ES = es;
        ScriptLang.Program pg = new ScriptLang.Program();
        ScriptLang.parse(vm.toScript(es), pg);
        LinearLayout c = card();
        if (pg.errors.isEmpty()) {
            note(c, "✓ " + T("Sin errores: ", "No errors: ") + pg.rules.size() + " " + T("reglas", "rules") + ", " + pg.macros.size() + " macros, "
                + pg.panels.size() + " " + T("paneles", "panels") + ", " + pg.buttons.size() + " " + T("botones", "buttons"), OKC);
            return;
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (ScriptLang.Err e : pg.errors) {
            if (n++ >= 4) { sb.append("…"); break; }
            sb.append(T("Línea ", "Line ")).append(e.line).append(": ").append(e.msg).append('\n');
        }
        note(c, "⚠ " + sb.toString().trim() + "\n" + T("(líneas del script generado, visible en Más → Ver script)", "(lines of the generated script, see More → View script)"), BAD);
    }

    private void ruleList(final boolean macros) {
        LinearLayout c = card();
        note(c, macros
            ? T("Una macro es una secuencia con nombre que podés llamar desde reglas, paneles u otras macros.", "A macro is a named sequence you can call from rules, panels or other macros.")
            : T("Una regla dice: «cuando pasa X, hacé Y». Podés usar esperas, repeticiones y condiciones.", "A rule says: 'when X happens, do Y'. You can use waits, repeats and conditions."), SUB);
        boolean any = false;
        for (final VisualModel.Rule r : vm.rules) {
            if (r.macro != macros) continue;
            any = true;
            LinearLayout row = ZUi.box(this, LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));
            LinearLayout col = ZUi.box(this, LinearLayout.VERTICAL);
            col.addView(ZUi.text(this, macros ? r.name : r.head, 14, r.on ? TXT : SUB, true));
            col.addView(ZUi.text(this, r.steps.size() + " " + T("pasos", "steps"), 12, SUB, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            Switch sw = new Switch(this);
            sw.setChecked(r.on);
            sw.setOnCheckedChangeListener((v, on) -> { r.on = on; touched(); });
            row.addView(sw);
            row.setOnClickListener(v -> { subRule = r; render(); });
            c.addView(row);
        }
        if (!any) note(c, T("Todavía no hay ninguna.", "None yet."), SUB);
        bigButton("+ " + (macros ? T("Nueva macro", "New macro") : T("Nueva regla", "New rule")), true, () -> {
            if (macros) {
                askText(T("Nombre de la macro", "Macro name"), "", "", n -> {
                    if (n.trim().isEmpty()) return;
                    VisualModel.Rule r = new VisualModel.Rule();
                    r.macro = true;
                    r.name = n.trim();
                    vm.rules.add(r);
                    subRule = r;
                    commit();
                });
            } else {
                wizard(BuilderCatalog.EVENTS, T("¿Cuándo?", "When?"), null, head -> {
                    VisualModel.Rule r = new VisualModel.Rule();
                    r.head = head;
                    vm.rules.add(r);
                    subRule = r;
                    commit();
                }, true);
            }
        });
        checkCard();
    }

    private void ruleEditor(final VisualModel.Rule r) {
        backRow(r.macro ? "Macros" : T("Reglas", "Rules"));
        LinearLayout c = card();
        if (r.macro) {
            textRow(c, T("Nombre de la macro", "Macro name"), r.name, s -> { r.name = s; commit(); });
            note(c, T("Se llama con «ejecutar \"", "Call it with 'run \"") + r.name + T("\"» desde otra regla, macro o botón de un panel.", "\"' from another rule, macro or panel button."), SUB);
        } else {
            LinearLayout h = ZUi.box(this, LinearLayout.VERTICAL);
            h.setPadding(0, dp(10), 0, dp(6));
            h.addView(ZUi.text(this, T("CUANDO", "WHEN"), 11, SUB, true));
            TextView t = ZUi.text(this, r.head, 15, 0xFFFFD27A, true);
            t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            t.setPadding(0, dp(4), 0, dp(8));
            h.addView(t);
            LinearLayout b = ZUi.box(this, LinearLayout.HORIZONTAL);
            b.addView(mini(T("Cambiar evento", "Change event"), () -> wizard(BuilderCatalog.EVENTS, T("¿Cuándo?", "When?"), null, head -> { r.head = head; commit(); }, true)));
            b.addView(mini("✎ " + T("Editar texto", "Edit text"), () -> askText(T("Evento", "Event"), r.head, "", s -> { r.head = s.trim(); commit(); })));
            h.addView(b);
            c.addView(h);
        }
        toggleRow(c, T("Activa", "Enabled"), r.on, v -> { r.on = v; touched(); });

        section(T("Hacer, en orden", "Do, in order"));
        LinearLayout sc = card();
        if (r.steps.isEmpty()) note(sc, T("Sin pasos todavía. Agregá la primera acción.", "No steps yet. Add the first action."), SUB);
        stepRows(sc, r.steps, 0);
        sc.addView(addButton(r.steps, 0));
        checkCard();
        bigButton(T("Borrar", "Delete"), false, () -> confirm(T("¿Borrar esto?", "Delete this?"), () -> { vm.rules.remove(r); subRule = null; commit(); }));
    }

    private View addButton(final List<VisualModel.Step> list, int depth) {
        LinearLayout r = ZUi.box(this, LinearLayout.HORIZONTAL);
        r.setPadding(dp(depth * 16), dp(6), 0, dp(8));
        r.addView(mini("+ " + T("acción", "action"), () -> wizard(BuilderCatalog.ACTIONS, T("¿Qué hacer?", "What to do?"), null, line -> {
            VisualModel.Step s = new VisualModel.Step();
            s.text = line;
            for (BuilderCatalog.Tpl t : BuilderCatalog.ACTIONS) if (t.block && matchesBlock(line, t)) s.block = true;
            String low = line.toLowerCase().trim();
            for (String k : new String[]{"si ", "if ", "mientras ", "while ", "repetir ", "repeat "}) if (low.startsWith(k)) s.block = true;
            list.add(s);
            commit();
        }, true)));
        return r;
    }

    private boolean matchesBlock(String line, BuilderCatalog.Tpl t) {
        String l = line.toLowerCase();
        String head = (es ? t.tEs : t.tEn).split("\\{")[0].trim().toLowerCase();
        return !head.isEmpty() && l.startsWith(head);
    }

    private static boolean isIf(VisualModel.Step s) {
        String l = s.text.toLowerCase();
        return s.block && (l.startsWith("si ") || l.startsWith("if "));
    }

    private void stepRows(LinearLayout parent, final List<VisualModel.Step> list, int depth) {
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            final VisualModel.Step s = list.get(i);
            LinearLayout r = ZUi.box(this, LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(depth * 16), dp(4), 0, dp(4));
            TextView t = ZUi.text(this, s.text, 12.5f, s.block ? 0xFFFFD27A : 0xFFE8ECFF, false);
            t.setTypeface(Typeface.MONOSPACE);
            t.setOnClickListener(v -> askText(T("Editar línea", "Edit line"), s.text, "", x -> { s.text = x.trim(); commit(); }));
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(mini("▲", () -> { if (idx > 0) { Collections.swap(list, idx, idx - 1); commit(); } }));
            r.addView(mini("▼", () -> { if (idx < list.size() - 1) { Collections.swap(list, idx, idx + 1); commit(); } }));
            r.addView(mini("✕", () -> { list.remove(idx); commit(); }));
            parent.addView(r);
            if (!s.block) continue;
            stepRows(parent, s.kids, depth + 1);
            parent.addView(addButton(s.kids, depth + 1));
            if (isIf(s)) {
                if (s.alt == null) {
                    LinearLayout er = ZUi.box(this, LinearLayout.HORIZONTAL);
                    er.setPadding(dp(depth * 16), 0, 0, dp(4));
                    er.addView(mini("+ " + T("sino", "else"), () -> { s.alt = new ArrayList<>(); commit(); }));
                    parent.addView(er);
                } else {
                    LinearLayout er = ZUi.box(this, LinearLayout.HORIZONTAL);
                    er.setGravity(Gravity.CENTER_VERTICAL);
                    er.setPadding(dp(depth * 16), dp(2), 0, dp(2));
                    TextView et = ZUi.text(this, T("sino", "else"), 12.5f, 0xFFFFD27A, false);
                    et.setTypeface(Typeface.MONOSPACE);
                    er.addView(et, new LinearLayout.LayoutParams(0, -2, 1));
                    er.addView(mini("✕", () -> { s.alt = null; commit(); }));
                    parent.addView(er);
                    stepRows(parent, s.alt, depth + 1);
                    parent.addView(addButton(s.alt, depth + 1));
                }
            }
            TextView end = ZUi.text(this, T("fin", "end"), 12.5f, 0xFFFFD27A, false);
            end.setTypeface(Typeface.MONOSPACE);
            end.setPadding(dp(depth * 16), 0, 0, dp(4));
            parent.addView(end);
        }
    }

    // ---------------------------------------------------------------- más

    private void morePage() {
        section(T("Automatización", "Automation"));
        LinearLayout c = card();
        toggleRow(c, T("Scripts y reglas activos", "Scripts and rules enabled"), ScriptStore.master(this), v -> ScriptStore.setMaster(this, v));
        note(c, T("Es el interruptor general: apaga todas las reglas, macros, paneles y botones de una vez.", "The master switch: turns every rule, macro, panel and button off at once."), SUB);
        c.addView(ZUi.divider(this), new LinearLayout.LayoutParams(-1, 1));
        navRow(c, "🧬 " + T("Mi launcher en código", "My launcher as code"), T("Escribí el launcher entero: elementos, gestos, animaciones", "Write the whole launcher: elements, gestures, animations"),
            () -> { flush(); startActivity(new Intent(this, UiEditorActivity.class)); });
        navRow(c, T("Ver / editar el script generado", "View / edit the generated script"), T("Texto legible en español o inglés", "Readable text in Spanish or English"),
            () -> { flush(); startActivity(new Intent(this, ScriptEditorActivity.class)); });
        navRow(c, T("Copiar el script generado", "Copy the generated script"), null, () -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("zc-script", vm.toScript(es)));
            Toast.makeText(this, T("Copiado", "Copied"), Toast.LENGTH_SHORT).show();
        });
        navRow(c, T("Probar en el launcher real", "Try it in the real launcher"), null, () -> { flush(); startActivity(new Intent(this, LauncherActivity.class)); });
        section(T("Lo que no es visual", "Beyond the visual tools"));
        LinearLayout n = card();
        note(n, T("Cualquier cosa que el lenguaje sepa hacer se puede agregar con «Línea de script libre» dentro de una regla o macro, o escribiendo un script completo en el editor de texto. Todo funciona igual en español e inglés.",
            "Anything the language can do can be added with 'Free script line' inside a rule or macro, or by writing a full script in the text editor. It all works the same in Spanish and English."), SUB);
        section(T("Restablecer", "Reset"));
        LinearLayout r = card();
        navRow(r, T("Restablecer todos los ajustes del launcher", "Reset all launcher settings"), T("Conserva apps fijadas, ocultas y dock", "Keeps pinned, hidden apps and dock"),
            () -> confirm(T("¿Restablecer los ajustes?", "Reset the settings?"), () -> { st.resetAll(); changedSetting(); Toast.makeText(this, "OK", Toast.LENGTH_SHORT).show(); }));
        navRow(r, T("Borrar reglas, macros, paneles y botones", "Delete rules, macros, panels and buttons"), T("Solo lo hecho con este constructor", "Only what was made with this builder"),
            () -> confirm(T("¿Borrar todo lo visual?", "Delete everything visual?"), () -> {
                vm.rules.clear(); vm.panels.clear(); vm.buttons.clear();
                commit();
            }));
    }

    // ---------------------------------------------------------------- comunes

    private void backRow(String where) {
        TextView b = ZUi.text(this, "‹  " + where, 14, SUB, true);
        b.setPadding(0, dp(8), dp(20), dp(8));
        b.setOnClickListener(v -> goBack());
        controls.addView(b);
    }

    private void confirm(String msg, Runnable yes) {
        dlg().setMessage(msg).setPositiveButton(T("Sí", "Yes"), (x, y) -> yes.run()).setNegativeButton(T("Cancelar", "Cancel"), null).show();
    }
}
