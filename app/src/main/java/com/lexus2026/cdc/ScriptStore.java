package com.lexus2026.cdc;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScriptStore {
    private ScriptStore() {}

    static final class Item {
        String id, name = "", src = "";
        boolean on = true;
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("zc_scripts", Context.MODE_PRIVATE);
    }

    private static void bump(Context c) {
        SharedPreferences p = sp(c);
        p.edit().putLong("ver", p.getLong("ver", 0) + 1).apply();
    }

    static long version(Context c) { return sp(c).getLong("ver", 0); }

    static boolean master(Context c) { return sp(c).getBoolean("master", true); }

    static void setMaster(Context c, boolean v) {
        sp(c).edit().putBoolean("master", v).apply();
        bump(c);
    }

    static String newId() { return Long.toString(System.nanoTime(), 36); }

    static List<Item> list(Context c) {
        SharedPreferences p = sp(c);
        List<Item> out = new ArrayList<>();
        for (String id : p.getString("list", "").split("\n")) {
            if (id.isEmpty()) continue;
            Item it = new Item();
            it.id = id;
            it.name = p.getString("n_" + id, "");
            it.src = p.getString("s_" + id, "");
            it.on = p.getBoolean("e_" + id, true);
            out.add(it);
        }
        return out;
    }

    static Item get(Context c, String id) {
        for (Item it : list(c)) if (it.id.equals(id)) return it;
        return null;
    }

    static void save(Context c, Item it) {
        SharedPreferences p = sp(c);
        StringBuilder ids = new StringBuilder(p.getString("list", ""));
        boolean has = false;
        for (String x : ids.toString().split("\n")) if (x.equals(it.id)) has = true;
        if (!has) ids.append(ids.length() > 0 ? "\n" : "").append(it.id);
        p.edit().putString("list", ids.toString()).putString("n_" + it.id, it.name)
            .putString("s_" + it.id, it.src).putBoolean("e_" + it.id, it.on).apply();
        bump(c);
    }

    static void delete(Context c, String id) {
        SharedPreferences p = sp(c);
        StringBuilder ids = new StringBuilder();
        for (String x : p.getString("list", "").split("\n")) {
            if (x.isEmpty() || x.equals(id)) continue;
            if (ids.length() > 0) ids.append('\n');
            ids.append(x);
        }
        p.edit().putString("list", ids.toString()).remove("n_" + id).remove("s_" + id).remove("e_" + id).apply();
        bump(c);
    }

    static Map<String, String> vars(Context c) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : sp(c).getString("vars", "").split("\n")) {
            int i = line.indexOf('\u001F');
            if (i > 0) m.put(line.substring(0, i), line.substring(i + 1));
        }
        return m;
    }

    static void saveVars(Context c, Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet())
            sb.append(e.getKey().replace('\n', ' ').replace('\u001F', ' ')).append('\u001F')
                .append(e.getValue().replace('\n', ' ').replace('\u001F', ' ')).append('\n');
        sp(c).edit().putString("vars", sb.toString()).apply();
    }
}
