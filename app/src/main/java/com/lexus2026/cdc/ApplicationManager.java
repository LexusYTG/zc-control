package com.lexus2026.cdc;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Consulta las apps con icono de lanzador (API pública, sin privilegios) y resuelve acciones sobre ellas. */
public class ApplicationManager {
    public static class App {
        public String pkg, label;
        public Drawable icon;
    }

    private final Context ctx;
    private final PackageManager pm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<App> apps = new ArrayList<>();
    private final LauncherSettings hs;

    public ApplicationManager(Context c) {
        ctx = c;
        pm = c.getPackageManager();
        hs = new LauncherSettings(c);
    }

    public boolean isHidden(String pkg) { return hs.isHidden(pkg); }

    public void refreshAsync(final Runnable done) {
        new Thread(() -> {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            final List<App> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!seen.add(pkg)) continue;
                App a = new App();
                a.pkg = pkg;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.icon = ri.loadIcon(pm);
                out.add(a);
            }
            Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
            main.post(() -> { apps = out; if (done != null) done.run(); });
        }).start();
    }

    public List<App> all() { return apps; }

    public App find(String pkg) {
        if (pkg == null) return null;
        for (App a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    /** Apps sin ninguna categoría: nunca se asignan solas. */
    public List<App> pending(CategoryManager cm) {
        List<App> l = new ArrayList<>();
        for (App a : apps) if (!cm.hasAny(a.pkg) && !hs.isHidden(a.pkg)) l.add(a);
        return l;
    }

    public List<App> inCategory(CategoryManager cm, String id) {
        List<App> l = new ArrayList<>();
        for (App a : apps) if (cm.has(a.pkg, id) && !hs.isHidden(a.pkg)) l.add(a);
        return l;
    }

    public boolean launch(String pkg) {
        // En modo escritorio todo se abre en ventana; fuera de él, según el ajuste de ventana flotante.
        return SideLauncher.launch(ctx, pkg, hs.get(LauncherSettings.DESKTOP) == 1);
    }

    public void info(String pkg) {
        try {
            ctx.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) { }
    }

    /** Usa el flujo normal del sistema: Android pide confirmación y decide si la app se puede desinstalar. */
    public void uninstall(String pkg) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) { }
    }
}
