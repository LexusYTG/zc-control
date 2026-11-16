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
     * Abre la app. Con "ventana flotante" activa intenta el modo freeform: primero por shell (Shizuku), que es
     * lo más fiable, y si no, con las opciones de actividad. Si el sistema o la app no lo soportan, Android la
     * abre en pantalla completa y no se pierde nada.
     */
    public static boolean launch(Context c, String pkg) {
        if (GameWatchdog.route(c, pkg)) return true;
        Intent li = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (li == null) return false;
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean floating = SideConfig.get(c, "floatWin") != 0;
        try {
            Rect r = null;
            if (floating) {
                DisplayMetrics d = c.getResources().getDisplayMetrics();
                int w = d.widthPixels * SideConfig.get(c, "winSize") / 100;
                int h = (int) (w * 1.25f);
                if (h > d.heightPixels * 85 / 100) h = d.heightPixels * 85 / 100;
                int left = (d.widthPixels - w) / 2;
                int top = Math.max(0, (d.heightPixels - h) / 3);
                r = new Rect(left, top, left + w, top + h);
            }
            if (floating && Privileged.available() && li.getComponent() != null) {
                ComponentName cn = li.getComponent();
                String pk = cn.getPackageName();
                // Lanza en freeform y, ya creada la tarea, le fija el tamaño (am start solo no acepta límites).
                String find = "(am stack list 2>/dev/null || cmd activity stack list 2>/dev/null)"
                    + " | grep \"taskId=[0-9]*: " + pk + "/\" | head -1 | sed -E 's/.*taskId=([0-9]+).*/\\1/'";
                String resize = "am task resize $T " + r.left + " " + r.top + " " + r.right + " " + r.bottom;
                Privileged.run("am start --windowingMode 5 -n " + cn.flattenToShortString()
                    + "; for i in 1 2 3 4 5 6; do sleep 0.25; T=$(" + find + "); "
                    + "[ -n \"$T\" ] && { " + resize + "; break; }; done");
                return true;
            }
            if (floating) {
                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setLaunchBounds(r);
                try {
                    ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class).invoke(opts, 5);
                } catch (Throwable ignored) { /* API oculta: sin ella solo se aplican los límites */ }
                c.startActivity(li, opts.toBundle());
                return true;
            }
            c.startActivity(li);
            return true;
        } catch (Throwable e) {
            try { c.startActivity(li); return true; } catch (Throwable e2) { return false; }
        }
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
