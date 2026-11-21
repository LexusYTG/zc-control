package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lexus2026.cdc.ScriptLang.Act;
import com.lexus2026.cdc.ScriptLang.Ins;
import com.lexus2026.cdc.ScriptLang.Rule;
import com.lexus2026.cdc.ScriptLang.Tok;

final class ScriptRuntime {
    private static final class Frame {
        final List<Ins> code;
        int pc;
        Frame(List<Ins> c) { code = c; }
    }

    private static final class Run {
        final List<Frame> stack = new ArrayList<>();
        String arg = "", pkg = "";
        int n;
        boolean blocked, done, waiting, dirty;
        int steps;
        final int[] ctr = new int[32];
    }

    private final LauncherEngine en;
    private final Activity act;
    private final LauncherSettings st;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final float d;
    private final Random rnd = new Random();
    private ScriptLang.Program pg = new ScriptLang.Program();
    private final Map<String, String> vars;
    private final Map<String, View> panels = new LinkedHashMap<>();
    private final Map<String, View> btnViews = new LinkedHashMap<>();
    private final Set<String> btnHidden = new LinkedHashSet<>();
    private final Map<String, View> edges = new LinkedHashMap<>();
    private final List<Runnable> timers = new ArrayList<>();
    private final List<Runnable> holds = new ArrayList<>();
    private long loadedVer = -1;
    private boolean loadedMaster = true, resumed, booted;
    private int lastInsT = -1, lastInsB = -1;

    ScriptRuntime(LauncherEngine e) {
        en = e;
        act = e.act();
        st = e.sets();
        d = e.dens();
        vars = ScriptStore.vars(act);
    }

    void attach() {
        final GridView desk = en.deskV();
        final GestureDetector gd = new GestureDetector(act, new GestureDetector.SimpleOnGestureListener() {
            private boolean empty(MotionEvent ev) {
                return desk.pointToPosition((int) ev.getX(), (int) ev.getY()) == AdapterView.INVALID_POSITION;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent ev) { if (empty(ev)) fire("bg_tap", null, 0); return false; }
            @Override public boolean onDoubleTap(MotionEvent ev) { if (empty(ev)) fire("bg_double", null, 0); return false; }
            @Override public void onLongPress(MotionEvent ev) { if (empty(ev)) fire("bg_hold", null, 0); }
        });
        desk.setOnTouchListener((v, ev) -> { gd.onTouchEvent(ev); return false; });
        ensureLoaded();
    }

    private String T(String es, String e) { return SL.t(act, es, e); }

    private void toast(String s) { Toast.makeText(act, s, Toast.LENGTH_SHORT).show(); }

    private void ensureLoaded() {
        long v = ScriptStore.version(act);
        boolean m = ScriptStore.master(act);
        if (v == loadedVer && m == loadedMaster) return;
        loadedVer = v;
        loadedMaster = m;
        ScriptLang.ES = SL.es(act);
        ScriptLang.Program np = new ScriptLang.Program();
        if (m) for (ScriptStore.Item it : ScriptStore.list(act)) if (it.on) ScriptLang.parse(it.src, np);
        pg = np;
        lastInsT = -1;
        if (resumed) { startTimers(); }
        rebuildButtons();
        rebuildEdges();
    }

    void onResume() {
        ensureLoaded();
        resumed = true;
        startTimers();
        rebuildButtons();
        rebuildEdges();
        if (!booted) { booted = true; fire("boot", null, 0); }
        fire("resume", null, 0);
    }

    void onPause() {
        resumed = false;
        cancelTimers();
        cancelHold();
    }

    void stop() {
        onPause();
        for (String k : new ArrayList<>(panels.keySet())) closePanel(k, false);
        for (View v : btnViews.values()) en.rootV().removeView(v);
        for (View v : edges.values()) en.rootV().removeView(v);
        btnViews.clear();
        edges.clear();
    }

    void relayout() {
        if (en.insT() == lastInsT && en.insB() == lastInsB) return;
        rebuildButtons();
    }

    boolean closeTopPanel() {
        if (panels.isEmpty()) return false;
        String k = null;
        for (String x : panels.keySet()) k = x;
        closePanel(k, true);
        return true;
    }

    boolean fire(String ev, String arg, int n) {
        ensureLoaded();
        boolean blocked = false;
        if (pg.rules.isEmpty()) return false;
        String label = arg, pkg = arg;
        if (ev.startsWith("app_") && arg != null) {
            ApplicationManager.App a = en.apps().find(arg);
            if (a != null) label = a.label;
        } else if (ev.startsWith("drawer_") && arg != null) label = drawerName(arg);
        for (Rule r : new ArrayList<>(pg.rules)) {
            if (!matches(r, ev, arg, label, n)) continue;
            Run run = new Run();
            run.arg = label == null ? "" : label;
            run.pkg = pkg == null ? "" : pkg;
            run.n = n;
            run.stack.add(new Frame(r.code));
            step(run);
            if (run.blocked) blocked = true;
        }
        return blocked;
    }

    private boolean matches(Rule r, String ev, String arg, String label, int n) {
        if (!r.ev.equals(ev)) return false;
        if (ev.equals("tap_center")) return r.n == n;
        if (ev.equals("hold_center")) return r.ms == n;
        if (r.arg == null) return true;
        if (arg == null) return false;
        if (ev.startsWith("app_")) {
            String a = ScriptLang.norm(arg), l = ScriptLang.norm(label);
            return a.equals(r.arg) || l.equals(r.arg) || l.contains(r.arg);
        }
        if (ev.startsWith("drawer_")) {
            String l = ScriptLang.norm(label);
            if (arg.equals("*")) return r.arg.equals("apps") || r.arg.equals("todas") || r.arg.equals("all");
            if (arg.equals("new")) return r.arg.equals("nuevas") || r.arg.equals("new") || r.arg.equals("nuevos");
            return l.equals(r.arg) || l.contains(r.arg) || ScriptLang.norm(arg).equals(r.arg);
        }
        return ScriptLang.norm(arg).equals(r.arg);
    }

