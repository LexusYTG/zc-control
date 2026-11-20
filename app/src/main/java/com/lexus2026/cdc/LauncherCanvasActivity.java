package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Editor visual manual del launcher (lo que el constructor por barras no hace): el launcher real a tamaño de pantalla, y encima
 * manijas para tocar, arrastrar y cambiar el tamaño de TODO directamente: dock, botón central, cajón, escritorio, origen del radial,
 * botones flotantes y elementos libres (apps, textos, relojes, imágenes, widgets, atajos, botones de acción y formas).
 * Cada elemento libre acepta toque, doble toque, mantener y cuatro deslizamientos, y cada gesto puede abrir una app, un cajón,
 * un panel, ejecutar una macro (con esperas, bucles y condiciones, hechas en el constructor) o lanzar un comando.
 * Los ajustes van a {@link LauncherSettings}; elementos libres y botones, a {@link VisualModel}. Hay deshacer y rehacer.
 */
public class LauncherCanvasActivity extends Activity {
    private static final int BG = ZUi.BG, HI = ZUi.CARD_HI, TXT = ZUi.TXT, SUB = ZUi.SUB, ACC = ZUi.ACC, BAD = 0xFFF87171;
    private static final int K_FREE = 0, K_BTN = 1, K_DOCK = 2, K_HOME = 3, K_DRAWER = 4, K_DESK = 5, K_RADIAL = 6;
    private static final int REQ_IMG = 61, REQ_BIND = 62, REQ_CFG = 63;
    private static final String[] KEYS = {LauncherSettings.DOCK_POS, LauncherSettings.DOCK_X, LauncherSettings.DOCK_Y, LauncherSettings.DOCK_W,
        LauncherSettings.DOCK_H, LauncherSettings.HOME_SIZE, LauncherSettings.DOCK_ICON, LauncherSettings.DOCK_RADIUS, LauncherSettings.DOCK_COLOR,
        LauncherSettings.DOCK_SHOW, LauncherSettings.DRAWER_W, LauncherSettings.DRAWER_X, LauncherSettings.DRAWER_Y, LauncherSettings.DRAWER_H,
        LauncherSettings.DRAWER_COLS, LauncherSettings.DRAWER_COLOR, LauncherSettings.DRAWER_RADIUS, LauncherSettings.DRAWER_FROM,
        LauncherSettings.DESK_L, LauncherSettings.DESK_T, LauncherSettings.DESK_R, LauncherSettings.DESK_B, LauncherSettings.DESK_COLS,
        LauncherSettings.DESK_GAP, LauncherSettings.APP_ICON, LauncherSettings.LABELS, LauncherSettings.RAD_X, LauncherSettings.RAD_Y,
        LauncherSettings.BUBBLE, LauncherSettings.ARC, LauncherSettings.RING1, LauncherSettings.RING2};

    private static final class Obj {
        final int kind;
        final Object ref;
        final int[] r;
        final String name;

        Obj(int kind, Object ref, int[] r, String name) { this.kind = kind; this.ref = ref; this.r = r; this.name = name; }
    }

    private LauncherSettings st;
    private VisualModel vm;
    private LauncherEngine engine;
    private CategoryManager cm;
    private FrameLayout stage, host, btnLayer;
    private Overlay overlay;
    private LinearLayout props, palette;
    private TextView info, snapChip;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private boolean es, dirty, snap = true;
    private float d, sc = 1f;
    private int mode, selKind = -1, pvT, pvB;
    private Object selRef;
    private int guideX = -1, guideY = -1;
    private AppWidgetProviderInfo pendingInfo;
    private int pendingWidget = -1;
    private boolean pendingReplace;

    private final Runnable saveRun = this::flush;
    private final Runnable applyRun = () -> {
        engine.applyAll();
        if (mode == 2) { engine.previewRadial(false); engine.previewRadial(true); }
        overlay.invalidate();
    };

