package com.lexus2026.cdc;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class Elem {
    public String id;
    public String type;
    public String a;
    public int page;
    public int x;
    public int y;
    public int w;
    public int h;
    public int b;

    public Elem(String id, String type, int page, int x, int y, int w, int h, int b, String a) {
        this.id = id;
        this.type = type;
        this.page = page;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.b = b;
        this.a = a == null ? "" : a;
    }

    public Elem copy() {
        return new Elem(newId(), type, page, x, y, w, h, b, a);
    }

    public static String newId() {
        return Long.toString(System.nanoTime(), 36);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\u001F', ' ');
    }

    public static List<Elem> load(Context c) {
        List<Elem> out = new ArrayList<>();
        String raw = Skin.getStr(c, "canvas", "");
        if (raw == null) return out;
        for (String line : raw.split("\n")) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\u001F", -1);
            if (f.length < 9) continue;
            try {
                out.add(new Elem(f[0], f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                    Integer.parseInt(f[5]), Integer.parseInt(f[6]), Integer.parseInt(f[7]), f[8]));
            } catch (NumberFormatException e) {}
        }
        return out;
    }

    public static void save(Context c, List<Elem> l) {
        StringBuilder sb = new StringBuilder();
        for (Elem e : l) {
            sb.append(clean(e.id)).append('\u001F').append(clean(e.type)).append('\u001F').append(e.page).append('\u001F')
                .append(e.x).append('\u001F').append(e.y).append('\u001F').append(e.w).append('\u001F').append(e.h).append('\u001F')
                .append(e.b).append('\u001F').append(clean(e.a)).append('\n');
        }
        Skin.setStr(c, "canvas", sb.toString());
    }

    public static List<Elem> example(int cols) {
        int c = Math.max(cols, 4);
        List<Elem> l = new ArrayList<>();
        l.add(new Elem(newId(), "clock", 0, 0, 0, c, 2, 0, ""));
        l.add(new Elem(newId(), "tile", 0, 0, 2, 1, 1, 0, "Modo avión"));
        l.add(new Elem(newId(), "tile", 0, 1, 2, 1, 1, 0, "Ahorro de batería"));
        l.add(new Elem(newId(), "tile", 0, 0, 3, 1, 1, 0, "Internet"));
        l.add(new Elem(newId(), "tile", 0, 1, 3, 1, 1, 0, "Bluetooth"));
        l.add(new Elem(newId(), "notifs", 0, 0, 4, c, 5, 0, ""));
        l.add(new Elem(newId(), "media", 1, 0, 0, Math.max(3, c - 3), 3, 0, ""));
        l.add(new Elem(newId(), "bright", 1, Math.max(3, c - 3), 0, 1, 3, 0, ""));
        l.add(new Elem(newId(), "volume", 1, Math.max(3, c - 3) + 1, 0, 1, 3, 0, ""));
        return l;
    }
}
