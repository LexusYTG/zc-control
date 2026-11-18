package com.lexus2026.cdc;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Categorías y asignaciones app→categorías (una app puede estar en varias). La lista es ordenada: las primeras
 * N pertenecen al radial 1 y las siguientes M al radial 2. Persistente en SharedPreferences.
 */
public class CategoryManager {
    public static class Category {
        public String id, name, icon;
        public int color;
        public int shape = -1; // -1 = la forma global de los ajustes
    }

    private final SharedPreferences sp;
    private final List<Category> cats = new ArrayList<>();
    private final Map<String, Set<String>> assign = new HashMap<>();

    public CategoryManager(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("zc_launcher_cats", Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        cats.clear();
        assign.clear();
        try {
            String s = sp.getString("cats", null);
            if (s != null) {
                JSONArray a = new JSONArray(s);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Category c = new Category();
                    c.id = o.getString("id");
                    c.name = o.optString("name", "?");
                    c.icon = o.optString("icon", "•");
                    c.color = o.optInt("color", 0xFF3D7BFF);
                    c.shape = o.optInt("shape", -1);
                    cats.add(c);
                }
            }
            String m = sp.getString("assign", null);
            if (m != null) {
                JSONObject o = new JSONObject(m);
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String pkg = it.next();
                    JSONArray ids = o.getJSONArray(pkg);
                    Set<String> set = new LinkedHashSet<>();
                    for (int j = 0; j < ids.length(); j++) set.add(ids.getString(j));
                    if (!set.isEmpty()) assign.put(pkg, set);
                }
            }
        } catch (Exception ignored) { }
        if (!sp.contains("cats")) {
            add(Lang.t(241), "🎮", 0xFF7C4DFF);
            add(Lang.t(242), "🎵", 0xFFE91E63);
            add(Lang.t(856), "🛠", 0xFF2979FF);
            add(Lang.t(243), "💬", 0xFF00BCD4);
            add(Lang.t(244), "💼", 0xFFFF9800);
            add(Lang.t(245), "⭐", 0xFFFFC107);
            add(Lang.t(246), "🖼", 0xFF4CAF50);
            add(Lang.t(841), "⚙", 0xFF607D8B);
        }
    }

    public void save() {
        try {
            JSONArray a = new JSONArray();
            for (Category c : cats) {
                JSONObject o = new JSONObject();
                o.put("id", c.id); o.put("name", c.name); o.put("icon", c.icon);
                o.put("color", c.color); o.put("shape", c.shape);
                a.put(o);
            }
            JSONObject m = new JSONObject();
            for (Map.Entry<String, Set<String>> e : assign.entrySet()) m.put(e.getKey(), new JSONArray(e.getValue()));
            sp.edit().putString("cats", a.toString()).putString("assign", m.toString()).apply();
        } catch (Exception ignored) { }
    }

    public List<Category> list() { return cats; }

    public Category byId(String id) {
        for (Category c : cats) if (c.id.equals(id)) return c;
        return null;
    }

    public Category add(String name, String icon, int color) {
        Category c = new Category();
        c.id = "c" + System.currentTimeMillis() + "_" + cats.size();
        c.name = name; c.icon = icon; c.color = color;
        cats.add(c);
        save();
        return c;
    }

    public void remove(String id) {
        Category c = byId(id);
        if (c == null) return;
        cats.remove(c);
        Iterator<Map.Entry<String, Set<String>>> it = assign.entrySet().iterator();
        while (it.hasNext()) {
            Set<String> s = it.next().getValue();
            s.remove(id);
            if (s.isEmpty()) it.remove();
        }
        save();
    }

    public void move(String id, int delta) {
        Category c = byId(id);
        if (c == null) return;
        int i = cats.indexOf(c), j = i + delta;
        if (j < 0 || j >= cats.size()) return;
        cats.remove(i);
        cats.add(j, c);
        save();
    }

    public Set<String> of(String pkg) {
        Set<String> s = assign.get(pkg);
        return s == null ? new LinkedHashSet<String>() : new LinkedHashSet<>(s);
    }

    public boolean has(String pkg, String id) { Set<String> s = assign.get(pkg); return s != null && s.contains(id); }
    public boolean hasAny(String pkg) { return assign.containsKey(pkg); }

    public void setApp(String pkg, Set<String> ids) {
        if (ids.isEmpty()) assign.remove(pkg); else assign.put(pkg, new LinkedHashSet<>(ids));
        save();
    }

    public void removeApp(String pkg, String id) {
        Set<String> s = of(pkg);
        s.remove(id);
        setApp(pkg, s);
    }

    public void forget(String pkg) { if (assign.remove(pkg) != null) save(); }
}
