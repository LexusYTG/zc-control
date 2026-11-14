package com.lexus2026.cdc

import android.content.Context

/** Apariencias disponibles para el panel. */
enum class Look(val key: String, val label: String, val cols: Int) {
    AOSP("aosp", "AOSP", 2),
    IOS("ios", "iOS", 4),
    XIAOMI("xiaomi", "Xiaomi", 4),
    COLOROS("coloros", "ColorOS", 4);

    companion object {
        fun fromPrefs(c: Context): Look {
            val k = c.getSharedPreferences("cc", Context.MODE_PRIVATE).getString("look", "aosp")
            return values().firstOrNull { it.key == k } ?: AOSP
        }
    }
}
