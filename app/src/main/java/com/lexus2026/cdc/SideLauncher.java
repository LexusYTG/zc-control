package com.lexus2026.cdc;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lista las apps instaladas y las abre, en ventana flotante cuando el sistema lo permite. */
public final class SideLauncher {
    private SideLauncher() {}

    public static final class App {
        public final String pkg;
        public final String label;
        public final Drawable icon;

        App(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    /** Apps con ícono de lanzador, ordenadas por nombre. Puede tardar: llamarla fuera del hilo principal. */
    public static List<App> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        List<App> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
            String label;
            try { label = ri.loadLabel(pm).toString(); } catch (Exception e) { label = pkg; }
            Drawable ic = null;
            try { ic = ri.loadIcon(pm); } catch (Exception e) { ic = null; }
            out.add(new App(pkg, label, ic));
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    public static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }

    public static Drawable icon(Context c, String pkg) {
        try { return c.getPackageManager().getApplicationIcon(pkg); } catch (Exception e) { return null; }
    }

    public static boolean installedPkg(Context c, String pkg) {
        return c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    /**
     * Abre la app. Con "ventana flotante" activa la lanza en modo freeform con las opciones de actividad
     * (modo + límites). Si el sistema o la app no lo soportan, Android la abre en pantalla completa.
     */
    /** Lanzamiento de la interfaz lateral: respeta el ajuste «ventana flotante» de esa interfaz. */
    public static boolean launch(Context c, String pkg) { return launch(c, pkg, false, true); }

    /** Lanzamiento del launcher: en ventana SOLO si se pide explícitamente (modo escritorio); el ajuste lateral no cuenta. */
    public static boolean launch(Context c, String pkg, boolean forceFloat) { return launch(c, pkg, forceFloat, false); }

    private static boolean launch(Context c, String pkg, boolean forceFloat, boolean useSideSetting) {
        if (GameWatchdog.route(c, pkg)) return true;
        Intent li = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (li == null) return false;
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean floating = forceFloat || (useSideSetting && SideConfig.get(c, "floatWin") != 0);
        if (!floating) {
            try { c.startActivity(li); return true; } catch (Throwable e) { return false; }
        }
        final int mode = SideConfig.get(c, "winMode");
        final String comp = li.getComponent() != null ? li.getComponent().flattenToShortString() : null;

        // Opciones de actividad desde la app (único método). El tamaño va DENTRO del lanzamiento; nunca se redimensiona después.
        try {
            DisplayMetrics d = c.getResources().getDisplayMetrics();
            int w = d.widthPixels * SideConfig.get(c, "winSize") / 100;
            int h = d.heightPixels * SideConfig.get(c, "winH") / 100;
            int left = (d.widthPixels - w) * SideConfig.get(c, "winX") / 100;
            int top = (d.heightPixels - h) * SideConfig.get(c, "winY") / 100;
            final Rect fr = SideConfig.get(c, "winBounds") != 0 ? new Rect(left, top, left + w, top + h) : null;

            if (SideConfig.get(c, "winSettings") != 0) ensureFreeform(c);
            if (SideConfig.get(c, "winExempt") != 0) exemptHiddenApis();

            final Intent fli = li;
            final Context fc = c;
            final boolean hack = SideConfig.get(c, "winHack") != 0;
            if (hack) try {
                Intent hi = new Intent(c, InvisibleFreeformActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                c.startActivity(hi, freeformOptions(new Rect(0, 0, 1, 1), mode));
            } catch (Throwable ignored) { /* si falla el auxiliar, se intenta igual */ }

            Runnable go = () -> {
                try {
                    fc.startActivity(fli, freeformOptions(fr, mode));
                } catch (Throwable t) {
                    try {
                        if (comp != null && Privileged.available())
                            Privileged.runFast("am start --windowingMode " + mode + " -n " + comp, null);
                        else fc.startActivity(fli);
                    } catch (Throwable ignored) { }
                }
            };
            if (hack) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(go, SideConfig.get(c, "winHackMs"));
            else go.run();
            return true;
        } catch (Throwable e) {
            try { c.startActivity(li); return true; } catch (Throwable e2) { return false; }
        }
    }

    /** Clave del modo de ventana dentro del Bundle de ActivityOptions (valor estable del framework). */
    private static final String KEY_WINDOWING_MODE = "android.activity.windowingMode";

    /**
     * Opciones de lanzamiento en ventana. El modo se escribe DIRECTO en el Bundle: no depende de la reflexión
     * sobre APIs ocultas, que en Android moderno falla en silencio y dejaba la app a pantalla completa.
     */
    private static android.os.Bundle freeformOptions(Rect bounds, int mode) {
        ActivityOptions o = ActivityOptions.makeBasic();
        if (bounds != null) o.setLaunchBounds(bounds);
        setWindowingMode(o, mode);                       // por si el sistema lo lee solo de ahí
        android.os.Bundle b = o.toBundle();
        if (b == null) b = new android.os.Bundle();
        b.putInt(KEY_WINDOWING_MODE, mode);
        return b;
    }


    private static boolean exempted;

    /** Permite la reflexión sobre APIs ocultas (VMRuntime.setHiddenApiExemptions("L")), como hace Taskbar. */
    private static void exemptHiddenApis() {
        if (exempted) return;
        exempted = true;
        try {
            java.lang.reflect.Method forName = Class.class.getDeclaredMethod("forName", String.class);
            java.lang.reflect.Method gdm = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Class<?> vm = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            java.lang.reflect.Method getRuntime = (java.lang.reflect.Method) gdm.invoke(vm, "getRuntime", null);
            java.lang.reflect.Method set = (java.lang.reflect.Method) gdm.invoke(vm, "setHiddenApiExemptions", new Class[]{String[].class});
            Object rt = getRuntime.invoke(null);
            set.invoke(rt, new Object[]{new String[]{"L"}});
        } catch (Throwable ignored) { /* en algunas versiones no funciona */ }
    }

    /** setLaunchWindowingMode es API oculta: se llama con la doble reflexión que la salta en Android 9+. */
    private static void setWindowingMode(ActivityOptions o, int mode) {
        try {
            java.lang.reflect.Method gm = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            java.lang.reflect.Method m = (java.lang.reflect.Method) gm.invoke(ActivityOptions.class, "setLaunchWindowingMode", new Class[]{int.class});
            m.setAccessible(true);
            m.invoke(o, mode);
        } catch (Throwable ignored) { /* sin ella basta con los límites */ }
    }

    /** Activa las opciones de ventana libre: por API si tenemos WRITE_SECURE_SETTINGS, si no por Shizuku. */
    public static void ensureFreeform(Context c) {
        String[] keys = {"enable_freeform_support", "force_resizable_activities", "enable_non_resizable_multi_window", "freeform_window_management"};
        try {
            if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED) {
                for (String k : keys) {
                    if (android.provider.Settings.Global.getInt(c.getContentResolver(), k, 0) != 1)
                        android.provider.Settings.Global.putInt(c.getContentResolver(), k, 1);
                }
                return;
            }
        } catch (Throwable ignored) {}
        if (Privileged.available()) prepareFreeform();
    }

    /** Activa por Shizuku lo que Android necesita para permitir ventanas flotantes (algunos equipos piden reiniciar). */
    public static void prepareFreeform() {
        Privileged.run(String.join("; ",
            "settings put global enable_freeform_support 1",
            "settings put global force_resizable_activities 1",
            "settings put global enable_non_resizable_multi_window 1",
            "settings put global freeform_window_management 1"));
    }
}
