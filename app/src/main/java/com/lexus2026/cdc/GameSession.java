package com.lexus2026.cdc;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GameSession implements Runnable {
    public static final int PENDING = 0, RUNNING = 1, DONE = 2, SKIPPED = 3, FAILED = 4;
    public static final int S_STATE = 0, S_READ = 1, S_KILL = 2, S_RES = 3, S_TWEAK = 4, S_COMPILE = 5, S_LAUNCH = 6;
    public static final String[] STEPS = {
        "Guardando estado original",
        "Lectura de procesos",
        "Limpieza de procesos",
        "Ajustando resolución y DPI",
        "Ajustes de actividades y batería",
        "Comprobando compilación",
        "Iniciando juego"
    };

    public interface Listener {
        void step(int i, int state, String detail);

        void progress(int pct, String label);

        void proc(String pkg, int state);

        void finished(boolean ok, String msg);
    }

    private final Context ctx;
    private final String pkg;
    private final GameSettings.Profile prof;
    private final Listener ls;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean cancel = false;

    public GameSession(Context c, String pkg, Listener l) {
        ctx = c.getApplicationContext();
        this.pkg = pkg;
        prof = GameSettings.profile(c, pkg);
        ls = l;
    }

    public GameSettings.Profile profile() {
        return prof;
    }

    public void cancel() {
        cancel = true;
    }

    private void step(final int i, final int s, final String d) {
        ui.post(() -> ls.step(i, s, d));
    }

    private void prog(final int p, final String t) {
        ui.post(() -> ls.progress(p, t));
    }

    private void proc(final String p, final int s) {
        ui.post(() -> ls.proc(p, s));
    }

    private void end(final boolean ok, final String m) {
        ui.post(() -> ls.finished(ok, m));
    }

    private boolean abort() {
        if (!cancel) return false;
        GameWatchdog.restore(ctx);
        end(false, "Preparación cancelada");
        return true;
    }

    @Override
    public void run() {
        try {
            work();
        } catch (Throwable e) {
            GameWatchdog.restore(ctx);
            end(false, "Error inesperado; se restauró el estado original.");
        }
    }

    private void work() {
        if (!Privileged.available()) {
            end(false, "Shizuku no está activo o no concedió permiso. ZC Game Control lo necesita para preparar el dispositivo.");
            return;
        }
        prog(2, "Guardando estado original…");
        step(S_STATE, RUNNING, "");
        if (!GameWatchdog.begin(ctx, pkg)) {
            step(S_STATE, FAILED, "No se pudo leer el estado");
            end(false, "No se pudo registrar el estado original; no se modificó nada.");
            return;
        }
        step(S_STATE, DONE, "");
        prog(8, "Estado guardado");
        if (abort()) return;
        if (prof.procs) {
            cleanup();
        } else {
            step(S_READ, SKIPPED, "Desactivado");
            step(S_KILL, SKIPPED, "Desactivado");
        }
        if (abort()) return;
        resolution();
        if (abort()) return;
        tweaks();
        if (abort()) return;
        compile();
        if (abort()) return;
        prog(100, "Listo");
        step(S_LAUNCH, RUNNING, "");
        end(true, "");
    }

    private void cleanup() {
        step(S_READ, RUNNING, "");
        prog(12, "Leyendo procesos…");
        List<String> cand = candidates();
        step(S_READ, DONE, cand.size() + " paquetes");
        for (String p : cand) proc(p, PENDING);
        step(S_KILL, RUNNING, "");
        int n = cand.size();
        int i = 0;
        int killed = 0;
        for (String p : cand) {
            if (cancel) return;
            prog(15 + 35 * i / Math.max(1, n), "Cerrando " + p);
            proc(p, RUNNING);
            int st = clean(p);
            if (st == DONE) killed++;
            proc(p, st);
            i++;
        }
        step(S_KILL, DONE, killed + " cerrados");
        prog(52, "Limpieza terminada");
    }

    private int clean(String p) {
        ShellExec.Res g = ShellExec.exec("am get-standby-bucket '" + p + "'", 6000);
        int ob = -1;
        try {
            ob = Integer.parseInt(g.out.trim().split("\\s+")[0]);
        } catch (Exception e) {
        }
        if (g.code != 0 || GameOps.bucketName(ob) == null) return SKIPPED;
        if (ob != 45) {
            GameWatchdog.noteBucket(ctx, p, ob);
            if (ShellExec.exec("am set-standby-bucket '" + p + "' restricted", 6000).code != 0) return FAILED;
        }
        return ShellExec.exec("am force-stop '" + p + "'", 8000).code == 0 ? DONE : FAILED;
    }

    private static void addComps(Set<String> s, String v) {
        if (v == null) return;
        for (String c : v.split(":")) {
            int i = c.indexOf('/');
            if (i > 0) s.add(c.substring(0, i));
        }
    }

    private Set<String> protectedSet(PackageManager pm) {
        Set<String> s = new HashSet<String>(GameSettings.exceptions(ctx));
        s.add(ctx.getPackageName());
        s.add(pkg);
        s.add("moe.shizuku.privileged.api");
        s.add("moe.shizuku.manager");
        s.add("android");
        s.add("com.android.systemui");
        ResolveInfo h = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        if (h != null && h.activityInfo != null) s.add(h.activityInfo.packageName);
        addComps(s, Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD));
        addComps(s, Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        addComps(s, Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners"));
        try {
            TelecomManager tm = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
            String d = tm.getDefaultDialerPackage();
            if (d != null) s.add(d);
        } catch (Throwable e) {
        }
        try {
            String sms = Telephony.Sms.getDefaultSmsPackage(ctx);
            if (sms != null) s.add(sms);
        } catch (Throwable e) {
        }
        try {
            for (ResolveInfo ri : pm.queryIntentServices(new Intent("android.net.VpnService"), 0)) {
                if (ri.serviceInfo != null) s.add(ri.serviceInfo.packageName);
            }
        } catch (Throwable e) {
        }
        return s;
    }

    private List<String> candidates() {
        PackageManager pm = ctx.getPackageManager();
        Set<String> skip = protectedSet(pm);
        ShellExec.Res r = ShellExec.exec("ps -A -o ARGS", 10000);
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (String line : r.out.split("\n")) {
            String t = line.trim();
            int sp = t.indexOf(' ');
            if (sp > 0) t = t.substring(0, sp);
            int col = t.indexOf(':');
            if (col > 0) t = t.substring(0, col);
            if (t.indexOf('.') > 0 && t.matches("[A-Za-z0-9_.]+")) names.add(t);
        }
        List<String> out = new ArrayList<String>();
        for (String n : names) {
            if (skip.contains(n)) continue;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(n, 0);
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || ai.uid < 10000) continue;
                out.add(n);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        return out;
    }

    private void resolution() {
        if (prof.pct >= 100) {
            step(S_RES, SKIPPED, "100 %");
            return;
        }
        step(S_RES, RUNNING, "");
        prog(58, "Ajustando resolución…");
        boolean ok = GameOps.resolution(ctx, prof.pct);
        GameWatchdog.Orig o = GameWatchdog.orig(ctx);
        if (ok && o != null) {
            int[] s = GameOps.size(o.w, o.h, prof.pct);
            step(S_RES, DONE, s[0] + "×" + s[1] + " · " + GameOps.dpi(o.dpi, prof.pct) + " dpi");
        } else {
            step(S_RES, FAILED, "No se pudo aplicar");
        }
        prog(70, "Resolución lista");
    }

    private void tweaks() {
        if (!prof.noKeep && !prof.bgLimit && !prof.saver) {
            step(S_TWEAK, SKIPPED, "Desactivado");
            return;
        }
        step(S_TWEAK, RUNNING, "");
        prog(72, "Aplicando ajustes temporales…");
        int bad = 0;
        int n = 0;
        if (prof.noKeep) {
            n++;
            if (!GameOps.alwaysFinish(ctx, true)) bad++;
        }
        if (prof.bgLimit) {
            n++;
            if (!GameOps.bgLimit(ctx, 0)) bad++;
        }
        if (prof.saver) {
            n++;
            if (!GameOps.saver(ctx, true)) bad++;
        }
        step(S_TWEAK, bad == 0 ? DONE : FAILED, bad == 0 ? n + " aplicados" : bad + " de " + n + " fallaron");
        prog(80, "Ajustes aplicados");
    }

    private void compile() {
        if (!prof.compile) {
            step(S_COMPILE, SKIPPED, "Desactivado");
            return;
        }
        step(S_COMPILE, RUNNING, "");
        prog(82, "Comprobando compilación…");
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, 0);
            long vc = GameOps.versionCode(pi);
            String vn = pi.versionName == null ? "" : pi.versionName;
            GameSettings.Rec r = GameSettings.rec(ctx, pkg);
            String why;
            if (r == null) why = "nuevo";
            else if (vc != r.code) why = "actualizado";
            else if (!r.ok) why = "reintento";
            else {
                step(S_COMPILE, SKIPPED, "Al día · v" + vn);
                prog(95, "Compilación al día");
                return;
            }
            if (r != null) GameSettings.dropRec(ctx, pkg);
            String mode = GameSettings.compileMode(ctx);
            prog(85, "Compilando (" + mode + ")…");
            ShellExec.Res c = ShellExec.exec("cmd package compile -m " + mode + " -f '" + pkg + "'", 300000);
            boolean ok = c.code == 0 && c.out.contains("Success");
            GameSettings.Rec n = new GameSettings.Rec();
            n.code = vc;
            n.name = vn;
            n.time = System.currentTimeMillis();
            n.ok = ok;
            n.mode = mode;
            GameSettings.saveRec(ctx, pkg, n);
            step(S_COMPILE, ok ? DONE : FAILED, ok ? why + " · " + mode : "Falló (" + why + ")");
        } catch (Exception e) {
            step(S_COMPILE, FAILED, "No se pudo leer la versión");
        }
        prog(95, "Compilación terminada");
    }
}
