package com.lexus2026.cdc;

/**
 * Geometría y selección del radial de dos etapas. No dibuja nada: calcula posiciones (arcos compactos centrados
 * en el eje vertical del botón Home) y decide qué burbuja está seleccionada según el dedo.
 * Índice global: 0..n1-1 = arco 1, n1..n1+n2-1 = arco 2 (coincide con el orden de la lista de categorías).
 */
public class RadialNavigationEngine {
    public static final int NONE = -1;

    public int n1, n2;
    public float cx, cy, r1, r2, rad, arcDeg, rotDeg;
    public float[] px = new float[0], py = new float[0];
    public boolean stage2;
    public int sel = NONE;
    private float sens = 1f, stage2Px;

    /** Calcula los dos arcos. Si no caben en la pantalla, reduce las burbujas hasta que quepan. */
    public void layout(float cx, float cy, int w, float topInset, float d, LauncherSettings s, int c1, int c2) {
        this.cx = cx; this.cy = cy; n1 = c1; n2 = c2; stage2 = false; sel = NONE;
        arcDeg = Math.max(20, Math.min(300, s.get(LauncherSettings.ARC)));
        rotDeg = s.get(LauncherSettings.ROT);
        boolean fit = s.get(LauncherSettings.FIT) == 1;
        float r1c = s.get(LauncherSettings.R1) * d, g2c = s.get(LauncherSettings.R2GAP) * d;
        float half = (float) Math.toRadians(arcDeg / 2f), arcRad = 2 * half;
        float gap = s.get(LauncherSettings.GAP) * d;
        float R = s.get(LauncherSettings.BUBBLE) * d / 2f, minR = 14 * d;
        float homeR = 30 * d, margin = 8 * d, labelSpace = 44 * d;
        float a1 = 0, a2 = 0;
        for (int it = 0; it < 10; it++) {
            float need = 2 * R + gap;
            a1 = Math.max(homeR + R + gap, n1 > 1 ? need * (n1 - 1) / arcRad : 0);
            if (r1c > 0) a1 = r1c;
            a2 = Math.max(a1 + 2 * R + gap, n2 > 1 ? need * (n2 - 1) / arcRad : 0);
            if (g2c > 0) a2 = a1 + g2c;
            if (!fit) break;
            float outer = n2 > 0 ? a2 : a1;
            float limH = (Math.min(cx, w - cx) - margin - R) / (float) Math.sin(half);
            float limV = cy - topInset - margin - R - labelSpace;
            if (outer <= Math.min(limH, limV) || R <= minR) break;
            R = Math.max(minR, R * 0.92f);
        }
        r1 = a1; r2 = a2; rad = R;
        px = new float[n1 + n2]; py = new float[n1 + n2];
        place(0, n1, r1, half);
        place(n1, n2, r2, half);
        stage2Px = s.get(LauncherSettings.STAGE2) * d;
        sens = s.get(LauncherSettings.SENS) / 100f;
    }

    private void place(int off, int cnt, float r, float half) {
        for (int k = 0; k < cnt; k++) {
            float a = (cnt == 1 ? 0 : -half + k * (2 * half / (cnt - 1))) + (float) Math.toRadians(rotDeg);
            px[off + k] = cx + r * (float) Math.sin(a);
            py[off + k] = cy - r * (float) Math.cos(a);
        }
    }

    /** Dedo en coordenadas del overlay. Devuelve true si cambió la etapa o la selección. */
    public boolean update(float rx, float ry) {
        float vx = cx + (rx - cx) * sens, vy = cy + (ry - cy) * sens; // la sensibilidad escala el recorrido
        float dist = (float) Math.hypot(vx - cx, vy - cy);
        boolean was2 = stage2;
        int wasSel = sel;
        if (n2 > 0) {
            if (!stage2 && dist >= r1 + stage2Px) stage2 = true;
            else if (stage2 && dist < r1 - rad) stage2 = false; // histéresis: no se cierra al ir de un arco al otro
        }
        int best = NONE, lim = stage2 ? n1 + n2 : n1;
        float bd = rad * 1.4f;
        for (int i = 0; i < lim; i++) {
            float dd = (float) Math.hypot(vx - px[i], vy - py[i]);
            if (dd < bd) { bd = dd; best = i; }
        }
        sel = best;
        return was2 != stage2 || wasSel != sel;
    }
}