    boolean hasRule(String ev, String arg) {
        for (Rule r : pg.rules) if (r.ev.equals(ev) && (r.arg == null || (arg != null && r.arg.equals(ScriptLang.norm(arg))))) return true;
        return false;
    }

    void runMacro(String name) {
        ensureLoaded();
        List<Ins> code = pg.macros.get(ScriptLang.norm(name));
        if (code == null) { toast(T("No existe la macro: ", "Macro not found: ") + name); return; }
        Run run = new Run();
        run.stack.add(new Frame(code));
        step(run);
    }

    void holdStart(final Runnable onBlocked) {
        cancelHold();
        ensureLoaded();
        Set<Long> seen = new LinkedHashSet<>();
        for (Rule r : pg.rules) if (r.ev.equals("hold_center")) seen.add(r.ms);
        for (final Long ms : seen) {
            Runnable rn = () -> { if (fire("hold_center", null, (int) (long) ms) && onBlocked != null) onBlocked.run(); };
            holds.add(rn);
            h.postDelayed(rn, ms);
        }
    }

    void cancelHold() {
        for (Runnable r : holds) h.removeCallbacks(r);
        holds.clear();
    }

    private void cancelTimers() {
        for (Runnable r : timers) h.removeCallbacks(r);
        timers.clear();
    }

    private void startTimers() {
        cancelTimers();
        boolean hasAt = false;
        for (final Rule r : pg.rules) {
            if (r.ev.equals("every")) {
                Runnable rn = new Runnable() {
                    @Override public void run() {
                        if (!resumed) return;
                        runRule(r, null, 0);
                        h.postDelayed(this, r.period);
                    }
                };
                timers.add(rn);
                h.postDelayed(rn, r.period);
            } else if (r.ev.equals("at")) hasAt = true;
        }
        if (hasAt) {
            Runnable tick = new Runnable() {
                @Override public void run() {
                    if (!resumed) return;
                    Calendar c = Calendar.getInstance();
                    String key = c.get(Calendar.DAY_OF_YEAR) + ":" + c.get(Calendar.HOUR_OF_DAY) + ":" + c.get(Calendar.MINUTE);
                    for (Rule r : pg.rules)
                        if (r.ev.equals("at") && r.hh == c.get(Calendar.HOUR_OF_DAY) && r.mm == c.get(Calendar.MINUTE) && !key.equals(r.lastKey)) {
                            r.lastKey = key;
                            runRule(r, null, 0);
                        }
                    h.postDelayed(this, 15000);
                }
            };
            timers.add(tick);
            h.post(tick);
        }
    }

    private void runRule(Rule r, String arg, int n) {
        Run run = new Run();
        run.arg = arg == null ? "" : arg;
        run.n = n;
        run.stack.add(new Frame(r.code));
        step(run);
    }

