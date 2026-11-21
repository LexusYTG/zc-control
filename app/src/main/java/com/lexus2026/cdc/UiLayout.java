package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Guarda el script del launcher (lenguaje UiLang) y deja listas las macros que necesita el motor de scripts. */
final class UiStore {
    private UiStore() {}

    static final String MACROS_ID = "zc_ui_macros";

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("zc_ui", Context.MODE_PRIVATE); }

    static String src(Context c) { return sp(c).getString("src", ""); }

    static boolean enabled(Context c) { return sp(c).getBoolean("on", false); }

    static long version(Context c) { return sp(c).getLong("ver", 0); }

    /** Guarda, y regenera las macros internas (gestos, inicio y comandos de elementos) que ejecuta el motor de scripts. */
    static void save(Context c, String src, boolean on) {
        SharedPreferences p = sp(c);
        p.edit().putString("src", src).putBoolean("on", on).putLong("ver", p.getLong("ver", 0) + 1).apply();
        UiLang.Scene sc = UiLang.parse(src, SL.es(c));
        ScriptStore.Item it = ScriptStore.get(c, MACROS_ID);
        if (it == null) { it = new ScriptStore.Item(); it.id = MACROS_ID; }
        it.name = "🧬 " + SL.t(c, "Launcher (interno, no editar)", "Launcher (internal, do not edit)");
        it.on = on;
        it.src = sc.macroSource();
        ScriptStore.save(c, it);
    }
}

/**
 * Intérprete del launcher descrito en UiLang: construye las vistas del script encima del launcher,
 * conecta los gestos con el motor de scripts y atiende las acciones sobre elementos (mostrar, ocultar, cambiar).
 */
