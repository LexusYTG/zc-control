package com.lexus2026.cdc;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Preferencias del launcher. Todo vive en SharedPreferences: sobrevive a reinicios y a actualizaciones. */
public class LauncherSettings {
    public static final String RING1 = "ring1", RING2 = "ring2", ARC = "arc", BUBBLE = "bubble", GAP = "gap",
        STAGE2 = "stage2", SENS = "sens", HOLD = "hold", ANIM = "anim", SHAPE = "shape",
        DOCK_ICON = "dockIcon", APP_ICON = "appIcon", BG = "bg", DIM = "dim", BG_COLOR = "bgColor", ACCENT = "accent", NEW_MODE = "newMode", DOCK_H = "dockH", DOCK_W = "dockW",
        R1 = "r1", R2GAP = "r2gap", TRACK = "track", ROT = "rot", FIT = "fit",
        RAD_X = "radX", RAD_Y = "radY", DOCK_POS = "dockPos", DOCK_X = "dockX", DOCK_Y = "dockY", DESKTOP = "desktop";

    private final SharedPreferences sp;

    public LauncherSettings(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("zc_launcher", Context.MODE_PRIVATE);
    }

    public static int def(String k) {
        switch (k) {
            case RING1: return 5;        // posiciones del primer radial
            case RING2: return 7;        // posiciones del segundo radial
            case ARC: return 125;        // apertura angular (grados)
            case BUBBLE: return 46;      // tamaño de burbuja (dp)
            case GAP: return 8;          // separación entre burbujas y entre arcos (dp)
            case STAGE2: return 34;      // dp más allá del 1er arco para abrir el 2º
            case SENS: return 100;       // % de sensibilidad del desplazamiento
            case HOLD: return 160;       // ms para mostrar el radial (solo lo muestra, no ejecuta nada)
            case ANIM: return 240;       // ms de animación
            case SHAPE: return 0;        // 0 círculo, 1 cuadrado suave, 2 cuadrado, 3 rombo
            case DOCK_ICON: return 48;
            case APP_ICON: return 52;
            case BG: return 0;           // 0 fondo del sistema, 1 color sólido
            case DIM: return 25;         // % de oscurecimiento sobre el fondo
            case BG_COLOR: return 0xFF0B0B1F;
            case ACCENT: return 0xFF3D7BFF;
            case DOCK_H: return 76;      // altura de la barra (dp)
            case DOCK_W: return 100;     // ancho de la barra (% del ancho disponible)
            case NEW_MODE: return 0;     // 0 apps nuevas sueltas en el escritorio, 1 dentro de la carpeta «Nuevas»
            case TRACK: return 14;       // grosor del arco de fondo (dp), 0 = sin arco
            default: return 0;           // R1, R2GAP: 0 = automático; ROT: 0°; FIT: 0 = respetar los ajustes tal cual
        }
    }

    /** Apps ocultas: no aparecen en el escritorio, categorías ni «Nuevas». Siguen visibles en el menú de emergencia. */
    public java.util.Set<String> hidden() {
        java.util.Set<String> l = new java.util.HashSet<>();
        for (String x : sp.getString("hidden", "").split("\n")) if (!x.isEmpty()) l.add(x);
        return l;
    }
    public boolean isHidden(String pkg) { return hidden().contains(pkg); }
    public void setHidden(String pkg, boolean on) {
        java.util.Set<String> l = hidden();
        if (on ? l.add(pkg) : l.remove(pkg)) sp.edit().putString("hidden", TextUtils.join("\n", l)).apply();
    }

    public int get(String k) { return sp.getInt(k, def(k)); }
    public void set(String k, int v) { sp.edit().putInt(k, v).apply(); }

    /** Posiciones del dock: 0,1 a la izquierda del botón central; 2,3 a la derecha. */
    public String getDock(int i) { return sp.getString("dock" + i, null); }
    public void setDock(int i, String pkg) {
        if (pkg == null) sp.edit().remove("dock" + i).apply(); else sp.edit().putString("dock" + i, pkg).apply();
    }

    /** Apps fijadas al escritorio, en orden. */
    public List<String> pinned() {
        List<String> l = new ArrayList<>();
        for (String x : sp.getString("pinned", "").split("\n")) if (!x.isEmpty()) l.add(x);
        return l;
    }
    public boolean isPinned(String pkg) { return pinned().contains(pkg); }
    public void pin(String pkg) {
        List<String> l = pinned();
        if (!l.contains(pkg)) { l.add(pkg); sp.edit().putString("pinned", TextUtils.join("\n", l)).apply(); }
    }
    public void unpin(String pkg) {
        List<String> l = pinned();
        if (l.remove(pkg)) sp.edit().putString("pinned", TextUtils.join("\n", l)).apply();
    }
}
