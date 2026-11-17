package com.lexus2026.cdc;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuración de la interfaz lateral (burbuja / gestos). Es compartida por los dos modos:
 * lo único que cambia entre ellos es {@link #getMode}. Se guarda aparte del Skin, en "zc_side".
 */
public final class SideConfig {
    private SideConfig() {}

    public static final int MODE_OFF = 0;
    public static final int MODE_BUBBLE = 1;
    public static final int MODE_GESTURE = 2;

    public static final String PREFS = "zc_side";
    private static final String K_MODE = "mode";
    private static final String K_APPS = "apps";

    /** Valores por defecto de los números. */
    public static final Map<String, Integer> D;

    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("bubbleSize", 46);   // dp
        m.put("bubbleAlpha", 85);  // %
        m.put("noteOn", 1);        // notificaciones dinámicas
        m.put("noteSecs", 5);      // segundos visibles
        m.put("holdSecs", 10);     // mantener apretado para entrar al lienzo
        m.put("posX", -1);         // permil del ancho (centro). -1 = pegada al borde derecho
        m.put("posY", 420);        // permil del alto (centro)
        m.put("floatWin", 1);      // abrir apps en ventana flotante
        m.put("winMode", 5);       // modo de ventana que se pide (5 = freeform)
        m.put("winBounds", 1);     // 1 = pasar tamaño y posición al lanzar; 0 = que decida el sistema
        m.put("winH", 72);         // % del alto de pantalla
        m.put("winX", 50);         // posición horizontal: 0 = izquierda, 100 = derecha
        m.put("winY", 30);         // posición vertical: 0 = arriba, 100 = abajo
        m.put("winHack", 0);       // lanzar antes la actividad auxiliar invisible (solo Android antiguos)
        m.put("winHackMs", 150);   // espera entre la auxiliar y la app
        m.put("winExempt", 1);     // desbloquear APIs ocultas por reflexión
        m.put("winSettings", 1);   // escribir las opciones globales de ventana libre
        m.put("winSize", 80);      // % del ancho de pantalla
        m.put("edgeW", 20);        // dp, ancho de la zona del borde (modo gestos)
        m.put("edgeH", 60);        // % del alto, zona del borde (modo gestos)
        D = Collections.unmodifiableMap(m);
    }

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int get(Context c, String key) {
        Integer d = D.get(key);
        return prefs(c).getInt(key, d != null ? d : 0);
    }

    public static void set(Context c, String key, int v) { prefs(c).edit().putInt(key, v).apply(); }

    public static int getMode(Context c) { return prefs(c).getInt(K_MODE, MODE_OFF); }

    public static void setMode(Context c, int mode) { prefs(c).edit().putInt(K_MODE, mode).apply(); }

    /** Guarda la posición libre de la burbuja (centro, en permil de la pantalla). */
    public static void setPos(Context c, int xPermil, int yPermil) {
        prefs(c).edit().putInt("posX", xPermil).putInt("posY", yPermil).apply();
    }

    public static void resetPos(Context c) { setPos(c, -1, D.get("posY")); }

    // ---------------- accesos directos ----------------

    public static List<String> shortcuts(Context c) {
        List<String> out = new ArrayList<>();
        String raw = prefs(c).getString(K_APPS, null);
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split("\n")) if (!s.trim().isEmpty() && !out.contains(s.trim())) out.add(s.trim());
        return out;
    }

    public static void saveShortcuts(Context c, List<String> list) {
        prefs(c).edit().putString(K_APPS, String.join("\n", list)).apply();
    }

    public static void addShortcut(Context c, String pkg) {
        List<String> l = shortcuts(c);
        if (!l.contains(pkg)) { l.add(pkg); saveShortcuts(c, l); }
    }

    public static void removeShortcut(Context c, String pkg) {
        List<String> l = shortcuts(c);
        if (l.remove(pkg)) saveShortcuts(c, l);
    }

    /** Mueve el acceso de la posición [from] a la posición [to] (si existen). */
    public static void moveShortcut(Context c, int from, int to) {
        List<String> l = shortcuts(c);
        if (from < 0 || from >= l.size() || to < 0 || to >= l.size() || from == to) return;
        l.add(to, l.remove(from));
        saveShortcuts(c, l);
    }

    // ---------------- navegación del sistema ----------------

    /**
     * El modo gestos solo se permite con la navegación de tres botones (0), para no chocar con los
     * gestos del sistema. 1 = dos botones, 2 = gestos.
     */
    public static boolean threeButtonNav(Context c) {
        int mode = -1;
        try { mode = Settings.Secure.getInt(c.getContentResolver(), "navigation_mode", -1); } catch (Throwable e) { mode = -1; }
        if (mode < 0) {
            try {
                int id = c.getResources().getIdentifier("config_navBarInteractionMode", "integer", "android");
                if (id != 0) mode = c.getResources().getInteger(id);
            } catch (Throwable e) { mode = -1; }
        }
        return mode == 0;
    }
}
