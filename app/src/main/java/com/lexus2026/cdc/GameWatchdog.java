package com.lexus2026.cdc;
import android.content.Intent;
import android.os.SystemClock;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public final class GameWatchdog {
    private GameWatchdog() {}

    public static final class Orig {
        public int w;
        public int h;
        public int dpi;
    }

    private static final Object LOCK = new Object();
    private static final long LEAVE_MS = 6000L;
    private static final long LAUNCH_MS = 45000L;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static volatile boolean running = false;
    private static volatile boolean launched = false;
    private static volatile String game = null;
    private static volatile boolean panelOn = false;
    private static GamePanel panel = null;
    private static Context appCtx = null;
    private static boolean listening = false;
    private static final Runnable hidePanel = () -> {
        if (panel != null) panel.hide();
    };
    /** Cierra la sesión solo si el juego ya no está realmente en primer plano. */
    private static final Runnable leave = () -> new Thread(() -> {
        String g = game;
        if (appCtx == null) return;
        if (g != null && g.equals(GameMeter.topPackage())) return;
        restore(appCtx);
    }).start();
    /**
     * Otro paquete emitió un evento de ventana (captura de pantalla, notificación, etc.). Antes de ocultar nada
     * se comprueba cuál es la actividad realmente en primer plano: si sigue siendo el juego, no se toca nada.
     */
    private static final Runnable awayCheck = () -> new Thread(() -> {
        final String g = game;
        if (!running || g == null) return;
        if (g.equals(GameMeter.topPackage())) {
            H.post(() -> {
                H.removeCallbacks(hidePanel);
                H.removeCallbacks(leave);
                if (panelOn) {
                    ControlService svc = ControlService.instance;
                    if (svc != null) {
                        if (panel == null) panel = new GamePanel(svc);
                        panel.show(g);
                    }
                }
            });
            return;
        }
        H.post(() -> {
            H.removeCallbacks(hidePanel);
            H.post(hidePanel);
            H.removeCallbacks(leave);
            H.postDelayed(leave, LEAVE_MS);
        });
    }).start();
    private static final Shizuku.OnBinderReceivedListener received = () -> recoverAsync(appCtx);

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("gcw", Context.MODE_PRIVATE);
    }

    private static volatile long prepUntil = 0L;
    private static volatile String bypassPkg = null;
    private static volatile long bypassUntil = 0L;

    public static void bypass(String pkg) {
        bypassPkg = pkg;
        bypassUntil = SystemClock.uptimeMillis() + 20000L;
    }

    public static void prepActive(boolean on) {
        prepUntil = on ? SystemClock.uptimeMillis() + 600000L : 0L;
    }

    private static void intercept(Context c, String pkg) {
        long now = SystemClock.uptimeMillis();
        if (now < prepUntil) return;
        if (pkg.equals(bypassPkg) && now < bypassUntil) return;
        if (ignorable(c, pkg)) return;
        if (!GameSettings.enabled(c) || !GameSettings.isGame(c, pkg)) return;
        prepUntil = now + 15000L;
        if (Privileged.available()) {
            Privileged.run("am start -n " + c.getPackageName() + "/.GamePrepActivity --es pkg " + pkg);
        } else {
            try {
                c.startActivity(new Intent(c, GamePrepActivity.class).putExtra("pkg", pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean route(Context c, String pkg) {
        long now = SystemClock.uptimeMillis();
        if (pkg == null || ignorable(c, pkg)) return false;
        if (pkg.equals(bypassPkg) && now < bypassUntil) return false;
        if (!GameSettings.enabled(c) || !GameSettings.isGame(c, pkg)) return false;
        prepUntil = now + 15000L;
        try {
            c.startActivity(new Intent(c, GamePrepActivity.class).putExtra("pkg", pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            prepUntil = 0L;
            return false;
        }
    }

    public static void configChanged() {
        if (panel != null) panel.refreshLayout();
    }

    public static boolean running() {
        return running;
    }

    public static void attach(Context c) {
        appCtx = c.getApplicationContext();
        if (!listening) {
            try {
                Shizuku.addBinderReceivedListener(received);
                listening = true;
            } catch (Throwable e) {
            }
        }
        recoverAsync(appCtx);
    }

    public static void detach() {
        try {
            Shizuku.removeBinderReceivedListener(received);
        } catch (Throwable e) {
        }
        listening = false;
        H.removeCallbacks(leave);
        H.removeCallbacks(awayCheck);
        if (panel != null) {
            panel.destroy();
            panel = null;
        }
    }

    public static void recoverAsync(final Context c) {
        if (c == null) return;
        new Thread(() -> recover(c)).start();
    }

    public static boolean recover(Context c) {
        synchronized (LOCK) {
            if (running) return true;
            return restore(c);
        }
    }

    public static void endAsync(final Context c) {
        if (c == null) return;
        new Thread(() -> restore(c)).start();
    }

    private static int[] dims(String t, String key) {
        for (String l : t.split("\n")) {
            if (!l.contains(key)) continue;
            Matcher m = Pattern.compile("(\\d+)x(\\d+)").matcher(l);
            if (m.find()) return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        return null;
    }

    private static int num(String t, String key) {
        for (String l : t.split("\n")) {
            if (!l.contains(key)) continue;
            String d = l.replaceAll("\\D+", "");
            if (!d.isEmpty()) return Integer.parseInt(d);
        }
        return -1;
    }

    public static boolean begin(Context c, String pkg) {
        synchronized (LOCK) {
            if (!restore(c)) return false;
            ShellExec.Res r = ShellExec.exec("wm size; echo '#'; wm density; echo '#'; settings get global always_finish_activities; echo '#'; settings get global low_power; echo '#'; settings get system screen_brightness; echo '#'; settings get system screen_brightness_mode", 10000);
            if (r.code != 0) return false;
            String[] p = r.out.split("#\n", -1);
            if (p.length < 6) return false;
            int[] phys = dims(p[0], "Physical size");
            int[] ov = dims(p[0], "Override size");
            int pd = num(p[1], "Physical density");
            int od = num(p[1], "Override density");
            int[] eff = ov != null ? ov : phys;
            int dpi = od > 0 ? od : pd;
            if (eff == null || dpi <= 0) return false;
            SharedPreferences.Editor e = sp(c).edit().clear();
            e.putBoolean("active", true);
            e.putString("pkg", pkg);
            e.putInt("w", eff[0]);
            e.putInt("h", eff[1]);
            e.putInt("dpi", dpi);
            e.putBoolean("sizeOv", ov != null);
            e.putBoolean("dpiOv", od > 0);
            e.putString("always", p[2].trim());
            e.putString("low", p[3].trim());
            e.putString("bright", p[4].trim());
            e.putString("bmode", p[5].trim());
            e.putInt("pct", 100);
            e.putLong("t", System.currentTimeMillis());
            if (!e.commit()) return false;
            running = true;
            launched = false;
            game = pkg;
            return true;
        }
    }

    public static void mark(Context c, String key) {
        sp(c).edit().putBoolean(key, true).commit();
    }

    public static void noteBucket(Context c, String pkg, int orig) {
        synchronized (LOCK) {
            SharedPreferences s = sp(c);
            String b = s.getString("bk", "");
            s.edit().putString("bk", (b.isEmpty() ? "" : b + ",") + pkg + ":" + orig).commit();
        }
    }

    public static Orig orig(Context c) {
        SharedPreferences s = sp(c);
        if (!s.getBoolean("active", false)) return null;
        Orig o = new Orig();
        o.w = s.getInt("w", 0);
        o.h = s.getInt("h", 0);
        o.dpi = s.getInt("dpi", 0);
        return o.w > 0 && o.h > 0 && o.dpi > 0 ? o : null;
    }

    public static int pct(Context c) {
        return sp(c).getInt("pct", 100);
    }

    public static void setPct(Context c, int p) {
        sp(c).edit().putInt("pct", p).commit();
    }

    private static String val(String v) {
        return v == null || v.isEmpty() || v.equals("null") ? "0" : v;
    }

    private static void finish() {
        running = false;
        launched = false;
        game = null;
        H.removeCallbacks(leave);
        H.post(hidePanel);
    }

    public static boolean restore(Context c) {
        synchronized (LOCK) {
            SharedPreferences s = sp(c);
            if (!s.getBoolean("active", false)) {
                finish();
                return true;
            }
            running = false;
            launched = false;
            H.removeCallbacks(leave);
            H.post(hidePanel);
            if (!Privileged.available()) return false;
            boolean ok = true;
            if (s.getBoolean("chRes", false)) {
                String sz = s.getBoolean("sizeOv", false) ? "wm size " + s.getInt("w", 0) + "x" + s.getInt("h", 0) : "wm size reset";
                String dn = s.getBoolean("dpiOv", false) ? "wm density " + s.getInt("dpi", 0) : "wm density reset";
                ok &= ShellExec.exec(sz, 10000).code == 0;
                ok &= ShellExec.exec(dn, 10000).code == 0;
            }
            if (s.getBoolean("chAct", false)) {
                ok &= ShellExec.exec("settings put global always_finish_activities " + val(s.getString("always", "0")), 6000).code == 0;
            }
            if (s.getBoolean("chSaver", false)) {
                ok &= ShellExec.exec("settings put global low_power " + val(s.getString("low", "0")), 6000).code == 0;
            }
            if (s.getBoolean("chBright", false)) {
                String b = s.getString("bright", "null");
                String bm = s.getString("bmode", "null");
                if (b.matches("\\d+")) ok &= ShellExec.exec("settings put system screen_brightness " + b, 6000).code == 0;
                if (bm.matches("\\d+")) ok &= ShellExec.exec("settings put system screen_brightness_mode " + bm, 6000).code == 0;
            }
            if (s.getBoolean("chLimit", false)) {
                ok &= GameOps.limit(c, -1);
            }
            String bk = s.getString("bk", "");
            if (!bk.isEmpty()) {
                for (String e : bk.split(",")) {
                    int i = e.lastIndexOf(':');
                    if (i <= 0) continue;
                    try {
                        String name = GameOps.bucketName(Integer.parseInt(e.substring(i + 1)));
                        if (name != null) ShellExec.exec("am set-standby-bucket '" + e.substring(0, i) + "' " + name, 6000);
                    } catch (Exception x) {
                    }
                }
            }
            if (!ok) return false;
            s.edit().clear().commit();
            finish();
            return true;
        }
    }

    public static void markLaunched(Context c, String pkg, boolean withPanel) {
        appCtx = c.getApplicationContext();
        game = pkg;
        launched = true;
        panelOn = withPanel;
        H.removeCallbacks(leave);
        H.postDelayed(leave, LAUNCH_MS);
    }

    private static boolean ignorable(Context c, String pkg) {
        if (pkg.equals(c.getPackageName()) || pkg.equals("com.android.systemui") || pkg.equals("android")) return true;
        String low = pkg.toLowerCase();
        if (low.contains("screenshot") || low.contains("screencapture") || low.contains("smartcapture") || low.contains("screenrecord")) return true;
        String ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return ime != null && ime.startsWith(pkg + "/");
    }

    public static void onForeground(Context c, String pkg, String cls) {
        if (!running) {
            if (panel != null) H.post(hidePanel);
            intercept(c, pkg);
            return;
        }
        if (!launched || game == null) return;
        if (pkg.equals(game)) {
            H.removeCallbacks(awayCheck);
            H.removeCallbacks(leave);
            H.removeCallbacks(hidePanel);
            if (panelOn) {
                ControlService svc = ControlService.instance;
                if (svc != null) {
                    if (panel == null) panel = new GamePanel(svc);
                    panel.show(game);
                }
            }
            return;
        }
        if (pkg.equals(c.getPackageName())) {
            boolean act = cls != null && cls.endsWith("Activity") && !cls.endsWith("GamePrepActivity");
            if (!act) return;
        } else if (ignorable(c, pkg)) return;
        H.removeCallbacks(awayCheck);
        H.postDelayed(awayCheck, 700L);
    }
}
