package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Lang {
    private static final String PREFS = "lang_prefs";
    private static final String KEY = "lang_id";
    private static Context ctx;
    private static SparseArray<String> texts = new SparseArray<>();
    private static SparseArray<String> base = new SparseArray<>();
    private static String current = "";
    private static final java.util.HashMap<String, Integer> rev = new java.util.HashMap<>();

    private Lang() {}

    public static synchronized void init(Context c) {
        ctx = c.getApplicationContext();
        List<String[]> langs = languages();
        String saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        String first = langs.isEmpty() ? "" : langs.get(0)[1];
        current = first;
        for (String[] l : langs) if (l[1].equals(saved)) current = saved;
        base = parse(first);
        rev.clear();
        for (int i = 0; i < base.size(); i++) if (!rev.containsKey(base.valueAt(i))) rev.put(base.valueAt(i), base.keyAt(i));
        texts = current.equals(first) ? base : parse(current);
    }

    public static String t(int id) {
        String s = texts.get(id);
        if (s == null) s = base.get(id);
        return s != null ? s : String.valueOf(id);
    }

    public static String s(String key) {
        Integer id = rev.get(key);
        return id != null ? t(id) : key;
    }

    public static List<String> sAll(String[] keys) {
        List<String> out = new ArrayList<>();
        for (String k : keys) out.add(s(k));
        return out;
    }

    public static String currentId() {
        return current;
    }

    public static List<String[]> languages() {
        List<String[]> out = new ArrayList<>();
        if (ctx == null) return out;
        try (BufferedReader r = open()) {
            String l;
            while ((l = r.readLine()) != null) {
                if (l.startsWith("id=")) break;
                if (!l.startsWith("Leng.name=")) continue;
                int a = l.indexOf("{id=");
                int b = l.lastIndexOf('}');
                if (a < 0 || b < a) continue;
                out.add(new String[]{l.substring(10, a).trim(), l.substring(a + 4, b).trim()});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void pick(final Activity a) {
        final List<String[]> ls = languages();
        String[] names = new String[ls.size()];
        int sel = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = ls.get(i)[0];
            if (ls.get(i)[1].equals(current)) sel = i;
        }
        new AlertDialog.Builder(a)
            .setTitle(t(872))
            .setSingleChoiceItems(names, sel, (d, w) -> {
                d.dismiss();
                if (!ls.get(w)[1].equals(current)) apply(a, ls.get(w)[1]);
            })
            .show();
    }

    private static void apply(Activity a, String id) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, id).commit();
        Intent i = new Intent(a, SettingsHomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finishAffinity();
        new Handler(Looper.getMainLooper()).postDelayed(() -> Process.killProcess(Process.myPid()), 250);
    }

    private static BufferedReader open() {
        return new BufferedReader(new InputStreamReader(ctx.getResources().openRawResource(R.raw.lengua), StandardCharsets.UTF_8));
    }

    private static SparseArray<String> parse(String id) {
        SparseArray<String> n = new SparseArray<>();
        if (ctx == null || id.isEmpty()) return n;
        try (BufferedReader r = open()) {
            String l;
            boolean in = false;
            String head = "id=" + id + "{";
            while ((l = r.readLine()) != null) {
                if (!in) {
                    if (l.trim().equals(head)) in = true;
                    continue;
                }
                if (l.trim().equals("..end..}")) break;
                int c = l.indexOf(',');
                if (c < 1) continue;
                int a = l.indexOf('{', c);
                int b = l.lastIndexOf('}');
                if (a < 0 || b <= a) continue;
                try {
                    n.put(Integer.parseInt(l.substring(0, c).trim()), unescape(l.substring(a + 1, b)));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return n;
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char nx = s.charAt(++i);
                switch (nx) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'u':
                        if (i + 4 < s.length()) {
                            sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                        break;
                    default: sb.append(nx);
                }
            } else sb.append(ch);
        }
        return sb.toString();
    }
}