    @SuppressWarnings("unchecked")
    private void step(final Run r) {
        r.waiting = false;
        while (!r.done && !r.waiting) {
            if (++r.steps > 20000) {
                toast(T("Script detenido: demasiados pasos (¿bucle infinito?)", "Script stopped: too many steps (infinite loop?)"));
                r.done = true;
                break;
            }
            Frame f = r.stack.get(r.stack.size() - 1);
            if (f.pc >= f.code.size()) {
                r.stack.remove(r.stack.size() - 1);
                if (r.stack.isEmpty()) r.done = true;
                continue;
            }
            Ins in = f.code.get(f.pc++);
            try {
                switch (in.op) {
                    case ScriptLang.A_ACT:
                        if (exec((Act) in.a, r)) r.waiting = true;
                        break;
                    case ScriptLang.A_JMP: f.pc = (Integer) in.a; break;
                    case ScriptLang.A_JF:
                        if (!cond((List<Tok>) in.a, r)) f.pc = (Integer) in.b;
                        break;
                    case ScriptLang.A_WAIT: {
                        long ms = (long) (num(eval((List<Tok>) in.a, r)) * (Long) in.b);
                        h.postDelayed(() -> step(r), Math.max(0, Math.min(ms, 3600000L)));
                        r.waiting = true;
                        break;
                    }
                    case ScriptLang.A_RINIT: r.ctr[(Integer) in.a] = (int) num(eval((List<Tok>) in.b, r)); break;
                    case ScriptLang.A_RTEST: {
                        int k = (Integer) in.a;
                        if (r.ctr[k] <= 0) f.pc = (Integer) in.b; else r.ctr[k]--;
                        break;
                    }
                    case ScriptLang.A_CALL: {
                        if (((String) in.a).startsWith("__uicmd_")) { en.uiCommand((String) in.a); break; }
                        List<Ins> mc = pg.macros.get((String) in.a);
                        if (mc != null && r.stack.size() < 20) r.stack.add(new Frame(mc));
                        break;
                    }
                    case ScriptLang.A_STOP: r.done = true; break;
                    case ScriptLang.A_BLOCK: r.blocked = true; break;
                    default:
                }
            } catch (Throwable e) {
                toast(T("Error en la línea ", "Error on line ") + in.line + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                r.done = true;
            }
        }
        if (r.dirty) {
            r.dirty = false;
            en.applyAll();
        }
    }

    private String drawerName(String id) {
        if (id == null) return "";
        if (id.equals("*")) return "apps";
        if (id.equals("new")) return T("nuevas", "new");
        CategoryManager.Category c = en.cats().byId(id);
        return c == null ? id : c.name;
    }

    private ApplicationManager.App findApp(String name) {
        if (name == null) return null;
        String n = ScriptLang.norm(name.trim());
        List<ApplicationManager.App> all = en.apps().all();
        for (ApplicationManager.App a : all) if (ScriptLang.norm(a.pkg).equals(n)) return a;
        for (ApplicationManager.App a : all) if (ScriptLang.norm(a.label).equals(n)) return a;
        for (ApplicationManager.App a : all) if (ScriptLang.norm(a.label).contains(n)) return a;
        for (ApplicationManager.App a : all) if (ScriptLang.norm(a.pkg).contains(n)) return a;
        return null;
    }

    private CategoryManager.Category findCat(String name) {
        if (name == null) return null;
        String n = ScriptLang.norm(name.trim());
        for (CategoryManager.Category c : en.cats().list()) if (c.id.equals(name) || ScriptLang.norm(c.name).equals(n)) return c;
        for (CategoryManager.Category c : en.cats().list()) if (ScriptLang.norm(c.name).contains(n)) return c;
        return null;
    }

    private ApplicationManager.App needApp(String name) {
        ApplicationManager.App a = findApp(name);
        if (a == null) toast(T("No encuentro la app: ", "App not found: ") + name);
        return a;
    }

    @SuppressWarnings("unchecked")
    private boolean exec(Act a, final Run r) {
        Object[] p = a.p;
        switch (a.k) {
            case "open_app": { ApplicationManager.App x = needApp(interp((String) p[0], r)); if (x != null) en.launchApp(x.pkg); break; }
            case "open_all": en.openD("*"); break;
            case "open_new": en.openD("new"); break;
            case "open_cat": {
                CategoryManager.Category c = findCat(interp((String) p[0], r));
                if (c == null) toast(T("No existe esa categoría", "Category not found")); else en.openD(c.id);
                break;
            }
            case "close_drawer": en.closeD(); break;
            case "close_all": en.closeD(); for (String k : new ArrayList<>(panels.keySet())) closePanel(k, true); break;
            case "open_launcher_settings": act.startActivity(new Intent(act, LauncherSettingsActivity.class)); break;
            case "open_editor": act.startActivity(new Intent(act, ScriptEditorActivity.class)); break;
            case "open_cc": act.startActivity(new Intent(act, ControlCenterActivity.class)); break;
            case "open_syssettings": act.startActivity(new Intent(Settings.ACTION_SETTINGS)); break;
            case "open_url": {
                try { act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(interp((String) p[0], r)))); }
                catch (Throwable e) { toast(T("No se pudo abrir el enlace", "Could not open the link")); }
                break;
            }
            case "panel_open": openPanel(interp((String) p[0], r), (String) p[1], (String) p[2], (Long) p[3], (Long) p[4]); break;
            case "panel_toggle": {
                String key = ScriptLang.norm(interp((String) p[0], r));
                if (panels.containsKey(key)) closePanel(key, true);
                else openPanel(interp((String) p[0], r), (String) p[1], (String) p[2], (Long) p[3], (Long) p[4]);
                break;
            }
            case "panel_close": {
                if (p[0] == null) { for (String k : new ArrayList<>(panels.keySet())) closePanel(k, true); }
                else closePanel(ScriptLang.norm(interp((String) p[0], r)), true);
                break;
            }
            case "btn_show": btnHidden.remove(ScriptLang.norm((String) p[0])); rebuildButtons(); break;
            case "btn_hide": btnHidden.add(ScriptLang.norm((String) p[0])); rebuildButtons(); break;
            case "btn_toggle": {
                String k = ScriptLang.norm((String) p[0]);
                if (!btnHidden.remove(k)) btnHidden.add(k);
                rebuildButtons();
                break;
            }
            case "app_hide": { ApplicationManager.App x = needApp(interp((String) p[0], r)); if (x != null) { st.setHidden(x.pkg, true); st.unpin(x.pkg); r.dirty = true; } break; }
            case "app_show": { ApplicationManager.App x = needApp(interp((String) p[0], r)); if (x != null) { st.setHidden(x.pkg, false); r.dirty = true; } break; }
            case "pin": { ApplicationManager.App x = needApp(interp((String) p[0], r)); if (x != null) { st.pin(x.pkg); r.dirty = true; } break; }
            case "unpin": { ApplicationManager.App x = needApp(interp((String) p[0], r)); if (x != null) { st.unpin(x.pkg); r.dirty = true; } break; }
            case "dock_put": {
                ApplicationManager.App x = needApp(interp((String) p[0], r));
                if (x == null) break;
                int idx = Math.max(0, Math.min(3, (int) num(eval((List<Tok>) p[1], r)) - 1));
                for (int k = 0; k < 4; k++) if (x.pkg.equals(st.getDock(k))) st.setDock(k, null);
                st.setDock(idx, x.pkg);
                r.dirty = true;
                break;
            }
            case "dock_clear": {
                int idx = Math.max(0, Math.min(3, (int) num(eval((List<Tok>) p[0], r)) - 1));
                st.setDock(idx, null);
                r.dirty = true;
                break;
            }
            case "dock_clear_app": {
                ApplicationManager.App x = needApp(interp((String) p[0], r));
                if (x != null) { for (int k = 0; k < 4; k++) if (x.pkg.equals(st.getDock(k))) st.setDock(k, null); r.dirty = true; }
                break;
            }
            case "cat_add_app": {
                ApplicationManager.App x = needApp(interp((String) p[0], r));
                CategoryManager.Category c = findCat(interp((String) p[1], r));
                if (x == null) break;
                if (c == null) { toast(T("No existe esa categoría", "Category not found")); break; }
                Set<String> ids = en.cats().of(x.pkg);
                ids.add(c.id);
                en.cats().setApp(x.pkg, ids);
                r.dirty = true;
                break;
            }
            case "cat_remove_app": {
                ApplicationManager.App x = needApp(interp((String) p[0], r));
                CategoryManager.Category c = findCat(interp((String) p[1], r));
                if (x != null && c != null) { en.cats().removeApp(x.pkg, c.id); r.dirty = true; }
                break;
            }
            case "cat_create": {
                String ic = (String) p[1];
                en.cats().add(interp((String) p[0], r), ic.isEmpty() ? "\uD83D\uDCC1" : ic, 0xFF3D7BFF);
                r.dirty = true;
                break;
            }
            case "cat_delete": {
                CategoryManager.Category c = findCat(interp((String) p[0], r));
                if (c != null) { en.cats().remove(c.id); r.dirty = true; }
                break;
            }
            case "set": applySetting((String) p[0], setValue((String) p[0], (List<Tok>) p[1], r), r); break;
            case "incr": {
                String key = (String) p[0];
                List<Tok> v = (List<Tok>) p[1];
                int sign = (Integer) p[2];
                double dv = v.isEmpty() ? 5 : num(eval(cleanUnits(v), r));
                applySetting(key, (int) Math.round(st.get(key) + sign * dv), r);
                break;
            }
            case "toggle_set": {
                String key = (String) p[0];
                applySetting(key, st.get(key) == 0 ? 1 : 0, r);
                break;
            }
            case "reset": st.reset((String) p[0]); r.dirty = true; break;
            case "reset_all": st.resetAll(); r.dirty = true; break;
            case "var": {
                vars.put((String) p[0], str(eval((List<Tok>) p[1], r)));
                ScriptStore.saveVars(act, vars);
                break;
            }
            case "var_incr": {
                String k = (String) p[0];
                List<Tok> v = (List<Tok>) p[1];
                double cur = num(numOrStr(vars.get(k)));
                double dv = v.isEmpty() ? 1 : num(eval(v, r));
                vars.put(k, str(cur + (Integer) p[2] * dv));
                ScriptStore.saveVars(act, vars);
                break;
            }
            case "toast": toast(interp((String) p[0], r)); break;
            case "vibrate": {
                long ms = ScriptLang.amountAfter((List<Tok>) p[0], 0, 1);
                if (ms <= 0) ms = 40;
                try {
                    Vibrator vb = (Vibrator) act.getSystemService(Context.VIBRATOR_SERVICE);
                    if (vb != null) vb.vibrate(VibrationEffect.createOneShot(Math.min(ms, 2000), VibrationEffect.DEFAULT_AMPLITUDE));
                } catch (Throwable ignored) { }
                break;
            }
            case "brightness": {
                try {
                    int v = Math.max(0, Math.min(100, (int) num(eval(cleanUnits((List<Tok>) p[0]), r))));
                    if (!Settings.System.canWrite(act)) { toast(T("Falta el permiso de modificar ajustes del sistema", "Missing the permission to modify system settings")); break; }
                    Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
                    Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, v * 255 / 100);
                } catch (Throwable ignored) { }
                break;
            }
            case "volume": {
                try {
                    int v = Math.max(0, Math.min(100, (int) num(eval(cleanUnits((List<Tok>) p[0]), r))));
                    android.media.AudioManager am = (android.media.AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) * v / 100, 0);
                } catch (Throwable ignored) { }
                break;
            }
            case "refresh": r.dirty = true; break;
            case "shell": {
                final String cmd = interp((String) p[0], r);
                runShell(cmd, new ShellDone() {
                    @Override public void done(String out) {
                        vars.put("salida", out);
                        vars.put("output", out);
                        step(r);
                    }
                });
                return true;
            }
            default:
        }
        return false;
    }

    private interface ShellDone { void done(String out); }

    private void runShell(final String cmd, final ShellDone cb) {
        new Thread(() -> {
            String out = "";
            try {
                if (Privileged.available()) {
                    ShellExec.Res res = ShellExec.exec(cmd, 8000);
                    out = res == null || res.out == null ? "" : res.out;
                } else {
                    final Process pr = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
                    h.postDelayed(pr::destroy, 8000);
                    BufferedReader br = new BufferedReader(new InputStreamReader(pr.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null && sb.length() < 4000) sb.append(line).append('\n');
                    out = sb.toString().trim();
                }
            } catch (Throwable ignored) { }
            final String fo = out.length() > 4000 ? out.substring(0, 4000) : out.trim();
            h.post(() -> cb.done(fo));
        }).start();
    }

    private List<Tok> cleanUnits(List<Tok> v) {
        List<Tok> l = new ArrayList<>(v);
        while (!l.isEmpty()) {
            Tok k = l.get(l.size() - 1);
            if (k.q || k.isNum) break;
            if (k.low.equals("dp") || k.low.equals("px") || k.low.equals("%") || k.low.equals("deg") || k.low.equals("grados") || k.low.equals("pt") || k.c != null && ScriptLang.unitMs(k, 0) > 0) l.remove(l.size() - 1);
            else break;
        }
        return l;
    }

    private int setValue(String key, List<Tok> v, Run r) {
        Integer e = ScriptLang.enumOf(key, v);
        if (e != null) return e;
        if (ScriptLang.COLOR_KEYS.contains(key)) {
            Integer c = ScriptLang.colorOf(v);
            if (c != null) return c;
            throw new RuntimeException(T("Color inválido", "Invalid color"));
        }
        if (ScriptLang.BOOL_KEYS.contains(key)) {
            Integer b = ScriptLang.boolOf(v);
            if (b != null) return b;
        }
        double mult = 1;
        if (key.equals(LauncherSettings.ANIM) || key.equals(LauncherSettings.HOLD)) {
            Tok last = v.get(v.size() - 1);
            if (!last.q && last.c != null) mult = ScriptLang.unitMs(last, 1);
        }
        return (int) Math.round(num(eval(cleanUnits(v), r)) * mult);
    }

    private void applySetting(String key, int val, Run r) {
        st.set(key, val);
        if (key.equals(LauncherSettings.BG_COLOR)) st.set(LauncherSettings.BG, 1);
        if (key.equals(LauncherSettings.DESKTOP)) {
            int want = val == 1 ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            act.setRequestedOrientation(want);
        }
        r.dirty = true;
    }

    private static String str(Object o) {
        if (o instanceof Double) {
            double v = (Double) o;
            if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
            return String.valueOf(v);
        }
        return String.valueOf(o);
    }

    private static double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(String.valueOf(o).trim().replace(',', '.')); }
        catch (NumberFormatException e) { return 0; }
    }

    private static Object numOrStr(String s) {
        if (s == null) return "";
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return s; }
    }

    private static boolean isNumeric(Object o) {
        if (o instanceof Number) return true;
        try { Double.parseDouble(String.valueOf(o).trim()); return true; } catch (NumberFormatException e) { return false; }
    }

    private static final Pattern VARP = Pattern.compile("\\{([^{}]+)\\}");

    private String interp(String s, Run r) {
        if (s == null || s.indexOf('{') < 0) return s;
        Matcher m = VARP.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            List<Tok> toks = ScriptLang.lex(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(str(eval(toks, r))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private int battery() {
        try {
            Intent i = act.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0), sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            return lv * 100 / Math.max(1, sc);
        } catch (Throwable e) { return 0; }
    }

    private boolean charging() {
        try {
            Intent i = act.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            return i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        } catch (Throwable e) { return false; }
    }

    private Object builtin(String low, Run r) {
        Calendar c = Calendar.getInstance();
        switch (low) {
            case "bateria": case "battery": return (double) battery();
            case "cargando": case "charging": return charging() ? 1d : 0d;
            case "hora": case "hour": return (double) c.get(Calendar.HOUR_OF_DAY);
            case "minuto": case "minute": return (double) c.get(Calendar.MINUTE);
            case "dia": case "day": case "weekday": { int w = c.get(Calendar.DAY_OF_WEEK); return (double) (w == Calendar.SUNDAY ? 7 : w - 1); }
            case "fecha": case "date": return (double) c.get(Calendar.DAY_OF_MONTH);
            case "mes": case "month": return (double) (c.get(Calendar.MONTH) + 1);
            case "ano": case "anio": case "year": return (double) c.get(Calendar.YEAR);
            case "cajon": case "drawer": return drawerName(en.drawerId());
            case "arg": case "argumento": case "evento": case "event": case "app": case "direccion": case "direction": return r.arg;
            case "paquete": case "package": return r.pkg;
            case "veces": case "times": return (double) r.n;
            case "aleatorio": case "random": return (double) rnd.nextInt(100);
            default: return null;
        }
    }

    private Object operand(List<Tok> g, Run r) {
        if (g.isEmpty()) return 0d;
        if (g.size() == 1) {
            Tok k = g.get(0);
            if (k.q) return interp(k.raw, r);
            if (k.isNum) return k.dval;
            if (k.color) return (double) k.cval;
            Object b = builtin(k.low, r);
            if (b != null) return b;
            if (vars.containsKey(k.low)) return numOrStr(vars.get(k.low));
        }
        Object[] mt = ScriptLang.matchPhrase(g, 0, ScriptLang.SETKEY);
        if (mt != null) {
            int used = 0;
            for (Tok k : g) if (!k.q && !ScriptLang.FILL.contains(k.low)) used++;
            int consumed = 0;
            for (int i = 0; i < (Integer) mt[1]; i++) if (!g.get(i).q && !ScriptLang.FILL.contains(g.get(i).low)) consumed++;
            if (consumed == used) return (double) st.get((String) mt[0]);
        }
        StringBuilder sb = new StringBuilder();
        for (Tok k : g) sb.append(sb.length() > 0 ? " " : "").append(k.q ? interp(k.raw, r) : k.raw);
        return sb.toString();
    }

    private static boolean isOp(Tok k) {
        return !k.q && !k.isNum && (k.raw.equals("+") || k.raw.equals("-") || k.raw.equals("*") || k.raw.equals("/") || k.raw.equals("%"));
    }

    private Object apply(Object a, String op, Object b) {
        if (op.equals("+") && (!isNumeric(a) || !isNumeric(b))) return str(a) + str(b);
        double x = num(a), y = num(b);
        switch (op) {
            case "+": return x + y;
            case "-": return x - y;
            case "*": return x * y;
            case "/": return y == 0 ? 0d : x / y;
            default: return y == 0 ? x : x % y;
        }
    }

    private Object eval(List<Tok> ts, Run r) {
        Object acc = null;
        String op = null;
        List<Tok> cur = new ArrayList<>();
        for (Tok k : ts) {
            if (isOp(k)) {
                Object v = operand(cur, r);
                acc = op == null ? v : apply(acc, op, v);
                op = k.raw;
                cur = new ArrayList<>();
            } else cur.add(k);
        }
        Object v = operand(cur, r);
        return op == null ? v : apply(acc, op, v);
    }

    private static String canonStr(String s) {
        String low = ScriptLang.norm(s.trim());
        String c = ScriptLang.SYN.get(low);
        return c != null && (c.equals("up") || c.equals("down") || c.equals("left") || c.equals("right")) ? c : low;
    }

    private int compare(Object a, Object b) {
        if (isNumeric(a) && isNumeric(b)) return Double.compare(num(a), num(b));
        String x = canonStr(str(a)), y = canonStr(str(b));
        return x.equals(y) ? 0 : x.compareTo(y);
    }

    private boolean cond(List<Tok> ts, Run r) {
        List<List<Tok>> segs = new ArrayList<>();
        List<String> joins = new ArrayList<>();
        List<Tok> cur = new ArrayList<>();
        for (Tok k : ts) {
            if (!k.q && ("and".equals(k.c) || "or".equals(k.c)) && !cur.isEmpty()) {
                segs.add(cur);
                joins.add(k.c);
                cur = new ArrayList<>();
            } else cur.add(k);
        }
        segs.add(cur);
        boolean res = single(segs.get(0), r);
        for (int i = 0; i < joins.size(); i++) {
            boolean v = single(segs.get(i + 1), r);
            res = joins.get(i).equals("and") ? (res && v) : (res || v);
        }
        return res;
    }

    private boolean single(List<Tok> s, Run r) {
        if (s.isEmpty()) return false;
        boolean neg = false;
        if (!s.get(0).q && "not".equals(s.get(0).c) && s.size() > 1 && !s.get(1).low.equals("es") && !s.get(1).low.equals("is")) {
            neg = true;
            s = ScriptLang.sub(s, 1, s.size());
        }
        return neg != base(s, r);
    }

    private boolean base(List<Tok> s, Run r) {
        boolean installed = false, panel = false, drawer = false, openW = false, closedW = false;
        for (Tok k : s) {
            if (k.q) continue;
            if ("installed".equals(k.c)) installed = true;
            if ("panel".equals(k.c)) panel = true;
            if ("drawer".equals(k.c)) drawer = true;
            if (k.low.equals("abierto") || k.low.equals("abierta") || k.low.equals("open") || k.low.equals("opened") || k.low.equals("visible")) openW = true;
            if (k.low.equals("cerrado") || k.low.equals("cerrada") || k.low.equals("closed")) closedW = true;
        }
        if (installed) {
            String nm = ScriptLang.firstQuoted(s);
            if (nm == null) nm = ScriptLang.bareAfter(s, "app");
            return findApp(nm) != null;
        }
        if (panel && (openW || closedW)) {
            String nm = ScriptLang.nameFor(s, "panel");
            boolean o = nm != null && panels.containsKey(ScriptLang.norm(nm));
            return closedW ? !o : o;
        }
        if (drawer && (openW || closedW)) return closedW ? en.drawerId() == null : en.drawerId() != null;
        int opIdx = -1;
        String sym = null;
        for (int i = 0; i < s.size(); i++) {
            Tok k = s.get(i);
            if (k.q) continue;
            String w = k.raw;
            if (w.equals("=") || w.equals("==") || w.equals("!=") || w.equals("<>") || w.equals(">") || w.equals(">=") || w.equals("<") || w.equals("<=")) { opIdx = i; sym = w; break; }
        }
        if (sym != null) {
            Object a = eval(ScriptLang.sub(s, 0, opIdx), r), b = eval(ScriptLang.sub(s, opIdx + 1, s.size()), r);
            int c = compare(a, b);
            switch (sym) {
                case "=": case "==": return c == 0;
                case "!=": case "<>": return c != 0;
                case ">": return c > 0;
                case ">=": return c >= 0;
                case "<": return c < 0;
                default: return c <= 0;
            }
        }
        int wi = -1;
        for (int i = 0; i < s.size(); i++) {
            Tok k = s.get(i);
            if (k.q) continue;
            String lw = k.low;
            if (lw.equals("es") || lw.equals("is") || lw.equals("igual") || lw.equals("equals") || lw.equals("equal") || lw.equals("mayor") || lw.equals("greater")
                || lw.equals("menor") || lw.equals("less") || lw.equals("distinto") || lw.equals("different") || lw.equals("contiene") || lw.equals("contains")
                || lw.equals("above") || lw.equals("below") || lw.equals("over") || lw.equals("under")) { wi = i; break; }
        }
        if (wi > 0) {
            boolean gt = false, lt = false, ne = false, ct = false, eq = false;
            int j = wi;
            while (j < s.size()) {
                Tok k = s.get(j);
                if (k.q) break;
                String lw = k.low;
                if (lw.equals("mayor") || lw.equals("greater") || lw.equals("above") || lw.equals("over")) gt = true;
                else if (lw.equals("menor") || lw.equals("less") || lw.equals("below") || lw.equals("under")) lt = true;
                else if (lw.equals("no") || lw.equals("not") || lw.equals("distinto") || lw.equals("different")) ne = true;
                else if (lw.equals("contiene") || lw.equals("contains")) ct = true;
                else if (lw.equals("igual") || lw.equals("equals") || lw.equals("equal") || lw.equals("es") || lw.equals("is")) eq = true;
                else if (lw.equals("que") || lw.equals("than") || lw.equals("a") || lw.equals("to") || lw.equals("de") || lw.equals("or") || lw.equals("o")) { }
                else break;
                j++;
            }
            if (j < s.size()) {
                Object a = eval(ScriptLang.sub(s, 0, wi), r), b = eval(ScriptLang.sub(s, j, s.size()), r);
                if (ct) return ScriptLang.norm(str(a)).contains(ScriptLang.norm(str(b))) != ne;
                int c = compare(a, b);
                if (gt) return eq ? c >= 0 : c > 0;
                if (lt) return eq ? c <= 0 : c < 0;
                return (c == 0) != ne;
            }
        }
        Object o = eval(s, r);
        if (o instanceof Number) return ((Number) o).doubleValue() != 0;
        String v = ScriptLang.norm(String.valueOf(o).trim());
        return !(v.isEmpty() || v.equals("false") || v.equals("no") || v.equals("0") || v.equals("off") || v.equals("nulo"));
    }

    private int dp(float v) { return (int) (v * d + 0.5f); }

    private GradientDrawable shape(int color, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * d);
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private void addToRoot(View v, ViewGroup.LayoutParams lp) {
        FrameLayout root = en.rootV();
        int idx = root.indexOfChild(en.radialV());
        if (idx < 0) root.addView(v, lp); else root.addView(v, idx, lp);
    }

    void openPanel(String name, String dirOv, String animOv, Long closeMs, Long durMs) {
        final String key = ScriptLang.norm(name);
        final ScriptLang.PanelDef pd = pg.panels.get(key);
        if (pd == null) { toast(T("No existe el panel: ", "Panel not found: ") + name); return; }
        if (panels.containsKey(key)) closePanel(key, false);
        final String dir = dirOv != null ? dirOv : pd.from.equals("auto") ? "down" : pd.from;
        final String anim = animOv != null ? animOv : pd.anim;
        final long dur = durMs != null ? durMs : pd.dur;
        int sw = act.getResources().getDisplayMetrics().widthPixels, sh = act.getResources().getDisplayMetrics().heightPixels;
        int pw, ph;
        boolean side = dir.equals("left") || dir.equals("right");
        if (dir.equals("center")) { pw = sw * (pd.w > 0 ? pd.w : 80) / 100; ph = sh * (pd.h > 0 ? pd.h : 50) / 100; }
        else if (side) { pw = sw * (pd.w > 0 ? pd.w : 72) / 100 - dp(12); ph = sh * (pd.h > 0 ? pd.h : 80) / 100; }
        else { pw = (sw - dp(24)) * (pd.w > 0 ? pd.w : 100) / 100; ph = sh * (pd.h > 0 ? pd.h : 45) / 100; }
        final FrameLayout wrap = new FrameLayout(act);
        final View scrim = new View(act);
        scrim.setBackgroundColor(Color.argb(90, 0, 0, 10));
        scrim.setOnClickListener(v -> { if (pd.outside) closePanel(key, true); });
        wrap.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        final LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(shape(pd.bg, pd.radius, Color.argb(40, 255, 255, 255)));
        box.setPadding(dp(14), dp(12), dp(14), dp(10));
        box.setClickable(true);
        if (!pd.title.isEmpty()) {
            TextView tt = new TextView(act);
            tt.setText(pd.title);
            tt.setTextSize(18);
            tt.setTextColor(pd.tcolor);
            tt.setPadding(dp(4), 0, 0, dp(8));
            box.addView(tt);
        }
        ScrollView sv = new ScrollView(act);
        GridLayout gl = new GridLayout(act);
        gl.setColumnCount(pd.cols);
        int cellW = Math.max(dp(40), (pw - dp(28)) / pd.cols);
        List<ScriptLang.PItem> items = new ArrayList<>();
        for (ScriptLang.PItem it : pd.items) {
            if (it.fromCategory != null) {
                CategoryManager.Category c = findCat(it.fromCategory);
                if (c == null) continue;
                for (ApplicationManager.App a : en.apps().inCategory(en.cats(), c.id)) {
                    ScriptLang.PItem x = new ScriptLang.PItem();
                    x.app = a.pkg;
                    items.add(x);
                }
            } else items.add(it);
        }
        for (ScriptLang.PItem it : items) gl.addView(panelCell(it, pd, key, cellW), cellParams(cellW));
        sv.addView(gl);
        box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pw, ph);
        lp.gravity = dir.equals("center") ? Gravity.CENTER
            : dir.equals("left") ? Gravity.START | Gravity.CENTER_VERTICAL
            : dir.equals("right") ? Gravity.END | Gravity.CENTER_VERTICAL
            : dir.equals("up") ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.setMargins(dp(12), en.insT() + dp(8), dp(12), en.insB() + dp(8));
        wrap.addView(box, lp);
        addToRoot(wrap, new FrameLayout.LayoutParams(-1, -1));
        panels.put(key, wrap);
        wrap.setTag(new Object[]{dir, anim, dur});
        box.setAlpha(0f);
        scrim.setAlpha(0f);
        box.post(() -> {
            float W = box.getWidth() + dp(24), H = box.getHeight() + dp(24);
            scrim.animate().alpha(1f).setDuration(dur).start();
            if (anim.equals("none")) { box.setAlpha(1f); scrim.setAlpha(1f); return; }
            if (anim.equals("fade")) { box.animate().alpha(1f).setDuration(dur).start(); return; }
            if (anim.equals("zoom") || dir.equals("center")) {
                box.setPivotX(dir.equals("left") ? 0 : dir.equals("right") ? box.getWidth() : box.getWidth() / 2f);
                box.setPivotY(dir.equals("up") ? 0 : dir.equals("down") ? box.getHeight() : box.getHeight() / 2f);
                box.setScaleX(0.6f);
                box.setScaleY(0.6f);
                box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(dur).setInterpolator(new DecelerateInterpolator(1.6f)).start();
                return;
            }
            box.setTranslationX(dir.equals("left") ? -W : dir.equals("right") ? W : 0);
            box.setTranslationY(dir.equals("up") ? -H - en.insT() : dir.equals("down") ? H + en.insB() : 0);
            box.setAlpha(1f);
            box.animate().translationX(0f).translationY(0f).setDuration(dur)
                .setInterpolator(anim.equals("bounce") ? new OvershootInterpolator(1.2f) : new DecelerateInterpolator(1.6f)).start();
        });
        if (closeMs != null) h.postDelayed(() -> { if (panels.get(key) == wrap) closePanel(key, true); }, closeMs);
        fire("panel_open", key, 0);
    }

    private GridLayout.LayoutParams cellParams(int w) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = w;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.setMargins(0, dp(4), 0, dp(4));
        return lp;
    }

    private View panelCell(final ScriptLang.PItem it, final ScriptLang.PanelDef pd, final String key, int cellW) {
        LinearLayout cell = new LinearLayout(act);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(2), dp(4), dp(2), dp(4));
        int sz = dp(pd.isize);
        String label = it.label;
        final ApplicationManager.App app = it.app == null ? null : findApp(it.app);
        if (app != null) {
            ImageView iv = new ImageView(act);
            iv.setImageDrawable(app.icon);
            cell.addView(iv, new LinearLayout.LayoutParams(sz, sz));
            if (label.isEmpty()) label = app.label;
        } else {
            TextView ic = new TextView(act);
            ic.setText(it.icon.isEmpty() ? "●" : it.icon);
            ic.setTextSize(pd.isize * 0.42f);
            ic.setTextColor(pd.tcolor);
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(shape(Color.argb(40, 255, 255, 255), pd.isize / 3f, 0));
            cell.addView(ic, new LinearLayout.LayoutParams(sz, sz));
        }
        TextView t = new TextView(act);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(pd.tcolor);
        t.setSingleLine();
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        cell.addView(t, new LinearLayout.LayoutParams(-1, -2));
        cell.setOnClickListener(v -> {
            if (pd.autoclose) closePanel(key, true);
            if (app != null) en.launchApp(app.pkg);
            else if (it.macro != null) runMacro(it.macro);
            else if (it.shell != null) runShell(it.shell, new ShellDone() { @Override public void done(String out) { } });
            else if (it.panel != null) openPanel(it.panel, null, null, null, null);
            else if (it.category != null) {
                CategoryManager.Category c = findCat(it.category);
                if (c != null) en.openD(c.id);
            }
        });
        return cell;
    }

    void closePanel(final String key, boolean animate) {
        final View wrap = panels.remove(key);
        if (wrap == null) return;
        Object tag = wrap.getTag();
        String dir = "down", anim = "slide";
        long dur = 200;
        if (tag instanceof Object[]) {
            Object[] t = (Object[]) tag;
            dir = (String) t[0];
            anim = (String) t[1];
            dur = Math.max(60, (Long) t[2] * 3 / 4);
        }
        final Runnable rm = () -> { en.rootV().removeView(wrap); fire("panel_close", key, 0); };
        if (!animate || anim.equals("none")) { rm.run(); return; }
        View box = ((FrameLayout) wrap).getChildAt(1);
        float W = box.getWidth() + dp(24), H = box.getHeight() + dp(24);
        ((FrameLayout) wrap).getChildAt(0).animate().alpha(0f).setDuration(dur).start();
        if (anim.equals("fade")) { box.animate().alpha(0f).setDuration(dur).withEndAction(rm).start(); return; }
        if (anim.equals("zoom") || dir.equals("center")) { box.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(dur).withEndAction(rm).start(); return; }
        box.animate().translationX(dir.equals("left") ? -W : dir.equals("right") ? W : 0).translationY(dir.equals("up") ? -H - en.insT() : dir.equals("down") ? H + en.insB() : 0)
            .setDuration(dur).setInterpolator(new DecelerateInterpolator()).withEndAction(rm).start();
    }

    private void rebuildButtons() {
        for (View v : btnViews.values()) en.rootV().removeView(v);
        btnViews.clear();
        lastInsT = en.insT();
        lastInsB = en.insB();
        for (final Map.Entry<String, ScriptLang.ButtonDef> e : pg.buttons.entrySet()) {
            final ScriptLang.ButtonDef bd = e.getValue();
            final String key = e.getKey();
            if (!bd.visible || btnHidden.contains(key)) continue;
            int sz = dp(bd.size);
            FrameLayout fl = new FrameLayout(act);
            float rad = bd.radius >= 0 ? bd.radius : bd.shape.equals("circle") ? bd.size / 2f : bd.shape.equals("round") ? bd.size / 4f : 0;
            fl.setBackground(shape(bd.bg, rad, Color.argb(50, 255, 255, 255)));
            fl.setAlpha(bd.opacity / 100f);
            fl.setElevation(dp(4));
            final ApplicationManager.App app = bd.app == null ? null : findApp(bd.app);
            if (app != null) {
                ImageView iv = new ImageView(act);
                iv.setImageDrawable(app.icon);
                fl.addView(iv, new FrameLayout.LayoutParams(sz * 3 / 4, sz * 3 / 4, Gravity.CENTER));
            } else {
                TextView t = new TextView(act);
                t.setText(bd.label.isEmpty() ? bd.icon : bd.icon + "\n" + bd.label);
                t.setTextSize(bd.label.isEmpty() ? bd.size * 0.42f : bd.size * 0.3f);
                t.setTextColor(bd.tcolor);
                t.setGravity(Gravity.CENTER);
                fl.addView(t, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            }
            fl.setOnClickListener(v -> {
                boolean any = hasRule("btn_tap", key);
                fire("btn_tap", key, 0);
                if (!any && app != null) en.launchApp(app.pkg);
            });
            fl.setOnLongClickListener(v -> {
                if (!hasRule("btn_hold", key)) return false;
                fire("btn_hold", key, 0);
                return true;
            });
            String pos = ScriptLang.norm(bd.pos);
            boolean l = pos.contains("izq") || pos.contains("left"), rt = pos.contains("der") || pos.contains("right");
            boolean tp = pos.contains("arriba") || pos.contains("top") || pos.contains("up") || pos.contains("superior");
            boolean bt = pos.contains("abajo") || pos.contains("bottom") || pos.contains("down") || pos.contains("inferior");
            int g = (l ? Gravity.START : rt ? Gravity.END : Gravity.CENTER_HORIZONTAL) | (tp ? Gravity.TOP : bt ? Gravity.BOTTOM : Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sz, sz, g);
            int mx = dp(8) + dp(bd.x), my = dp(8) + dp(bd.y);
            lp.leftMargin = l || !rt ? mx : 0;
            lp.rightMargin = rt ? mx : 0;
            lp.topMargin = tp ? en.insT() + my : (bt ? 0 : my);
            lp.bottomMargin = bt ? en.insB() + my : 0;
            addToRoot(fl, lp);
            btnViews.put(key, fl);
        }
    }

    private void rebuildEdges() {
        for (View v : edges.values()) en.rootV().removeView(v);
        edges.clear();
        Set<String> need = new LinkedHashSet<>();
        for (Rule r : pg.rules) if (r.ev.equals("swipe_edge") && r.arg != null) need.add(r.arg);
        for (final String edge : need) {
            View v = new View(act);
            final float[] s = new float[2];
            final boolean[] on = {false};
            v.setOnTouchListener((x, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: s[0] = ev.getRawX(); s[1] = ev.getRawY(); on[0] = true; break;
                    case MotionEvent.ACTION_MOVE: {
                        if (!on[0]) break;
                        float dx = ev.getRawX() - s[0], dy = ev.getRawY() - s[1], th = dp(48);
                        boolean ok = edge.equals("left") ? dx > th : edge.equals("right") ? -dx > th : edge.equals("top") ? dy > th : -dy > th;
                        if (ok) { on[0] = false; fire("swipe_edge", edge, 0); }
                        break;
                    }
                    default: on[0] = false;
                }
                return true;
            });
            int t = dp(14);
            FrameLayout.LayoutParams lp = edge.equals("left") ? new FrameLayout.LayoutParams(t, -1, Gravity.START)
                : edge.equals("right") ? new FrameLayout.LayoutParams(t, -1, Gravity.END)
                : edge.equals("top") ? new FrameLayout.LayoutParams(-1, t, Gravity.TOP) : new FrameLayout.LayoutParams(-1, t, Gravity.BOTTOM);
            addToRoot(v, lp);
            edges.put(edge, v);
        }
    }

    String statusLine() {
        return pg.rules.size() + " / " + pg.macros.size() + " / " + pg.panels.size() + " / " + pg.buttons.size();
    }
}
