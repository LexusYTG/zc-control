package com.lexus2026.cdc;

import android.content.Context;

final class SL {
    private SL() {}

    private static Boolean es;

    static boolean es(Context c) {
        if (es == null) {
            String id = c.getApplicationContext().getSharedPreferences("lang_prefs", Context.MODE_PRIVATE).getString("lang_id", "");
            es = id.isEmpty() ? java.util.Locale.getDefault().getLanguage().equals("es") : id.startsWith("es");
        }
        return es;
    }

    static String t(Context c, String s, String e) { return es(c) ? s : e; }

    static String[] t(Context c, String[] s, String[] e) { return es(c) ? s : e; }
}
