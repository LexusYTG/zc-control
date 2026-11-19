package com.lexus2026.cdc;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/** Ejecuta comandos de shell con los permisos de Shizuku (sin root). */
public final class Privileged {
    private Privileged() {}

    public static boolean available() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }


    // ---- shell persistente: evita crear un proceso por Shizuku en cada toque (era lo que tardaba ~2 s) ----
    private static Process shell;
    private static java.io.OutputStream shIn;
    private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.ConcurrentHashMap<Long, Runnable> WAITING = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String MARK = "__ZC_DONE_";

    private static boolean alive() {
        if (shell == null) return false;
        try { shell.exitValue(); return false; } catch (IllegalThreadStateException e) { return true; } catch (Throwable t) { return false; }
    }

    private static synchronized boolean ensureShell() {
        if (alive() && shIn != null) return true;
        try {
            Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            final Process p = (Process) m.invoke(null, new String[]{"sh"}, null, null);
            shell = p;
            shIn = p.getOutputStream();
            shIn.write("exec 2>&1\n".getBytes());
            shIn.flush();
            Thread t = new Thread(() -> {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                    String l;
                    while ((l = br.readLine()) != null) {
                        if (l.startsWith(MARK)) {
                            try {
                                Runnable r = WAITING.remove(Long.parseLong(l.substring(MARK.length()).trim()));
                                if (r != null) new Handler(Looper.getMainLooper()).post(r);
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
                synchronized (Privileged.class) { if (shell == p) { shell = null; shIn = null; } }
                // si la shell murió, avisa a los que esperaban para que no se queden colgados
                for (Long k : WAITING.keySet()) { Runnable r = WAITING.remove(k); if (r != null) new Handler(Looper.getMainLooper()).post(r); }
            }, "zc-shell-reader");
            t.setDaemon(true);
            t.start();
            return true;
        } catch (Throwable e) {
            shell = null; shIn = null;
            return false;
        }
    }

    /** Igual que run(), pero reutiliza una shell abierta: casi instantáneo. done se ejecuta al terminar el comando. */
    public static void runFast(final String cmd, final Runnable done) {
        new Thread(() -> {
            boolean ok = false;
            synchronized (Privileged.class) {
                if (ensureShell()) {
                    try {
                        long id = SEQ.incrementAndGet();
                        if (done != null) WAITING.put(id, done);
                        shIn.write((cmd + "\necho " + MARK + id + "\n").getBytes());
                        shIn.flush();
                        ok = true;
                    } catch (Throwable e) { shell = null; shIn = null; }
                }
            }
            if (!ok) run(cmd, done);
        }, "zc-shell-writer").start();
    }

    public static void run(String cmd) {
        run(cmd, null);
    }

    public static void run(final String cmd, final Runnable done) {
        new Thread(() -> {
            try {
                Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                m.setAccessible(true);
                Process p = (Process) m.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
                p.waitFor();
            } catch (Throwable e) { /* sin permiso o Shizuku detenido */ }
            if (done != null) new Handler(Looper.getMainLooper()).post(done);
        }).start();
    }

    public static void grantAll(Context ctx) {
        grantAll(ctx, null);
    }

    /** Concede por shell todos los permisos que la app necesitaba pedir a mano. */
    public static void grantAll(Context ctx, Runnable done) {
        String pkg = ctx.getPackageName();
        String svc = pkg + "/com.lexus2026.cdc.ControlService";
        String cur = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (cur == null) cur = "";
        String list = cur.contains(svc) ? cur : cur.isEmpty() ? svc : cur + ":" + svc;
        // Si el servicio no está conectado, escribir el mismo valor no hace nada: hay que apagarlo y encenderlo de verdad.
        String acc = ControlService.instance == null ? reviveCmd(ctx)
                : "settings put secure enabled_accessibility_services '" + list + "'; settings put secure accessibility_enabled 1";
        String cmd = String.join("; ",
                "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS",
                "appops set " + pkg + Lang.t(504),
                "appops set " + pkg + Lang.t(505),
                "cmd notification allow_dnd " + pkg,
                "cmd notification allow_listener " + pkg + "/com.lexus2026.cdc.MediaListener",
                "dumpsys deviceidle whitelist +" + pkg,
                acc
        );
        run(cmd, done);
    }

    /** Comandos que quitan y vuelven a dar el permiso de accesibilidad y el de notificaciones (fuerza al sistema a reconectar). */
    private static String reviveCmd(Context ctx) {
        String pkg = ctx.getPackageName();
        android.content.ComponentName svcName = new android.content.ComponentName(pkg, "com.lexus2026.cdc.ControlService");
        String svc = svcName.flattenToString();
        String lis = pkg + "/com.lexus2026.cdc.MediaListener";
        String cur = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        StringBuilder without = new StringBuilder();
        if (cur != null) {
            for (String e : cur.split(":")) {
                if (e.isEmpty() || svcName.equals(android.content.ComponentName.unflattenFromString(e))) continue;
                if (without.length() > 0) without.append(':');
                without.append(e);
            }
        }
        String with = without.length() == 0 ? svc : without + ":" + svc;
        return String.join("; ",
                "settings put secure enabled_accessibility_services '" + without + "'",
                "sleep 1",
                "settings put secure enabled_accessibility_services '" + with + "'",
                "settings put secure accessibility_enabled 1",
                "cmd notification disallow_listener " + lis,
                "cmd notification allow_listener " + lis);
    }

    /**
     * Vigilante que vive fuera de la app (en el shell de Shizuku): si el proceso de la app muere, por ejemplo al
     * "cerrar todo" en recientes, quita y vuelve a dar el permiso de accesibilidad. Eso hace que el sistema relance el
     * servicio, y con él el proceso, sin tener que abrir la app a mano. Solo se inicia una vez (archivo con el pid).
     */
    public static void startWatcher(Context ctx) {
        String pkg = ctx.getPackageName();
        String svc = new android.content.ComponentName(pkg, "com.lexus2026.cdc.ControlService").flattenToString();
        String loop = "echo $$ > /data/local/tmp/zcw.pid; while true; do "
                + "if [ -z \"$(pidof " + pkg + ")\" ]; then "
                + "cur=$(settings get secure enabled_accessibility_services); [ \"$cur\" = null ] && cur=; "
                + "wo=$(echo \"$cur\" | sed -e \"s#" + pkg + "/[^:]*ControlService##g\" -e \"s#::*#:#g\" -e \"s#^:##\" -e \"s#:\\$##\"); "
                + "settings put secure enabled_accessibility_services \"$wo\"; sleep 1; "
                + "if [ -n \"$wo\" ]; then n=\"$wo:" + svc + "\"; else n=\"" + svc + "\"; fi; "
                + "settings put secure enabled_accessibility_services \"$n\"; settings put secure accessibility_enabled 1; sleep 8; "
                + "fi; sleep 2; done";
        String cmd = "p=$(cat /data/local/tmp/zcw.pid 2>/dev/null); "
                + "if [ -n \"$p\" ] && [ -d /proc/$p ]; then exit 0; fi; "
                + "setsid nohup sh -c '" + loop + "' >/dev/null 2>&1 &";
        run(cmd, null);
    }

    /** Reconecta los servicios cuando el proceso murió y el sistema no los volvió a lanzar. */
    public static void revive(Context ctx, Runnable done) {
        run(reviveCmd(ctx), done);
    }
}
