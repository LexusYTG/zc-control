package com.lexus2026.cdc;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class GameSettings {
    private GameSettings() {}

    public static final class Profile {
        public boolean procs = true;
        public int pct = 90;
        public boolean compile = false;
        public boolean panel = false;
        public boolean saver = false;
        public boolean noKeep = false;
        public boolean bgLimit = false;
    }

    public static final class Rec {
        public long code;
        public String name;
        public long time;
        public boolean ok;
        public String mode;
    }

    public static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("gc", Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) {
        return sp(c).getBoolean("enabled", false);
    }

    public static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("enabled", on).apply();
    }

    private static Profile load(SharedPreferences s, String k) {
        Profile p = new Profile();
        p.procs = s.getBoolean(k + "procs", p.procs);
        p.pct = s.getInt(k + "pct", p.pct);
        p.compile = s.getBoolean(k + "compile", p.compile);
        p.panel = s.getBoolean(k + "panel", p.panel);
        p.saver = s.getBoolean(k + "saver", p.saver);
        p.noKeep = s.getBoolean(k + "noKeep", p.noKeep);
        p.bgLimit = s.getBoolean(k + "bgLimit", p.bgLimit);
        return p;
    }

    private static void store(SharedPreferences.Editor e, String k, Profile p) {
        e.putBoolean(k + "procs", p.procs);
        e.putInt(k + "pct", p.pct);
        e.putBoolean(k + "compile", p.compile);
        e.putBoolean(k + "panel", p.panel);
        e.putBoolean(k + "saver", p.saver);
        e.putBoolean(k + "noKeep", p.noKeep);
        e.putBoolean(k + "bgLimit", p.bgLimit);
    }

    public static Profile defaults(Context c) {
        return load(sp(c), "d|");
    }

    public static boolean hasProfile(Context c, String pkg) {
        return sp(c).getBoolean("p|" + pkg + "|custom", false);
    }

    public static Profile profile(Context c, String pkg) {
        return hasProfile(c, pkg) ? load(sp(c), "p|" + pkg + "|") : defaults(c);
    }

    public static void saveDefaults(Context c, Profile p) {
        SharedPreferences.Editor e = sp(c).edit();
        store(e, "d|", p);
        e.apply();
    }

    public static void saveProfile(Context c, String pkg, Profile p) {
        SharedPreferences.Editor e = sp(c).edit();
        store(e, "p|" + pkg + "|", p);
        e.putBoolean("p|" + pkg + "|custom", true);
        e.apply();
    }

    public static void resetProfile(Context c, String pkg) {
        SharedPreferences s = sp(c);
        SharedPreferences.Editor e = s.edit();
        for (String k : s.getAll().keySet()) if (k.startsWith("p|" + pkg + "|")) e.remove(k);
        e.apply();
    }

    public static String compileMode(Context c) {
        return sp(c).getString("cmode", "speed");
    }

    public static void setCompileMode(Context c, String m) {
        sp(c).edit().putString("cmode", m).apply();
    }

    private static Set<String> set(Context c, String k) {
        Set<String> s = sp(c).getStringSet(k, null);
        return s == null ? new HashSet<String>() : new HashSet<String>(s);
    }

    public static Set<String> exceptions(Context c) {
        return set(c, "exc");
    }

    public static void setExceptions(Context c, Set<String> s) {
        sp(c).edit().putStringSet("exc", new HashSet<String>(s)).apply();
    }

    public static Set<String> extras(Context c) {
        return set(c, "extra");
    }

    public static void setExtras(Context c, Set<String> s) {
        sp(c).edit().putStringSet("extra", new HashSet<String>(s)).apply();
    }

    public static Rec rec(Context c, String pkg) {
        String v = sp(c).getString("r|" + pkg, null);
        if (v == null) return null;
        String[] a = v.split("\u001f", -1);
        if (a.length < 5) return null;
        try {
            Rec r = new Rec();
            r.code = Long.parseLong(a[0]);
            r.name = a[1];
            r.time = Long.parseLong(a[2]);
            r.ok = a[3].equals("1");
            r.mode = a[4];
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    public static void saveRec(Context c, String pkg, Rec r) {
        String v = r.code + "\u001f" + r.name + "\u001f" + r.time + "\u001f" + (r.ok ? "1" : "0") + "\u001f" + r.mode;
        sp(c).edit().putString("r|" + pkg, v).apply();
    }

    public static void dropRec(Context c, String pkg) {
        sp(c).edit().remove("r|" + pkg).apply();
    }
}