final class UiLayout {
    private final LauncherEngine en;
    private final Activity act;
    private final float d;
    private final FrameLayout layer;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Map<String, View> byName = new HashMap<>();
    private final Map<View, UiLang.Node> nodes = new HashMap<>();
    private final List<Object[]> dyn = new ArrayList<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private UiLang.Scene sc;
    private long loadedVer = -1;
    private boolean loadedOn, running;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            refreshDynamic();
            h.postDelayed(this, 1000);
        }
    };

    UiLayout(LauncherEngine e) {
        en = e;
        act = e.act();
        d = e.dens();
        layer = new FrameLayout(act);
        layer.setClipChildren(false);
    }

    View layer() { return layer; }

    private int dp(float v) { return (int) (v * d + 0.5f); }

    private void toast(String s) { Toast.makeText(act, s, Toast.LENGTH_SHORT).show(); }

    // ------------------------------------------------------------------ ciclo de vida

    void onResume() {
        reload();
        applyHide();
        running = true;
        h.removeCallbacks(tick);
        h.post(tick);
    }

    void onPause() {
        running = false;
        h.removeCallbacks(tick);
    }

    void destroy() { onPause(); }

    void relayout() { layer.setPadding(0, en.insT(), 0, en.insB()); }

    /** Después de que el launcher reaplica sus ajustes: vuelve a ocultar lo pedido y refresca las listas de apps. */
    void afterApply() {
        applyHide();
        for (Runnable r : refreshers) r.run();
    }

    private void applyHide() {
        boolean on = sc != null && loadedOn;
        en.uiHide(on && sc.hideDesk, on && sc.hideDock);
    }

    void reload() {
        long v = UiStore.version(act);
        boolean on = UiStore.enabled(act);
        if (v == loadedVer && on == loadedOn) return;
        loadedVer = v;
        loadedOn = on;
        layer.removeAllViews();
        byName.clear();
        nodes.clear();
        dyn.clear();
        refreshers.clear();
        sc = null;
        if (!on) { applyHide(); return; }
        sc = UiLang.parse(UiStore.src(act), SL.es(act));
        relayout();
        for (UiLang.Node n : sc.roots) {
            try { build(n, layer); } catch (Throwable e) { toast(SL.t(act, "Error en la línea ", "Error on line ") + n.line + ": " + e.getMessage()); }
        }
        applyHide();
        refreshDynamic();
        if (sc.initMacro != null) h.postDelayed(() -> en.runUiMacro(sc.initMacro), 120);
    }

    // ------------------------------------------------------------------ construcción

    private static Object get(UiLang.Node n, String k) { return n.p.get(k); }

    private View build(final UiLang.Node n, ViewGroup parent) {
        View v;
        switch (n.kind) {
            case "box": case "button": v = container(n); break;
            case "text": case "clock": v = new TextView(act); break;
            case "icon": v = new ImageView(act); break;
            case "grid": v = grid(n); break;
            default: v = new View(act);
        }
        nodes.put(v, n);
        String name = (String) get(n, "name");
        if (name != null) byName.put(ScriptLang.norm(name), v);
        style(v, n);
        parent.addView(v, params(n, parent));
        if (v instanceof ViewGroup && !(v instanceof GridView)) {
            for (UiLang.Node k : n.kids) {
                try { build(k, (ViewGroup) v); } catch (Throwable e) { toast(SL.t(act, "Error en la línea ", "Error on line ") + k.line + ": " + e.getMessage()); }
            }
        }
        if (n.kind.equals("icon")) bindIcon((ImageView) v, n);
        wire(v, n);
        return v;
    }

    private ViewGroup container(UiLang.Node n) {
        String lay = (String) get(n, "layout");
        if ("row".equals(lay) || "col".equals(lay)) {
            LinearLayout l = new LinearLayout(act);
            l.setOrientation("row".equals(lay) ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            if (n.kind.equals("button")) l.setGravity(Gravity.CENTER);
            return l;
        }
        FrameLayout f = new FrameLayout(act);
        f.setClipChildren(false);
        return f;
    }

    private int px(UiLang.Dim dm, boolean horiz, int def) {
        if (dm == null) return def;
        switch (dm.mode) {
            case UiLang.D_FILL: return -1;
            case UiLang.D_WRAP: return -2;
            case UiLang.D_PCT: return (int) ((horiz ? act.getResources().getDisplayMetrics().widthPixels : act.getResources().getDisplayMetrics().heightPixels) * dm.v / 100f);
            default: return dp(dm.v);
        }
    }

    private ViewGroup.LayoutParams params(UiLang.Node n, ViewGroup parent) {
        boolean grid = n.kind.equals("grid"), icon = n.kind.equals("icon");
        int pw = px((UiLang.Dim) get(n, "w"), true, grid ? -1 : icon ? dp(48) : -2);
        int ph = px((UiLang.Dim) get(n, "h"), false, grid ? -1 : icon ? dp(48) : -2);
        int[] m = (int[]) get(n, "margin");
        int ml = m == null ? 0 : dp(m[0]), mt = m == null ? 0 : dp(m[1]), mr = m == null ? 0 : dp(m[2]), mb = m == null ? 0 : dp(m[3]);
        Integer x = (Integer) get(n, "x"), y = (Integer) get(n, "y");
        int dx = x == null ? 0 : dp(x), dy = y == null ? 0 : dp(y);
        if (parent instanceof LinearLayout) {
            Float wt = (Float) get(n, "weight");
            boolean horiz = ((LinearLayout) parent).getOrientation() == LinearLayout.HORIZONTAL;
            if (wt != null && horiz && get(n, "w") == null) pw = 0;
            if (wt != null && !horiz && get(n, "h") == null) ph = 0;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(pw, ph, wt == null ? 0f : wt);
            lp.setMargins(ml + dx, mt + dy, mr, mb);
            return lp;
        }
        String[] pos = (String[]) get(n, "pos");
        String pv = pos == null ? "top" : pos[0], ph2 = pos == null ? "left" : pos[1];
        int g = (pv.equals("bottom") ? Gravity.BOTTOM : pv.equals("center") ? Gravity.CENTER_VERTICAL : Gravity.TOP)
            | (ph2.equals("right") ? Gravity.END : ph2.equals("center") ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pw, ph, g);
        lp.leftMargin = ml + (ph2.equals("right") ? 0 : dx);
        lp.rightMargin = mr + (ph2.equals("right") ? dx : 0);
        lp.topMargin = mt + (pv.equals("bottom") ? 0 : dy);
        lp.bottomMargin = mb + (pv.equals("bottom") ? dy : 0);
        return lp;
    }

    private void style(View v, UiLang.Node n) {
        boolean btn = n.kind.equals("button");
        Integer bg = (Integer) get(n, "bg");
        int[] grad = (int[]) get(n, "grad");
        Integer radius = (Integer) get(n, "radius");
        int[] border = (int[]) get(n, "border");
        if (btn && bg == null && grad == null) bg = 0xFF3D7BFF;
        if (btn && radius == null) radius = 14;
        if (bg != null || grad != null || border != null) {
            GradientDrawable g = new GradientDrawable();
            if (grad != null) {
                g.setOrientation(grad[2] == 90 ? GradientDrawable.Orientation.BOTTOM_TOP : grad[2] == 0 ? GradientDrawable.Orientation.LEFT_RIGHT
                    : grad[2] == 180 ? GradientDrawable.Orientation.RIGHT_LEFT : GradientDrawable.Orientation.TOP_BOTTOM);
                g.setColors(new int[]{grad[0], grad[1]});
            } else if (bg != null) g.setColor(bg);
            if (radius != null) g.setCornerRadius(radius < 0 ? 10000f : dp(radius));
            if (border != null) g.setStroke(dp(border[1]), border[0]);
            v.setBackground(g);
            if (radius != null) v.setClipToOutline(true);
        } else v.setBackground(null);
        Integer op = (Integer) get(n, "opacity");
        v.setAlpha(op == null ? 1f : Math.max(0, Math.min(100, op)) / 100f);
        Integer sh = (Integer) get(n, "shadow");
        v.setElevation(sh == null ? 0 : dp(sh));
        Float rot = (Float) get(n, "rot");
        v.setRotation(rot == null ? 0 : rot);
        int[] pad = (int[]) get(n, "pad");
        if (pad == null && btn) pad = new int[]{16, 10, 16, 10};
        if (pad != null) v.setPadding(dp(pad[0]), dp(pad[1]), dp(pad[2]), dp(pad[3]));
        Boolean vis = (Boolean) get(n, "visible");
        v.setVisibility(Boolean.FALSE.equals(vis) ? View.GONE : View.VISIBLE);

        if (v instanceof TextView) styleText((TextView) v, n);
        else if (btn && v instanceof ViewGroup) {
            // etiqueta propia del botón
            String label = (String) get(n, "content");
            ViewGroup vg = (ViewGroup) v;
            View old = vg.findViewWithTag("zc_label");
            if (old != null) vg.removeView(old);
            if (label != null) {
                TextView t = new TextView(act);
                t.setTag("zc_label");
                styleText(t, n);
                vg.addView(t, vg instanceof LinearLayout ? new LinearLayout.LayoutParams(-2, -2) : new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
                if (!interpolates(label)) t.setText(label); else dyn.add(new Object[]{t, n});
            }
        }
        if (v instanceof ImageView) {
            Integer fg = (Integer) get(n, "fg");
            if (fg != null) ((ImageView) v).setColorFilter(fg);
            ((ImageView) v).setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
    }

    private void styleText(TextView t, UiLang.Node n) {
        Integer fg = (Integer) get(n, "fg");
        t.setTextColor(fg == null ? Color.WHITE : fg);
        Float sz = (Float) get(n, "textsize");
        t.setTextSize(sz == null ? (n.kind.equals("clock") ? 40f : 16f) : sz);
        if (Boolean.TRUE.equals(get(n, "bold"))) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        String al = (String) get(n, "align");
        t.setGravity("right".equals(al) ? Gravity.END : "center".equals(al) ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        if (n.kind.equals("clock") || n.kind.equals("text")) {
            String c = (String) get(n, "content");
            if (n.kind.equals("text") && c != null && !interpolates(c)) t.setText(c);
            else dynAdd(t, n);
        }
    }

    private void dynAdd(TextView t, UiLang.Node n) {
        for (int i = dyn.size() - 1; i >= 0; i--) if (dyn.get(i)[0] == t) dyn.remove(i);
        dyn.add(new Object[]{t, n});
    }

    private static boolean interpolates(String s) { return s != null && s.indexOf('{') >= 0; }

    // ------------------------------------------------------------------ gestos

    private void wire(final View v, final UiLang.Node n) {
        final Map<String, String> hd = n.handlers;
        boolean iconDefault = n.kind.equals("icon") && !hd.containsKey("tap");
        if (hd.isEmpty() && !iconDefault) return;
        boolean complex = hd.containsKey("double") || hd.containsKey("swipe_up") || hd.containsKey("swipe_down")
            || hd.containsKey("swipe_left") || hd.containsKey("swipe_right");
        if (!complex) {
            if (hd.containsKey("tap") || iconDefault) v.setOnClickListener(x -> run(n, "tap"));
            if (hd.containsKey("hold")) v.setOnLongClickListener(x -> { run(n, "hold"); return true; });
            return;
        }
        final GestureDetector gd = new GestureDetector(act, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { run(n, "tap"); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { run(n, "double"); return true; }
            @Override public void onLongPress(MotionEvent e) { run(n, "hold"); }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null) return false;
                float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
                String dir = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? "right" : "left") : (dy > 0 ? "down" : "up");
                if (Math.max(Math.abs(dx), Math.abs(dy)) < dp(40)) return false;
                return run(n, "swipe_" + dir);
            }
        });
        v.setClickable(true);
        v.setOnTouchListener((x, e) -> gd.onTouchEvent(e));
    }

    private boolean run(UiLang.Node n, String ev) {
        if (ev.equals("tap") && !n.handlers.containsKey("tap") && n.kind.equals("icon")) {
            String app = (String) get(n, "app");
            ApplicationManager.App a = app == null ? null : findApp(app);
            if (a != null) en.launchApp(a.pkg);
            return true;
        }
        String m = n.handlers.get(ev);
        if (m == null) return false;
        en.runUiMacro(m);
        return true;
    }

    // ------------------------------------------------------------------ apps, iconos y grillas

    private ApplicationManager.App findApp(String name) {
        String q = ScriptLang.norm(name);
        ApplicationManager.App part = null;
        for (ApplicationManager.App a : en.apps().all()) {
            String l = ScriptLang.norm(a.label);
            if (l.equals(q) || a.pkg.equals(name)) return a;
            if (part == null && l.contains(q)) part = a;
        }
        return part;
    }

    private void bindIcon(final ImageView iv, final UiLang.Node n) {
        final Runnable r = () -> {
            String app = (String) get(n, "app");
            ApplicationManager.App a = app == null ? null : findApp(app);
            iv.setImageDrawable(a == null ? null : a.icon);
        };
        r.run();
        refreshers.add(r);
    }

    private List<ApplicationManager.App> data(UiLang.Node n) {
        String[] src = (String[]) get(n, "source");
        List<ApplicationManager.App> out = new ArrayList<>();
        String type = src == null ? "all" : src[0];
        LauncherSettings st = en.sets();
        switch (type) {
            case "pinned":
                for (String p : st.pinned()) { ApplicationManager.App a = en.apps().find(p); if (a != null) out.add(a); }
                break;
            case "new": out.addAll(en.apps().pending(en.cats())); break;
            case "cat": {
                String q = ScriptLang.norm(src[1]);
                for (CategoryManager.Category c : en.cats().list())
                    if (ScriptLang.norm(c.name).equals(q)) { out.addAll(en.apps().inCategory(en.cats(), c.id)); break; }
                break;
            }
            default:
                for (ApplicationManager.App a : en.apps().all()) if (!st.isHidden(a.pkg)) out.add(a);
        }
        return out;
    }

    private GridView grid(final UiLang.Node n) {
        final GridView gv = new GridView(act);
        Integer cols = (Integer) get(n, "cols"), gap = (Integer) get(n, "gap");
        gv.setNumColumns(cols == null ? 4 : Math.max(1, cols));
        gv.setVerticalSpacing(dp(gap == null ? 8 : gap));
        gv.setHorizontalSpacing(dp(gap == null ? 8 : gap));
        gv.setSelector(new ColorDrawable(0));
        gv.setClipToPadding(false);
        final List<ApplicationManager.App> items = new ArrayList<>();
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup p) {
                ApplicationManager.App a = items.get(i);
                LinearLayout cell = new LinearLayout(act);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(2), dp(6), dp(2), dp(6));
                Integer is = (Integer) get(n, "iconsize");
                int sz = dp(is == null ? 52 : is);
                ImageView iv = new ImageView(act);
                iv.setImageDrawable(a.icon);
                cell.addView(iv, new LinearLayout.LayoutParams(sz, sz));
                if (!Boolean.FALSE.equals(get(n, "labels"))) {
                    TextView t = new TextView(act);
                    t.setText(a.label);
                    t.setSingleLine();
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setGravity(Gravity.CENTER);
                    Integer fg = (Integer) get(n, "fg");
                    t.setTextColor(fg == null ? Color.WHITE : fg);
                    Float ts = (Float) get(n, "textsize");
                    t.setTextSize(ts == null ? 11f : ts);
                    cell.addView(t, new LinearLayout.LayoutParams(-1, -2));
                }
                return cell;
            }
        };
        gv.setAdapter(ad);
        gv.setOnItemClickListener((p, v, i, id) -> { if (i < items.size()) en.launchApp(items.get(i).pkg); });
        Runnable refresh = () -> {
            Integer c2 = (Integer) get(n, "cols");
            gv.setNumColumns(c2 == null ? 4 : Math.max(1, c2));
            items.clear();
            items.addAll(data(n));
            ad.notifyDataSetChanged();
        };
        refresh.run();
        refreshers.add(refresh);
        return gv;
    }

    // ------------------------------------------------------------------ texto dinámico

    private String interp(String s) {
        if (s == null || s.indexOf('{') < 0) return s == null ? "" : s;
        Matcher m = Pattern.compile("\\{([^}]+)\\}").matcher(s);
        StringBuffer sb = new StringBuffer();
        Calendar c = Calendar.getInstance();
        Map<String, String> vars = ScriptStore.vars(act);
        while (m.find()) {
            String k = ScriptLang.norm(m.group(1).trim());
            String val;
            switch (k) {
                case "hora": case "hour": val = String.format(Locale.US, "%02d", c.get(Calendar.HOUR_OF_DAY)); break;
                case "minuto": case "minute": val = String.format(Locale.US, "%02d", c.get(Calendar.MINUTE)); break;
                case "segundo": case "second": val = String.format(Locale.US, "%02d", c.get(Calendar.SECOND)); break;
                case "dia": case "day": val = String.valueOf(c.get(Calendar.DAY_OF_MONTH)); break;
                case "mes": case "month": val = String.valueOf(c.get(Calendar.MONTH) + 1); break;
                case "ano": case "year": val = String.valueOf(c.get(Calendar.YEAR)); break;
                case "fecha": case "date": val = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()); break;
                case "bateria": case "battery": val = String.valueOf(battery()); break;
                case "cargando": case "charging": val = charging() ? SL.t(act, "si", "yes") : "no"; break;
                default: val = vars.containsKey(k) ? vars.get(k) : m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private int battery() {
        try { return ((BatteryManager) act.getSystemService(Context.BATTERY_SERVICE)).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); }
        catch (Throwable e) { return 0; }
    }

    private boolean charging() {
        try {
            Intent i = act.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int s = i == null ? 0 : i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            return s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL;
        } catch (Throwable e) { return false; }
    }

    private void refreshDynamic() {
        for (Object[] o : dyn) {
            TextView t = (TextView) o[0];
            UiLang.Node n = (UiLang.Node) o[1];
            String c = (String) get(n, "content");
            if (n.kind.equals("clock")) {
                String f = (String) get(n, "format");
                try { t.setText(new SimpleDateFormat(f == null ? "HH:mm" : f, Locale.getDefault()).format(new Date())); }
                catch (Throwable e) { t.setText(f); }
            } else t.setText(interp(c));
        }
    }

    // ------------------------------------------------------------------ comandos sobre elementos

    /** Llamado por el motor de scripts cuando una macro interna «__uicmd_N» se ejecuta. */
    void command(String macro) {
        if (sc == null) return;
        int idx;
        try { idx = Integer.parseInt(macro.substring("__uicmd_".length())); } catch (Throwable e) { return; }
        if (idx < 0 || idx >= sc.cmds.size()) return;
        UiLang.Cmd c = sc.cmds.get(idx);
        View v = byName.get(c.name);
        if (v == null) { toast(SL.t(act, "No existe el elemento: ", "Element not found: ") + c.name); return; }
        UiLang.Node n = nodes.get(v);
        switch (c.type) {
            case UiLang.Cmd.SHOW: show(v, n, c); break;
            case UiLang.Cmd.HIDE: hide(v, n, c); break;
            case UiLang.Cmd.TOGGLE:
                if (v.getVisibility() == View.VISIBLE && !Boolean.FALSE.equals(get(n, "visible"))) hide(v, n, c); else show(v, n, c);
                break;
            default:
                n.p.putAll(c.patch.p);
                style(v, n);
                if (v.getParent() instanceof ViewGroup) v.setLayoutParams(params(n, (ViewGroup) v.getParent()));
                if (n.kind.equals("icon")) bindIcon((ImageView) v, n);
                if (n.kind.equals("grid")) afterApply();
                refreshDynamic();
        }
    }

    private void show(final View v, UiLang.Node n, UiLang.Cmd c) {
        n.p.put("visible", true);
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        final Integer op = (Integer) get(n, "opacity");
        final float target = op == null ? 1f : op / 100f;
        final String dir = c.dir, anim = c.anim == null ? (c.dir == null ? "fade" : "slide") : c.anim;
        final long dur = c.dur;
        if (anim.equals("none")) { v.setAlpha(target); return; }
        v.post(() -> {
            float W = layer.getWidth(), H = layer.getHeight(), tx = 0, ty = 0;
            if (dir != null) {
                if (dir.equals("left")) tx = -(v.getLeft() + v.getWidth());
                else if (dir.equals("right")) tx = W - v.getLeft();
                else if (dir.equals("up")) ty = -(v.getTop() + v.getHeight());
                else ty = H - v.getTop();
            }
            if (anim.equals("zoom")) { v.setScaleX(0.6f); v.setScaleY(0.6f); v.setAlpha(0f); }
            else if (anim.equals("fade")) v.setAlpha(0f);
            else { v.setTranslationX(tx); v.setTranslationY(ty); v.setAlpha(anim.equals("bounce") ? target : 0f); }
            v.animate().translationX(0).translationY(0).scaleX(1f).scaleY(1f).alpha(target).setDuration(dur)
                .setInterpolator(anim.equals("bounce") ? new OvershootInterpolator(1.3f) : new DecelerateInterpolator()).start();
        });
    }

    private void hide(final View v, UiLang.Node n, UiLang.Cmd c) {
        n.p.put("visible", false);
        v.animate().cancel();
        final String dir = c.dir, anim = c.anim == null ? (c.dir == null ? "fade" : "slide") : c.anim;
        final Integer op = (Integer) get(n, "opacity");
        final float base = op == null ? 1f : op / 100f;
        Runnable end = () -> {
            v.setVisibility(View.GONE);
            v.setTranslationX(0); v.setTranslationY(0); v.setScaleX(1f); v.setScaleY(1f); v.setAlpha(base);
        };
        if (anim.equals("none") || v.getVisibility() != View.VISIBLE) { end.run(); return; }
        float W = layer.getWidth(), H = layer.getHeight(), tx = 0, ty = 0;
        if (dir != null) {
            if (dir.equals("left")) tx = -(v.getLeft() + v.getWidth());
            else if (dir.equals("right")) tx = W - v.getLeft();
            else if (dir.equals("up")) ty = -(v.getTop() + v.getHeight());
            else ty = H - v.getTop();
        }
        android.view.ViewPropertyAnimator pa = v.animate().setDuration(c.dur).setInterpolator(new DecelerateInterpolator()).withEndAction(end);
        if (anim.equals("zoom")) pa.scaleX(0.6f).scaleY(0.6f).alpha(0f);
        else if (anim.equals("fade")) pa.alpha(0f);
        else pa.translationX(tx).translationY(ty);
        pa.start();
    }
}