    // ================================================================== ciclo de vida

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Consent.granted(this)) { Consent.require(this, false); return; }
        d = getResources().getDisplayMetrics().density;
        es = SL.es(this);
        st = new LauncherSettings(this);
        vm = VisualModel.load(this);
        cm = new CategoryManager(this);
        pvT = sysDim("status_bar_height");
        pvB = sysDim("navigation_bar_height");

        LinearLayout root = ZUi.box(this, LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // ---- encabezado
        LinearLayout head = ZUi.box(this, LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(12), dp(8), dp(8), dp(2));
        TextView back = ZUi.text(this, "‹", 28, TXT, false);
        back.setPadding(0, 0, dp(12), dp(2));
        back.setOnClickListener(v -> goBack());
        head.addView(back);
        head.addView(ZUi.text(this, T("Editor visual", "Visual editor"), 18, TXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(headBtn("↶", this::doUndo));
        head.addView(headBtn("↷", this::doRedo));
        snapChip = headBtn("🧲", () -> { snap = !snap; snapChip.setAlpha(snap ? 1f : 0.35f); });
        head.addView(snapChip);
        head.addView(headBtn("⚙", () -> { flush(); startActivity(new Intent(this, LauncherBuilderActivity.class)); }));
        TextView play = headBtn("▶", () -> { flush(); startActivity(new Intent(this, LauncherActivity.class)); });
        play.setTextColor(ACC);
        head.addView(play);
        root.addView(head);

        LinearLayout modes = ZUi.box(this, LinearLayout.HORIZONTAL);
        modes.setGravity(Gravity.CENTER);
        modes.setPadding(dp(8), dp(2), dp(8), dp(4));
        String[] mn = {T("Escritorio", "Desktop"), T("Cajón", "Drawer"), "Radial"};
        for (int i = 0; i < 3; i++) {
            final int m = i;
            TextView c = ZUi.text(this, mn[i], 12, TXT, true);
            c.setPadding(dp(14), dp(6), dp(14), dp(6));
            c.setBackground(ZUi.shape(this, HI, 14));
            c.setOnClickListener(v -> setMode(m));
            modes.addView(c, ZKit.lp(this, -2, -2, 0, 3, 0, 3, 0));
        }
        root.addView(modes);

        // ---- escenario: el launcher real a tamaño de pantalla, reducido para que entre
        int W = W(), H = H();
        stage = new FrameLayout(this);
        stage.setBackgroundColor(0xFF05070D);
        host = new FrameLayout(this);
        host.setPivotX(0);
        host.setPivotY(0);
        View wall = new View(this);
        wall.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1B2A6B, 0xFF5B2A7B, 0xFF0B0F1A}));
        host.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        engine = new LauncherEngine(this, true);
        engine.setPreviewInsets(pvT, pvB);
        host.addView(engine.view(), new FrameLayout.LayoutParams(-1, -1));
        btnLayer = new FrameLayout(this);
        host.addView(btnLayer, new FrameLayout.LayoutParams(-1, -1));
        overlay = new Overlay();
        host.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(host, new FrameLayout.LayoutParams(W, H, Gravity.TOP | Gravity.START));
        stage.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, orr, ob) -> fit(r - l, bt - t));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        // ---- información, propiedades del elemento elegido y paleta para agregar
        info = ZUi.text(this, "", 12, SUB, false);
        info.setPadding(dp(14), dp(6), dp(14), dp(2));
        root.addView(info);
        props = ZUi.box(this, LinearLayout.HORIZONTAL);
        props.setPadding(dp(8), 0, dp(8), 0);
        HorizontalScrollView ps = new HorizontalScrollView(this);
        ps.setHorizontalScrollBarEnabled(false);
        ps.addView(props);
        root.addView(ps);

        palette = ZUi.box(this, LinearLayout.HORIZONTAL);
        palette.setPadding(dp(8), dp(2), dp(8), dp(8));
        palette.addView(chip("＋ App", () -> pickApp(pkg -> addFree("app", pkg))));
        palette.addView(chip("＋ " + T("Texto", "Text"), () -> askText(T("Texto", "Text"), "", t -> addFree("text", t))));
        palette.addView(chip("＋ " + T("Reloj", "Clock"), () -> addFree("clock", "")));
        palette.addView(chip("＋ " + T("Imagen", "Image"), () -> pickImage(false)));
        palette.addView(chip("＋ Widget", this::pickWidget));
        palette.addView(chip("＋ " + T("Atajo", "Shortcut"), () -> pickCategory(id -> addFree("category", id))));
        palette.addView(chip("＋ " + T("Botón", "Button"), () -> askText(T("Símbolo del botón (emoji o letra)", "Button symbol (emoji or letter)"), "★", g -> {
            VisualModel.Free f = newFree("action", "");
            f.icon = g;
            insertFree(f);
        })));
        palette.addView(chip("＋ " + T("Forma", "Shape"), () -> addFree("shape", "")));
        palette.addView(chip("＋ " + T("Flotante", "Floating"), this::addBtn));
        HorizontalScrollView pal = new HorizontalScrollView(this);
        pal.setHorizontalScrollBarEnabled(false);
        pal.addView(palette);
        root.addView(pal);

        Insets.apply(root);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33)
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        engine.showFree(vm.free);
        rebuildBtns();
        buildProps();
    }

    @Override protected void onResume() {
        super.onResume();
        if (engine != null) engine.onResume();
        if (engine != null) engine.showFree(vm.free);
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
        if (selKind >= 0) { clearSel(); return; }
        flush();
        finish();
    }

    // ================================================================== utilidades

    private String T(String a, String b) { return es ? a : b; }
    private int dp(float v) { return (int) (v * d + 0.5f); }
    private int W() { return getResources().getDisplayMetrics().widthPixels; }
    private int H() { return getResources().getDisplayMetrics().heightPixels; }
    private int sysDim(String n) {
        int id = getResources().getIdentifier(n, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private int sq(int wPermille) { return Math.max(20, wPermille * W() / H()); }
    private AlertDialog.Builder dlg() { return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert); }

    private void fit(int w, int h) {
        if (w <= 0 || h <= 0) return;
        sc = Math.min(w / (float) W(), h / (float) H());
        host.setScaleX(sc);
        host.setScaleY(sc);
        host.setTranslationX((w - W() * sc) / 2f);
        host.setTranslationY((h - H() * sc) / 2f);
        engine.setPreviewScale(sc);
        overlay.invalidate();
    }

    private TextView headBtn(String s, Runnable r) {
        TextView t = ZUi.text(this, s, 18, TXT, false);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private TextView chip(String s, Runnable r) {
        TextView t = ZUi.text(this, s, 12, TXT, true);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setBackground(ZUi.shape(this, HI, 14));
        t.setOnClickListener(v -> r.run());
        t.setLayoutParams(ZKit.lp(this, -2, -2, 0, 3, 0, 3, 0));
        return t;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void flush() {
        ui.removeCallbacks(saveRun);
        if (dirty && vm != null) { dirty = false; vm.save(this); }
    }

    private void saveSoon() {
        dirty = true;
        ui.removeCallbacks(saveRun);
        ui.postDelayed(saveRun, 350);
    }

    private void applySoon() {
        ui.removeCallbacks(applyRun);
        ui.postDelayed(applyRun, 70);
    }

    private void setMode(int m) {
        mode = m;
        engine.previewRadial(false);
        engine.previewDrawer(false);
        if (m == 1) engine.previewDrawer(true);
        else if (m == 2) ui.postDelayed(() -> engine.previewRadial(true), 120);
        clearSel();
    }

    // ================================================================== deshacer

    private String snapshot() {
        try {
            JSONObject o = new JSONObject();
            JSONArray fa = new JSONArray();
            for (VisualModel.Free f : vm.free) fa.put(VisualModel.freeTo(f));
            o.put("f", fa);
            JSONArray ba = new JSONArray();
            for (VisualModel.Btn b : vm.buttons) ba.put(VisualModel.btnTo(b));
            o.put("b", ba);
            JSONObject so = new JSONObject();
            for (String k : KEYS) so.put(k, st.get(k));
            o.put("s", so);
            return o.toString();
        } catch (Throwable t) { return null; }
    }

    private void restore(String s) {
        try {
            JSONObject o = new JSONObject(s);
            vm.free.clear();
            JSONArray fa = o.getJSONArray("f");
            for (int i = 0; i < fa.length(); i++) vm.free.add(VisualModel.freeFrom(fa.getJSONObject(i)));
            vm.buttons.clear();
            JSONArray ba = o.getJSONArray("b");
            for (int i = 0; i < ba.length(); i++) vm.buttons.add(VisualModel.btnFrom(ba.getJSONObject(i)));
            JSONObject so = o.getJSONObject("s");
            for (String k : KEYS) if (so.has(k)) st.set(k, so.getInt(k));
        } catch (Throwable ignored) { }
        selKind = -1;
        selRef = null;
        engine.applyAll();
        engine.showFree(vm.free);
        rebuildBtns();
        saveSoon();
        buildProps();
        overlay.invalidate();
    }

    private void pushUndo() {
        String s = snapshot();
        if (s == null) return;
        if (undo.isEmpty() || !undo.peek().equals(s)) undo.push(s);
        while (undo.size() > 40) undo.removeLast();
        redo.clear();
    }

    private void doUndo() {
        if (undo.isEmpty()) { toast(T("Nada que deshacer", "Nothing to undo")); return; }
        String cur = snapshot();
        if (cur != null) redo.push(cur);
        restore(undo.pop());
    }

    private void doRedo() {
        if (redo.isEmpty()) { toast(T("Nada que rehacer", "Nothing to redo")); return; }
        String cur = snapshot();
        if (cur != null) undo.push(cur);
        restore(redo.pop());
    }

    // ================================================================== objetos del escenario

    private int[] btnRect(VisualModel.Btn b) {
        int W = W(), H = H(), sz = dp(b.size), mx = dp(8) + dp(Math.max(0, b.x)), my = dp(8) + dp(Math.max(0, b.y));
        int l = b.hz.equals("left") ? mx : b.hz.equals("right") ? W - mx - sz : (W - sz) / 2 + mx;
        int t = b.v.equals("up") ? pvT + my : b.v.equals("down") ? H - pvB - my - sz : (H - sz) / 2 + my;
        return new int[]{l, t, sz, sz};
    }

    /** Convierte una posición arrastrada en «ancla + desplazamiento», que es como el motor de scripts coloca los botones flotantes. */
    private void setBtnPos(VisualModel.Btn b, int left, int top) {
        int W = W(), H = H(), sz = dp(b.size);
        boolean leftSide = left + sz / 2 < W / 2, upSide = top + sz / 2 < H / 2;
        b.hz = leftSide ? "left" : "right";
        b.v = upSide ? "up" : "down";
        int mx = leftSide ? left : W - left - sz;
        int my = upSide ? top - pvT : H - pvB - top - sz;
        b.x = Math.max(0, Math.round((mx - dp(8)) / d));
        b.y = Math.max(0, Math.round((my - dp(8)) / d));
    }

    private String freeName(VisualModel.Free f) {
        switch (f.type) {
            case "app": return "App: " + appName(f.a);
            case "text": return T("Texto: ", "Text: ") + f.a;
            case "clock": return T("Reloj", "Clock");
            case "image": return T("Imagen", "Image");
            case "widget": return "Widget";
            case "category": return T("Atajo: ", "Shortcut: ") + ("*".equals(f.a) ? T("todas", "all") : catName(f.a));
            case "action": return T("Botón ", "Button ") + f.icon;
            default: return T("Forma", "Shape");
        }
    }

    private String appName(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Throwable t) { return pkg; }
    }

    private String catName(String id) {
        CategoryManager.Category c = cm.byId(id);
        return c == null ? id : c.name;
    }

    private List<Obj> objects() {
        List<Obj> l = new ArrayList<>();
        if (mode == 0) l.add(new Obj(K_DESK, null, engine.deskRect(), T("Escritorio (zona de iconos)", "Desktop (icon area)")));
        if (mode == 1) l.add(new Obj(K_DRAWER, null, engine.drawerRect(), T("Cajón de apps", "App drawer")));
        l.add(new Obj(K_DOCK, null, engine.dockRect(), "Dock"));
        l.add(new Obj(K_HOME, null, engine.homeRect(), T("Botón central", "Center button")));
        if (mode == 0) {
            for (VisualModel.Free f : vm.free) l.add(new Obj(K_FREE, f, FreeLayer.rect(this, f), freeName(f)));
            for (VisualModel.Btn b : vm.buttons) l.add(new Obj(K_BTN, b, btnRect(b), T("Botón flotante: ", "Floating button: ") + b.name));
        }
        if (mode == 2) {
            int[] p = engine.radialPoint();
            int h = dp(16);
            l.add(new Obj(K_RADIAL, null, new int[]{p[0] - h, p[1] - h, h * 2, h * 2}, T("Origen del radial", "Radial origin")));
        }
        return l;
    }

    private Obj findSel(List<Obj> os) {
        if (selKind < 0) return null;
        for (Obj o : os) if (o.kind == selKind && o.ref == selRef) return o;
        return null;
    }

    private boolean hasHandle(Obj o) { return o.kind != K_RADIAL; }

    /** Esquina de la manija de tamaño: abajo a la derecha; en dock y cajón (anclados abajo) arriba, porque crecen hacia arriba. */
    private int[] handleXY(Obj o) {
        if (o.kind == K_DOCK && st.get(LauncherSettings.DOCK_POS) == 2) return new int[]{o.r[0], o.r[1]};
        if (o.kind == K_DOCK || o.kind == K_DRAWER) return new int[]{o.r[0] + o.r[2], o.r[1]};
        return new int[]{o.r[0] + o.r[2], o.r[1] + o.r[3]};
    }

    private int hs() { return (int) (dp(26) / sc); }

    private boolean inHandle(Obj o, float x, float y) {
        int[] h = handleXY(o);
        return Math.abs(x - h[0]) < hs() && Math.abs(y - h[1]) < hs();
    }

    private List<Obj> hits(List<Obj> os, float x, float y) {
        List<Obj> out = new ArrayList<>();
        int slop = (int) (dp(6) / sc);
        for (int i = os.size() - 1; i >= 0; i--) {
            int[] r = os.get(i).r;
            if (x >= r[0] - slop && x <= r[0] + r[2] + slop && y >= r[1] - slop && y <= r[1] + r[3] + slop) out.add(os.get(i));
        }
        return out;
    }

    private void select(Obj o) {
        boolean same = o.kind == selKind && o.ref == selRef;
        selKind = o.kind;
        selRef = o.ref;
        if (!same) buildProps();
        overlay.invalidate();
    }

    private void clearSel() {
        selKind = -1;
        selRef = null;
        buildProps();
        overlay.invalidate();
    }

    // ================================================================== capa de dibujo y toques

    private final class Overlay extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Obj target;
        private boolean resizing, moved, wasSel;
        private float sx, sy;
        private int[] r0, dock0, base;

        Overlay() {
            super(LauncherCanvasActivity.this);
            line.setStyle(Paint.Style.STROKE);
            txt.setColor(Color.WHITE);
        }

        @Override protected void onDraw(Canvas c) {
            float k = 1f / sc;
            List<Obj> os = objects();
            Obj cur = findSel(os);
            for (Obj o : os) {
                if (o == cur) continue;
                line.setColor(0x66FFFFFF);
                line.setStrokeWidth(dp(1) * k);
                line.setPathEffect(new DashPathEffect(new float[]{dp(5) * k, dp(4) * k}, 0));
                c.drawRoundRect(o.r[0], o.r[1], o.r[0] + o.r[2], o.r[1] + o.r[3], dp(6), dp(6), line);
            }
            if (cur != null) {
                line.setPathEffect(null);
                line.setColor(ACC);
                line.setStrokeWidth(dp(2) * k);
                c.drawRoundRect(cur.r[0], cur.r[1], cur.r[0] + cur.r[2], cur.r[1] + cur.r[3], dp(6), dp(6), line);
                if (hasHandle(cur)) {
                    int[] h = handleXY(cur);
                    fill.setColor(ACC);
                    c.drawCircle(h[0], h[1], dp(9) * k, fill);
                    fill.setColor(Color.WHITE);
                    c.drawCircle(h[0], h[1], dp(3.5f) * k, fill);
                }
                txt.setTextSize(dp(11) * k);
                fill.setColor(0xCC000000);
                float tw = txt.measureText(cur.name) + dp(10) * k;
                float ty = Math.max(cur.r[1] - dp(6) * k, dp(16) * k);
                c.drawRoundRect(cur.r[0], ty - dp(14) * k, cur.r[0] + tw, ty + dp(4) * k, dp(4), dp(4), fill);
                c.drawText(cur.name, cur.r[0] + dp(5) * k, ty, txt);
            }
            if (guideX >= 0 || guideY >= 0) {
                line.setColor(0xFFFF4DD2);
                line.setStrokeWidth(dp(1) * k);
                line.setPathEffect(null);
                if (guideX >= 0) c.drawLine(guideX, 0, guideX, H(), line);
                if (guideY >= 0) c.drawLine(0, guideY, W(), guideY, line);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            float x = ev.getX(), y = ev.getY();
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    moved = false;
                    resizing = false;
                    List<Obj> os = objects();
                    Obj cur = findSel(os);
                    if (cur != null && hasHandle(cur) && inHandle(cur, x, y)) { target = cur; resizing = true; }
                    else { List<Obj> h = hits(os, x, y); target = h.isEmpty() ? null : h.get(0); }
                    if (target == null) { if (selKind >= 0) clearSel(); return true; }
                    wasSel = cur != null && cur.kind == target.kind && cur.ref == target.ref;
                    select(target);
                    sx = x;
                    sy = y;
                    r0 = target.r.clone();
                    dock0 = engine.dockRect();
                    base = new int[KEYS.length];
                    for (int i = 0; i < KEYS.length; i++) base[i] = st.get(KEYS[i]);
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (target == null) return true;
                    float dx = x - sx, dy = y - sy;
                    if (!moved && Math.hypot(dx, dy) < dp(4) / sc) return true;
                    if (!moved) { moved = true; pushUndo(); }
                    drag(target, dx, dy, resizing, r0, dock0, base);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (target != null) {
                        if (moved) endDrag(target);
                        else if (ev.getActionMasked() == MotionEvent.ACTION_UP && wasSel && !resizing) {
                            // segundo toque sobre lo ya elegido: pasa al elemento que está debajo
                            List<Obj> h = hits(objects(), x, y);
                            for (int i = 0; i < h.size(); i++) {
                                if (h.get(i).kind == target.kind && h.get(i).ref == target.ref) { select(h.get((i + 1) % h.size())); break; }
                            }
                        }
                    }
                    target = null;
                    guideX = guideY = -1;
                    invalidate();
                    return true;
                }
            }
            return true;
        }
    }

    private int baseOf(int[] base, String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return base[i];
        return 0;
    }

    /** Imanta el rectángulo (izq, arriba, ancho, alto) a los bordes y centros de la pantalla y de los demás elementos libres. */
    private void snapRect(int[] r, boolean move, Object self) {
        guideX = guideY = -1;
        if (!snap) return;
        int thr = Math.max(dp(4), (int) (dp(7) / sc));
        List<Integer> xs = new ArrayList<>(), ys = new ArrayList<>();
        Collections.addAll(xs, 0, W() / 2, W());
        Collections.addAll(ys, 0, H() / 2, H());
        for (VisualModel.Free o : vm.free) {
            if (o == self) continue;
            int[] q = FreeLayer.rect(this, o);
            Collections.addAll(xs, q[0], q[0] + q[2] / 2, q[0] + q[2]);
            Collections.addAll(ys, q[1], q[1] + q[3] / 2, q[1] + q[3]);
        }
        int[] ex = move ? new int[]{r[0], r[0] + r[2] / 2, r[0] + r[2]} : new int[]{r[0] + r[2]};
        int[] ey = move ? new int[]{r[1], r[1] + r[3] / 2, r[1] + r[3]} : new int[]{r[1] + r[3]};
        int bd = thr + 1, bg = -1, bs = 0;
        for (int e : ex) for (int c : xs) { int df = Math.abs(c - e); if (df < bd) { bd = df; bg = c; bs = c - e; } }
        if (bg >= 0) { if (move) r[0] += bs; else r[2] += bs; guideX = bg; }
        bd = thr + 1; bg = -1; bs = 0;
        for (int e : ey) for (int c : ys) { int df = Math.abs(c - e); if (df < bd) { bd = df; bg = c; bs = c - e; } }
        if (bg >= 0) { if (move) r[1] += bs; else r[3] += bs; guideY = bg; }
    }

    private void drag(Obj t, float dx, float dy, boolean resize, int[] r0, int[] dock0, int[] base) {
        int W = W(), H = H(), avail = W - dp(24);
        switch (t.kind) {
            case K_FREE: {
                VisualModel.Free f = (VisualModel.Free) t.ref;
                int[] r = r0.clone();
                if (resize) {
                    r[2] = Math.max(dp(24), r0[2] + (int) dx);
                    r[3] = Math.max(dp(24), r0[3] + (int) dy);
                    snapRect(r, false, f);
                    r[2] = Math.max(dp(24), Math.min(W - r[0], r[2]));
                    r[3] = Math.max(dp(24), Math.min(H - r[1], r[3]));
                } else {
                    r[0] = r0[0] + (int) dx;
                    r[1] = r0[1] + (int) dy;
                    snapRect(r, true, f);
                    r[0] = clamp(r[0], 0, Math.max(0, W - r[2]));
                    r[1] = clamp(r[1], 0, Math.max(0, H - r[3]));
                }
                f.x = r[0] * 1000 / W;
                f.y = r[1] * 1000 / H;
                f.w = Math.max(10, r[2] * 1000 / W);
                f.h = Math.max(10, r[3] * 1000 / H);
                engine.placeFree(vm.free.indexOf(f), f);
                saveSoon();
                break;
            }
            case K_BTN: {
                VisualModel.Btn b = (VisualModel.Btn) t.ref;
                if (resize) b.size = clamp(Math.round((r0[2] + Math.max(dx, dy)) / d), 24, 160);
                else setBtnPos(b, r0[0] + (int) dx, r0[1] + (int) dy);
                placeBtn(b);
                saveSoon();
                break;
            }
            case K_HOME:
                if (resize) {
                    st.set(LauncherSettings.HOME_SIZE, clamp(Math.round((r0[2] + Math.max(dx, dy)) / d), 36, 90));
                    engine.relayout();
                    applySoon();
                    break;
                }
                // arrastrar el botón central mueve todo el dock: sigue
            case K_DOCK: {
                int pos0 = baseOf(base, LauncherSettings.DOCK_POS);
                if (resize && t.kind == K_DOCK) {
                    int nw = dock0[2] + (int) (pos0 == 2 ? -dx : pos0 == 0 ? dx * 2 : dx);
                    st.set(LauncherSettings.DOCK_W, clamp(nw * 100 / Math.max(1, avail), 40, 100));
                    st.set(LauncherSettings.DOCK_H, clamp(Math.round((dock0[3] - dy) / d), 48, 140));
                } else {
                    int w = dock0[2], left = dock0[0] + (int) dx, cx = left + w / 2;
                    int pos = cx < W / 3 ? 1 : cx > W * 2 / 3 ? 2 : 0;
                    int bs = pos == 1 ? dp(8) : pos == 2 ? W - dp(8) - w : (W - w) / 2;
                    int off = Math.round((left - bs) / d);
                    guideX = -1;
                    if (snap && Math.abs(cx - W / 2) < Math.max(dp(5), (int) (dp(8) / sc))) { pos = 0; off = 0; guideX = W / 2; }
                    st.set(LauncherSettings.DOCK_POS, pos);
                    st.set(LauncherSettings.DOCK_X, clamp(off, -300, 300));
                    st.set(LauncherSettings.DOCK_Y, clamp(baseOf(base, LauncherSettings.DOCK_Y) - Math.round(dy / d), 0, 400));
                }
                engine.relayout();
                applySoon();
                break;
            }
            case K_DRAWER: {
                if (resize) {
                    st.set(LauncherSettings.DRAWER_W, clamp((r0[2] + (int) (dx * 2)) * 100 / Math.max(1, avail), 20, 100));
                    st.set(LauncherSettings.DRAWER_H, clamp((r0[3] - (int) dy) * 100 / H, 30, 90));
                } else {
                    st.set(LauncherSettings.DRAWER_X, clamp(baseOf(base, LauncherSettings.DRAWER_X) + Math.round(dx / d), -300, 300));
                    st.set(LauncherSettings.DRAWER_Y, clamp(baseOf(base, LauncherSettings.DRAWER_Y) - Math.round(dy / d), -200, 500));
                }
                engine.relayout();
                break;
            }
            case K_DESK: {
                int ddx = Math.round(dx / d), ddy = Math.round(dy / d);
                if (!resize) {
                    st.set(LauncherSettings.DESK_L, clamp(baseOf(base, LauncherSettings.DESK_L) + ddx, -8, 800));
                    st.set(LauncherSettings.DESK_T, clamp(baseOf(base, LauncherSettings.DESK_T) + ddy, -8, 800));
                }
                st.set(LauncherSettings.DESK_R, clamp(baseOf(base, LauncherSettings.DESK_R) - ddx, -8, 800));
                st.set(LauncherSettings.DESK_B, clamp(baseOf(base, LauncherSettings.DESK_B) - ddy, -8, 1200));
                engine.relayout();
                break;
            }
            case K_RADIAL: {
                int[] hm = engine.homeRect();
                float px = r0[0] + r0[2] / 2f + dx, py = r0[1] + r0[3] / 2f + dy;
                st.set(LauncherSettings.RAD_X, clamp(Math.round((px - (hm[0] + hm[2] / 2f)) / d), -200, 200));
                st.set(LauncherSettings.RAD_Y, clamp(Math.round(((hm[1] + hm[3] / 2f) - dp(7) - py) / d), -150, 300));
                applySoon();
                break;
            }
        }
        info.setText(t.name + "  " + describeNow(t));
    }

    private String describeNow(Obj t) {
        switch (t.kind) {
            case K_FREE: { VisualModel.Free f = (VisualModel.Free) t.ref; return (f.x / 10f) + "%, " + (f.y / 10f) + "%  ·  " + (f.w / 10f) + "×" + (f.h / 10f) + "%"; }
            case K_BTN: { VisualModel.Btn b = (VisualModel.Btn) t.ref; return b.v + "/" + b.hz + "  x" + b.x + " y" + b.y + "  " + b.size + " dp"; }
            case K_DOCK: return st.get(LauncherSettings.DOCK_W) + "% × " + st.get(LauncherSettings.DOCK_H) + " dp  ·  x" + st.get(LauncherSettings.DOCK_X) + " y" + st.get(LauncherSettings.DOCK_Y);
            case K_HOME: return st.get(LauncherSettings.HOME_SIZE) + " dp";
            case K_DRAWER: return st.get(LauncherSettings.DRAWER_W) + "% × " + st.get(LauncherSettings.DRAWER_H) + "%  ·  x" + st.get(LauncherSettings.DRAWER_X) + " y" + st.get(LauncherSettings.DRAWER_Y);
            case K_DESK: return "L" + st.get(LauncherSettings.DESK_L) + " T" + st.get(LauncherSettings.DESK_T) + " R" + st.get(LauncherSettings.DESK_R) + " B" + st.get(LauncherSettings.DESK_B);
            default: return "x" + st.get(LauncherSettings.RAD_X) + " y" + st.get(LauncherSettings.RAD_Y);
        }
    }

    private void endDrag(Obj t) {
        if (t.kind == K_FREE) { engine.showFree(vm.free); saveSoon(); }
        else if (t.kind == K_BTN) saveSoon();
        else { ui.removeCallbacks(applyRun); applyRun.run(); }
        buildProps();
    }

    // ================================================================== botones flotantes (vista de mentira, el script real solo corre en el launcher)

    private void rebuildBtns() {
        btnLayer.removeAllViews();
        for (VisualModel.Btn b : vm.buttons) {
            TextView t = new TextView(this);
            t.setText(b.label.isEmpty() ? b.icon : b.icon + "\n" + b.label);
            t.setTextColor(b.tcolor);
            t.setTextSize(b.label.isEmpty() ? b.size * 0.42f : b.size * 0.3f);
            t.setGravity(Gravity.CENTER);
            t.setAlpha(b.opacity / 100f * (b.visible ? 1f : 0.35f));
            GradientDrawable g = new GradientDrawable();
            g.setColor(b.bg);
            g.setCornerRadius(dp(b.shape.equals("circle") ? b.size / 2f : b.shape.equals("round") ? b.size / 4f : 0));
            t.setBackground(g);
            btnLayer.addView(t, new FrameLayout.LayoutParams(1, 1));
            placeBtn(b);
        }
    }

    private void placeBtn(VisualModel.Btn b) {
        View v = btnLayer.getChildAt(vm.buttons.indexOf(b));
        if (v == null) return;
        int[] r = btnRect(b);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(r[2], r[3], Gravity.TOP | Gravity.START);
        lp.leftMargin = r[0];
        lp.topMargin = r[1];
        v.setLayoutParams(lp);
    }

    // ================================================================== agregar elementos

    private VisualModel.Free newFree(String type, String a) {
        VisualModel.Free f = new VisualModel.Free();
        f.type = type;
        f.a = a == null ? "" : a;
        int n = vm.free.size() % 6;
        f.x = 120 + n * 40;
        f.y = 300 + n * 30;
        switch (type) {
            case "app": f.w = 170; f.h = sq(170); f.tsize = 16; f.radius = 0; break;
            case "text": f.w = 480; f.h = 70; f.tsize = 20; f.radius = 0; break;
            case "clock": f.w = 560; f.h = 100; f.tsize = 48; f.bold = true; f.radius = 0; break;
            case "image": f.w = 480; f.h = 220; f.radius = 20; break;
            case "widget": f.w = 700; f.h = 160; f.radius = 20; break;
            case "category": f.w = 190; f.h = sq(190); f.bg = 0x33FFFFFF; f.radius = 22; f.tsize = 16; break;
            case "action": f.w = 190; f.h = sq(190); f.bg = 0x663D7BFF; f.radius = 60; f.tsize = 18; break;
            default: f.w = 500; f.h = 120; f.bg = 0x55FFFFFF; f.radius = 24; break;
        }
        return f;
    }

    private void addFree(String type, String a) { insertFree(newFree(type, a)); }

    private void insertFree(VisualModel.Free f) {
        pushUndo();
        vm.free.add(f);
        selKind = K_FREE;
        selRef = f;
        if (mode != 0) { mode = 0; engine.previewRadial(false); engine.previewDrawer(false); }
        engine.showFree(vm.free);
        saveSoon();
        buildProps();
        overlay.invalidate();
    }

    private void addBtn() {
        pushUndo();
        VisualModel.Btn b = new VisualModel.Btn();
        int n = vm.buttons.size() + 1;
        while (btnNameTaken(T("Botón ", "Button ") + n)) n++;
        b.name = T("Botón ", "Button ") + n;
        b.icon = "●";
        b.v = "down";
        b.hz = "right";
        b.x = 16;
        b.y = 120;
        vm.buttons.add(b);
        selKind = K_BTN;
        selRef = b;
        if (mode != 0) { mode = 0; engine.previewRadial(false); engine.previewDrawer(false); }
        rebuildBtns();
        saveSoon();
        buildProps();
        overlay.invalidate();
        toast(T("Botón flotante creado. Con ⚙ le asignás qué hace al tocarlo (regla «cuando botón pulsado»).", "Floating button created. Use ⚙ to set what it does (rule “when button pressed”)."));
    }

    private boolean btnNameTaken(String n) {
        for (VisualModel.Btn b : vm.buttons) if (b.name.equals(n)) return true;
        return false;
    }

    private void pickImage(boolean replace) {
        pendingReplace = replace;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_IMG);
    }

    private void pickWidget() {
        final AppWidgetHost wh = engine.widgetHost();
        if (wh == null) { toast(T("Widgets no disponibles", "Widgets unavailable")); return; }
        final AppWidgetManager awm = AppWidgetManager.getInstance(this);
        final List<AppWidgetProviderInfo> list = awm.getInstalledProviders();
        List<CharSequence> names = new ArrayList<>();
        for (AppWidgetProviderInfo p : list) names.add(p.loadLabel(getPackageManager()));
        dlg().setTitle(T("Elegí un widget", "Pick a widget")).setItems(names.toArray(new CharSequence[0]), (dd, which) -> {
            AppWidgetProviderInfo p = list.get(which);
            int id = wh.allocateAppWidgetId();
            pendingWidget = id;
            pendingInfo = p;
            if (awm.bindAppWidgetIdIfAllowed(id, p.provider)) configureOrAdd(id);
            else {
                Intent bi = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                bi.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                bi.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider);
                startActivityForResult(bi, REQ_BIND);
            }
        }).show();
    }

    private void configureOrAdd(int id) {
        AppWidgetProviderInfo wi = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (wi != null && wi.configure != null) {
            try { engine.widgetHost().startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CFG, null); return; } catch (Throwable ignored) { }
        }
        finishWidget(id);
    }

    private void finishWidget(int id) {
        VisualModel.Free f = newFree("widget", String.valueOf(id));
        if (pendingInfo != null) {
            f.w = clamp(pendingInfo.minWidth * 1000 / W(), 150, 1000);
            f.h = clamp(pendingInfo.minHeight * 1000 / H(), 40, 800);
        }
        insertFree(f);
        pendingWidget = -1;
        pendingInfo = null;
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        AppWidgetHost wh = engine == null ? null : engine.widgetHost();
        if (req == REQ_IMG) {
            Uri u = res == RESULT_OK && data != null ? data.getData() : null;
            if (u == null) { pendingReplace = false; return; }
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Throwable ignored) { }
            if (pendingReplace && selRef instanceof VisualModel.Free) edit(() -> ((VisualModel.Free) selRef).a = u.toString());
            else addFree("image", u.toString());
            pendingReplace = false;
        } else if (req == REQ_BIND) {
            if (res == RESULT_OK && pendingWidget >= 0) configureOrAdd(pendingWidget);
            else if (pendingWidget >= 0) { if (wh != null) wh.deleteAppWidgetId(pendingWidget); pendingWidget = -1; }
        } else if (req == REQ_CFG) {
            if (res == RESULT_OK && pendingWidget >= 0) finishWidget(pendingWidget);
            else if (pendingWidget >= 0) { if (wh != null) wh.deleteAppWidgetId(pendingWidget); pendingWidget = -1; }
        }
    }

    // ================================================================== propiedades del elemento elegido

    /** Cambio hecho sobre un elemento libre: deshacer, redibujar, guardar y refrescar botones de propiedades. */
    private void edit(Runnable change) {
        pushUndo();
        change.run();
        engine.showFree(vm.free);
        rebuildBtns();
        saveSoon();
        buildProps();
        overlay.invalidate();
    }

    /** Cambio de un ajuste del launcher. */
    private void setPref(String key, int v) {
        pushUndo();
        st.set(key, v);
        engine.applyAll();
        if (mode == 2) { engine.previewRadial(false); ui.postDelayed(() -> engine.previewRadial(true), 120); }
        buildProps();
        overlay.invalidate();
    }

    private void resetPrefs(String... keys) {
        pushUndo();
        for (String k : keys) st.reset(k);
        engine.applyAll();
        if (mode == 2) { engine.previewRadial(false); ui.postDelayed(() -> engine.previewRadial(true), 120); }
        buildProps();
        overlay.invalidate();
    }

    private void numPref(String label, String key, int lo, int hi, String unit) {
        props.addView(chip(label + " " + st.get(key) + unit, () -> askInt(label, st.get(key), lo, hi, v -> setPref(key, v))));
    }

    private void colorPref(String label, String key) {
        props.addView(chip("🎨 " + label, () -> askColor(label, st.get(key), true, c -> setPref(key, c))));
    }

    private void buildProps() {
        props.removeAllViews();
        Obj o = findSel(objects());
        if (o == null) {
            info.setText(T("Tocá algo para elegirlo · arrastrá para mover · la manija ● cambia el tamaño · tocá de nuevo para elegir lo que está debajo",
                "Tap something to select · drag to move · the ● handle resizes · tap again to pick what is underneath"));
            return;
        }
        info.setText(o.name + "  " + describeNow(o));
        switch (o.kind) {
            case K_FREE: freeProps((VisualModel.Free) o.ref); break;
            case K_BTN: btnProps((VisualModel.Btn) o.ref); break;
            case K_DOCK:
                props.addView(chip(T("Posición: ", "Position: ") + new String[]{T("centro", "center"), T("izquierda", "left"), T("derecha", "right")}[st.get(LauncherSettings.DOCK_POS) % 3],
                    () -> { pushUndo(); st.set(LauncherSettings.DOCK_POS, (st.get(LauncherSettings.DOCK_POS) + 1) % 3); st.set(LauncherSettings.DOCK_X, 0); engine.applyAll(); buildProps(); overlay.invalidate(); }));
                props.addView(chip(st.get(LauncherSettings.DOCK_SHOW) == 1 ? T("Fondo: sí", "Backdrop: on") : T("Fondo: no", "Backdrop: off"),
                    () -> setPref(LauncherSettings.DOCK_SHOW, 1 - st.get(LauncherSettings.DOCK_SHOW))));
                colorPref(T("Color", "Color"), LauncherSettings.DOCK_COLOR);
                numPref(T("Redondeo", "Radius"), LauncherSettings.DOCK_RADIUS, 0, 50, " dp");
                numPref(T("Icono", "Icon"), LauncherSettings.DOCK_ICON, 32, 72, " dp");
                props.addView(chip("↺", () -> resetPrefs(LauncherSettings.DOCK_POS, LauncherSettings.DOCK_X, LauncherSettings.DOCK_Y, LauncherSettings.DOCK_W, LauncherSettings.DOCK_H)));
                break;
            case K_HOME:
                numPref(T("Tamaño", "Size"), LauncherSettings.HOME_SIZE, 36, 90, " dp");
                props.addView(chip(T("Arrastralo: mueve todo el dock", "Drag it: moves the whole dock"), () -> { }));
                break;
            case K_DRAWER:
                numPref(T("Columnas", "Columns"), LauncherSettings.DRAWER_COLS, 2, 8, "");
                colorPref(T("Color", "Color"), LauncherSettings.DRAWER_COLOR);
                numPref(T("Redondeo", "Radius"), LauncherSettings.DRAWER_RADIUS, 0, 50, " dp");
                props.addView(chip(T("Entra desde: ", "Enters from: ") + new String[]{T("centro", "center"), "←", "→", "↑", T("fundido", "fade")}[st.get(LauncherSettings.DRAWER_FROM) % 5],
                    () -> setPref(LauncherSettings.DRAWER_FROM, (st.get(LauncherSettings.DRAWER_FROM) + 1) % 5)));
                props.addView(chip("↺", () -> resetPrefs(LauncherSettings.DRAWER_W, LauncherSettings.DRAWER_X, LauncherSettings.DRAWER_Y, LauncherSettings.DRAWER_H)));
                break;
            case K_DESK:
                numPref(T("Columnas", "Columns"), LauncherSettings.DESK_COLS, 1, 8, "");
                numPref(T("Separación", "Spacing"), LauncherSettings.DESK_GAP, 0, 40, " dp");
                numPref(T("Icono", "Icon"), LauncherSettings.APP_ICON, 32, 96, " dp");
                props.addView(chip(st.get(LauncherSettings.LABELS) == 1 ? T("Etiquetas: sí", "Labels: on") : T("Etiquetas: no", "Labels: off"),
                    () -> setPref(LauncherSettings.LABELS, 1 - st.get(LauncherSettings.LABELS))));
                props.addView(chip("↺", () -> resetPrefs(LauncherSettings.DESK_L, LauncherSettings.DESK_T, LauncherSettings.DESK_R, LauncherSettings.DESK_B)));
                break;
            case K_RADIAL:
                numPref(T("Burbuja", "Bubble"), LauncherSettings.BUBBLE, 24, 96, " dp");
                numPref(T("Apertura", "Arc"), LauncherSettings.ARC, 60, 180, "°");
                numPref(T("Radial 1", "Ring 1"), LauncherSettings.RING1, 3, 8, "");
                numPref(T("Radial 2", "Ring 2"), LauncherSettings.RING2, 3, 10, "");
                props.addView(chip("↺", () -> resetPrefs(LauncherSettings.RAD_X, LauncherSettings.RAD_Y)));
                break;
        }
    }

    private void freeProps(final VisualModel.Free f) {
        props.addView(chip("🗑", () -> confirm(T("¿Borrar este elemento?", "Delete this element?"), () -> edit(() -> {
            if (f.type.equals("widget")) {
                try { engine.widgetHost().deleteAppWidgetId(Integer.parseInt(f.a)); } catch (Throwable ignored) { }
            }
            vm.free.remove(f);
            selKind = -1;
            selRef = null;
        }))));
        props.addView(chip("⧉", () -> {
            if (f.type.equals("widget")) { toast(T("Un widget no se puede duplicar: agregá otro", "A widget can't be duplicated: add another")); return; }
            edit(() -> {
                VisualModel.Free c = f.copy();
                c.x = Math.min(900, f.x + 30);
                c.y = Math.min(900, f.y + 20);
                vm.free.add(c);
                selRef = c;
            });
        }));
        props.addView(chip("⚡ " + T("Acciones", "Actions"), () -> gestureDialog(f)));
        props.addView(chip(f.visible ? "👁" : "🚫", () -> edit(() -> f.visible = !f.visible)));
        props.addView(chip("⬆", () -> edit(() -> { vm.free.remove(f); vm.free.add(f); })));
        props.addView(chip("⬇", () -> edit(() -> { vm.free.remove(f); vm.free.add(0, f); })));
        if (!f.type.equals("widget") && !f.type.equals("image"))
            props.addView(chip("🎨 " + T("Fondo", "Fill"), () -> askColor(T("Fondo", "Fill"), f.bg, true, c -> edit(() -> f.bg = c))));
        props.addView(chip(T("Redondeo ", "Radius ") + f.radius, () -> askInt(T("Redondeo (dp)", "Radius (dp)"), f.radius, 0, 200, v -> edit(() -> f.radius = v))));
        props.addView(chip(T("Opacidad ", "Opacity ") + f.opacity + "%", () -> askInt(T("Opacidad (%)", "Opacity (%)"), f.opacity, 5, 100, v -> edit(() -> f.opacity = v))));
        switch (f.type) {
            case "text":
                props.addView(chip("✎ " + T("Texto", "Text"), () -> askText(T("Texto", "Text"), f.a, t -> edit(() -> f.a = t))));
                textStyle(f);
                break;
            case "clock":
                props.addView(chip("🕒 " + new String[]{"HH:mm", "HH:mm:ss", "hh:mm a", T("fecha corta", "short date"), T("fecha larga", "long date")}[f.fmt % 5],
                    () -> edit(() -> f.fmt = (f.fmt + 1) % 5)));
                textStyle(f);
                break;
            case "app":
                props.addView(chip("🔁 App", () -> pickApp(pkg -> edit(() -> f.a = pkg))));
                props.addView(chip(f.label ? T("Etiqueta: sí", "Label: on") : T("Etiqueta: no", "Label: off"), () -> edit(() -> f.label = !f.label)));
                textStyle(f);
                break;
            case "category":
                props.addView(chip("🔁 " + T("Categoría", "Category"), () -> pickCategory(id -> edit(() -> f.a = id))));
                props.addView(chip("✎ " + T("Símbolo", "Symbol"), () -> askText(T("Símbolo", "Symbol"), f.icon, g -> edit(() -> f.icon = g))));
                props.addView(chip(f.label ? T("Etiqueta: sí", "Label: on") : T("Etiqueta: no", "Label: off"), () -> edit(() -> f.label = !f.label)));
                textStyle(f);
                break;
            case "action":
                props.addView(chip("✎ " + T("Símbolo", "Symbol"), () -> askText(T("Símbolo", "Symbol"), f.icon, g -> edit(() -> f.icon = g))));
                props.addView(chip("✎ " + T("Nombre", "Name"), () -> askText(T("Nombre bajo el símbolo (vacío = ninguno)", "Name under the symbol (empty = none)"), f.a, t -> edit(() -> f.a = t))));
                textStyle(f);
                break;
            case "image":
                props.addView(chip("🖼 " + T("Cambiar", "Change"), () -> pickImage(true)));
                break;
            default:
                break;
        }
    }

    private void textStyle(final VisualModel.Free f) {
        props.addView(chip("🎨 " + T("Texto", "Text"), () -> askColor(T("Color del texto", "Text color"), f.tcolor, true, c -> edit(() -> f.tcolor = c))));
        props.addView(chip("Aa " + f.tsize, () -> askInt(T("Tamaño del texto (sp)", "Text size (sp)"), f.tsize, 6, 160, v -> edit(() -> f.tsize = v))));
        props.addView(chip(f.bold ? "𝗕 ✓" : "𝗕", () -> edit(() -> f.bold = !f.bold)));
    }

    private void btnProps(final VisualModel.Btn b) {
        props.addView(chip("🗑", () -> confirm(T("¿Borrar este botón?", "Delete this button?"), () -> {
            pushUndo();
            vm.buttons.remove(b);
            selKind = -1;
            selRef = null;
            rebuildBtns();
            saveSoon();
            buildProps();
            overlay.invalidate();
        })));
        props.addView(chip("⚙ " + T("Qué hace", "What it does"), () -> {
            flush();
            toast(T("En Reglas: «cuando botón \"" + b.name + "\" pulsado»", "In Rules: “when button \"" + b.name + "\" pressed”"));
            startActivity(new Intent(this, LauncherBuilderActivity.class));
        }));
        props.addView(chip("✎ " + T("Símbolo", "Symbol"), () -> askText(T("Símbolo", "Symbol"), b.icon, g -> editBtn(() -> b.icon = g))));
        props.addView(chip("✎ " + T("Etiqueta", "Label"), () -> askText(T("Etiqueta (vacío = ninguna)", "Label (empty = none)"), b.label, t -> editBtn(() -> b.label = t))));
        props.addView(chip("🎨 " + T("Fondo", "Fill"), () -> askColor(T("Fondo", "Fill"), b.bg, true, c -> editBtn(() -> b.bg = c))));
        props.addView(chip("🎨 " + T("Texto", "Text"), () -> askColor(T("Color del texto", "Text color"), b.tcolor, true, c -> editBtn(() -> b.tcolor = c))));
        props.addView(chip(T("Forma: ", "Shape: ") + b.shape, () -> editBtn(() -> b.shape = b.shape.equals("circle") ? "round" : b.shape.equals("round") ? "square" : "circle")));
        props.addView(chip(T("Opacidad ", "Opacity ") + b.opacity + "%", () -> askInt(T("Opacidad (%)", "Opacity (%)"), b.opacity, 5, 100, v -> editBtn(() -> b.opacity = v))));
        props.addView(chip(b.visible ? "👁" : "🚫", () -> editBtn(() -> b.visible = !b.visible)));
    }

    private void editBtn(Runnable change) {
        pushUndo();
        change.run();
        rebuildBtns();
        saveSoon();
        buildProps();
        overlay.invalidate();
    }

    // ================================================================== acciones de los gestos

    private String actName(String a) {
        if (a == null || a.isEmpty()) return "—";
        int i = a.indexOf(':');
        String k = i < 0 ? a : a.substring(0, i), v = i < 0 ? "" : a.substring(i + 1);
        switch (k) {
            case "macro": return "▶ " + v;
            case "app": return appName(v);
            case "drawer": return T("Cajón ", "Drawer ") + ("*".equals(v) ? T("(todas)", "(all)") : catName(v));
            case "panel": return "Panel " + v;
            case "shell": return "shell: " + v;
            case "home": return T("Ir al inicio", "Go home");
            default: return a;
        }
    }

    private void gestureDialog(final VisualModel.Free f) {
        final String[] names = {T("Toque", "Tap"), T("Doble toque", "Double tap"), T("Mantener", "Long press"), T("Deslizar ↑", "Swipe ↑"),
            T("Deslizar ↓", "Swipe ↓"), T("Deslizar ←", "Swipe ←"), T("Deslizar →", "Swipe →")};
        final String[] vals = {f.tap, f.dbl, f.hold, f.sup, f.sdn, f.slf, f.srt};
        String[] items = new String[7];
        for (int i = 0; i < 7; i++) {
            String shown = actName(vals[i]);
            if (i == 0 && vals[0].isEmpty() && (f.type.equals("app") || f.type.equals("category"))) shown = T("(abre lo suyo)", "(opens its target)");
            items[i] = names[i] + ":   " + shown;
        }
        dlg().setTitle(T("¿Qué hace cada gesto?", "What does each gesture do?")).setItems(items, (dd, i) ->
            chooseAction(names[i], vals[i], v -> edit(() -> {
                switch (i) {
                    case 0: f.tap = v; break;
                    case 1: f.dbl = v; break;
                    case 2: f.hold = v; break;
                    case 3: f.sup = v; break;
                    case 4: f.sdn = v; break;
                    case 5: f.slf = v; break;
                    default: f.srt = v; break;
                }
            }))).setNegativeButton(T("Cerrar", "Close"), null).show();
    }

    private void chooseAction(String gesture, String cur, final Consumer<String> cb) {
        String[] items = {T("Ninguna", "None"), T("Abrir una app…", "Open an app…"), T("Abrir el cajón / una categoría…", "Open the drawer / a category…"),
            T("Abrir un panel…", "Open a panel…"), T("Ejecutar una macro…", "Run a macro…"), T("Comando de shell…", "Shell command…"), T("Ir al inicio (cerrar cajón)", "Go home (close drawer)")};
        pickList(gesture + (cur.isEmpty() ? "" : "  [" + actName(cur) + "]"), items, i -> {
            switch (i) {
                case 0: cb.accept(""); break;
                case 1: pickApp(pkg -> cb.accept("app:" + pkg)); break;
                case 2: pickCategory(id -> cb.accept("drawer:" + id)); break;
                case 3: {
                    List<String> names = new ArrayList<>();
                    for (VisualModel.Panel p : vm.panels) if (!p.name.trim().isEmpty()) names.add(p.name);
                    if (names.isEmpty()) { toast(T("Todavía no hay paneles: creá uno con ⚙", "No panels yet: create one with ⚙")); break; }
                    pickList(T("Panel", "Panel"), names.toArray(new String[0]), k -> cb.accept("panel:" + names.get(k)));
                    break;
                }
                case 4: macroChooser(cb); break;
                case 5: askText(T("Comando de shell", "Shell command"), "", c -> { if (!c.trim().isEmpty()) cb.accept("shell:" + c.trim()); }); break;
                default: cb.accept("home"); break;
            }
        });
    }

    private void macroChooser(final Consumer<String> cb) {
        final List<String> names = new ArrayList<>();
        for (VisualModel.Rule r : vm.rules) if (r.macro && !r.name.trim().isEmpty()) names.add(r.name);
        final String[] items = new String[names.size() + 1];
        for (int i = 0; i < names.size(); i++) items[i] = "▶ " + names.get(i);
        items[names.size()] = T("＋ Nueva macro (pasos, esperas, bucles, condiciones)…", "＋ New macro (steps, waits, loops, conditions)…");
        pickList(T("Macro", "Macro"), items, i -> {
            if (i < names.size()) { cb.accept("macro:" + names.get(i)); return; }
            askText(T("Nombre de la macro", "Macro name"), "", n -> {
                if (n.trim().isEmpty()) return;
                VisualModel.Rule r = new VisualModel.Rule();
                r.macro = true;
                r.name = n.trim();
                vm.rules.add(r);
                dirty = true;
                flush();
                cb.accept("macro:" + r.name);
                toast(T("Macro creada. Armá sus pasos en ⚙ → Macros.", "Macro created. Build its steps in ⚙ → Macros."));
            });
        });
    }

    // ================================================================== diálogos

    private void confirm(String msg, Runnable yes) {
        dlg().setMessage(msg).setPositiveButton("OK", (dd, w) -> yes.run()).setNegativeButton(T("Cancelar", "Cancel"), null).show();
    }

    private void pickList(String title, String[] items, IntConsumer cb) {
        dlg().setTitle(title).setItems(items, (dd, i) -> cb.accept(i)).setNegativeButton(T("Cancelar", "Cancel"), null).show();
    }

    private void askText(String title, String init, Consumer<String> cb) {
        final EditText et = new EditText(this);
        et.setText(init);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(0x66FFFFFF);
        LinearLayout w = ZUi.box(this, LinearLayout.VERTICAL);
        w.setPadding(dp(20), dp(8), dp(20), 0);
        w.addView(et);
        dlg().setTitle(title).setView(w).setPositiveButton("OK", (dd, x) -> cb.accept(et.getText().toString()))
            .setNegativeButton(T("Cancelar", "Cancel"), null).show();
    }

    private void askInt(String title, int cur, int lo, int hi, IntConsumer cb) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        et.setText(String.valueOf(cur));
        et.setTextColor(Color.WHITE);
        LinearLayout w = ZUi.box(this, LinearLayout.VERTICAL);
        w.setPadding(dp(20), dp(8), dp(20), 0);
        w.addView(et);
        dlg().setTitle(title + "  (" + lo + " – " + hi + ")").setView(w).setPositiveButton("OK", (dd, x) -> {
            try { cb.accept(clamp(Integer.parseInt(et.getText().toString().trim()), lo, hi)); }
            catch (Exception ex) { toast(T("Número inválido", "Invalid number")); }
        }).setNegativeButton(T("Cancelar", "Cancel"), null).show();
    }

    private void askColor(String title, int cur, boolean alpha, IntConsumer cb) {
        final EditText hex = new EditText(this);
        hex.setHint("AARRGGBB");
        hex.setText(String.format("%08X", cur));
        hex.setTextColor(Color.WHITE);
        hex.setHintTextColor(0x66FFFFFF);
        LinearLayout w = ZUi.box(this, LinearLayout.VERTICAL);
        w.setPadding(dp(20), dp(8), dp(20), 0);
        w.addView(hex);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout sw = ZUi.box(this, LinearLayout.HORIZONTAL);
        int[] extra = {0, 0x55FFFFFF, 0x99000000};
        List<Integer> cols = new ArrayList<>();
        for (int c : extra) cols.add(c);
        for (int c : BuilderCatalog.COLOR_VAL) cols.add(c);
        for (final int col : cols) {
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(col);
            g.setCornerRadius(dp(8));
            g.setStroke(dp(1), 0x66FFFFFF);
            v.setBackground(g);
            v.setOnClickListener(x -> hex.setText(String.format("%08X", col)));
            sw.addView(v, ZKit.lp(this, dp(36), dp(36), 0, 0, 10, 8, 0));
        }
        hs.addView(sw);
        w.addView(hs);
        dlg().setTitle(title).setView(w).setPositiveButton("OK", (dd, x) -> {
            try {
                String h = hex.getText().toString().trim().replace("#", "");
                cb.accept(Color.parseColor("#" + h));
            } catch (Exception ex) { toast(T("Color inválido", "Invalid color")); }
        }).setNegativeButton(T("Cancelar", "Cancel"), null).show();
    }

    private void pickApp(final Consumer<String> cb) {
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<String[]> apps = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) apps.add(new String[]{ri.loadLabel(pm).toString(), ri.activityInfo.packageName});
        Collections.sort(apps, (x, y) -> x[0].toLowerCase().compareTo(y[0].toLowerCase()));
        CharSequence[] names = new CharSequence[apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = apps.get(i)[0];
        dlg().setTitle(T("Elegí una app", "Pick an app")).setItems(names, (dd, which) -> cb.accept(apps.get(which)[1])).show();
    }

    private void pickCategory(final Consumer<String> cb) {
        final List<String> ids = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        ids.add("*");
        names.add(T("Todas las apps", "All apps"));
        for (CategoryManager.Category c : cm.list()) { ids.add(c.id); names.add(c.icon + "  " + c.name); }
        pickList(T("Categoría", "Category"), names.toArray(new String[0]), i -> cb.accept(ids.get(i)));
    }
}
