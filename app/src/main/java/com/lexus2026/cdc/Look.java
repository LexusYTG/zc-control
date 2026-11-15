package com.lexus2026.cdc;

import android.content.Context;

/** Apariencias disponibles para el panel. */
public enum Look {
    AOSP("aosp", "AOSP", 2),
    IOS("ios", "iOS", 4),
    XIAOMI("xiaomi", "Xiaomi", 4),
    COLOROS("coloros", "ColorOS", 4);

    public final String key;
    public final String label;
    public final int cols;

    Look(String key, String label, int cols) {
        this.key = key;
        this.label = label;
        this.cols = cols;
    }

    public static Look fromPrefs(Context c) {
        String k = c.getSharedPreferences("cc", Context.MODE_PRIVATE).getString("look", "aosp");
        for (Look l : values()) {
            if (l.key.equals(k)) return l;
        }
        return AOSP;
    }
}
