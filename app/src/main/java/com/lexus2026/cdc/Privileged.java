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
        String cmd = String.join("; ",
                "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS",
                "appops set " + pkg + " ACCESS_RESTRICTED_SETTINGS allow",
                "appops set " + pkg + " WRITE_SETTINGS allow",
                "cmd notification allow_dnd " + pkg,
                "cmd notification allow_listener " + pkg + "/com.lexus2026.cdc.MediaListener",
                "dumpsys deviceidle whitelist +" + pkg,
                "settings put secure enabled_accessibility_services '" + list + "'",
                "settings put secure accessibility_enabled 1"
        );
        run(cmd, done);
    }
}
