package com.lexus2026.cdc;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelo del constructor visual del launcher: reglas, macros, paneles y botones flotantes.
 * Se guarda como JSON y, en cada cambio, se genera el script legible (español o inglés) en ScriptStore,
 * así el motor de scripts existente lo ejecuta sin saber que viene de una interfaz visual.
 * El script generado se puede abrir y retocar a mano en el editor de texto, pero la próxima edición visual lo regenera.
 */
final class VisualModel {
    static final String SCRIPT_ID = "zc_visual";

    /** Un paso: una línea de script; si {@code block}, abre un bloque (si / repetir / mientras) con hijos y «fin». */
    static final class Step {
        String text = "";
        boolean block;
        List<Step> kids = new ArrayList<>();
        List<Step> alt;            // solo para «si»: pasos del «sino»
    }

    /** Regla («cuando…», «cada…», «a las…») o macro con nombre. */
    static final class Rule {
        String name = "";           // solo macros
        String head = "";           // solo reglas: línea de evento completa
        boolean macro, on = true;
        List<Step> steps = new ArrayList<>();
    }

    static final class Item {
        String kind = "app";        // app | category | action
        String name = "", icon = "", tkind = "macro", target = "";
    }

    static final class Panel {
        String name = "";
        String from = "auto", anim = "slide", title = "";
        int dur = 260, cols = 4, w = -1, h = -1, bg = 0xF0121228, radius = 26, tcolor = 0xFFFFFFFF, isize = 52;
        boolean outside = true, autoclose = true;
        List<Item> items = new ArrayList<>();
    }

    static final class Btn {
        String name = "", icon = "●", label = "", app = "", shape = "circle";
        String v = "down", hz = "right";
        int x, y, size = 48, bg = 0xFF3D7BFF, opacity = 100, tcolor = 0xFFFFFFFF;
        boolean visible = true;
    }

    /**
     * Elemento libre del escritorio, colocado a mano en el editor visual. Posición y tamaño en milésimas del ancho (x, w)
     * y del alto (y, h) de la pantalla, así se adapta a cualquier resolución. Los gestos guardan una acción «tipo:destino»:
     * macro:Nombre · app:paquete · drawer:* | id de categoría · panel:Nombre · shell:comando · home · radial · (vacío = nada).
     */
    static final class Free {
        String id = Elem.newId(), type = "text";   // app | text | clock | image | widget | category | action | shape
        String a = "", icon = "";
        int x = 100, y = 300, w = 300, h = 120;
        int bg = 0, tcolor = 0xFFFFFFFF, radius = 16, tsize = 18, opacity = 100, fmt = 0;
        boolean visible = true, bold, label = true;
        String tap = "", hold = "", dbl = "", sup = "", sdn = "", slf = "", srt = "";

        Free copy() {
            Free f = new Free();
            f.type = type; f.a = a; f.icon = icon; f.x = x; f.y = y; f.w = w; f.h = h; f.bg = bg; f.tcolor = tcolor;
            f.radius = radius; f.tsize = tsize; f.opacity = opacity; f.fmt = fmt; f.visible = visible; f.bold = bold;
            f.label = label; f.tap = tap; f.hold = hold; f.dbl = dbl; f.sup = sup; f.sdn = sdn; f.slf = slf; f.srt = srt;
            return f;
        }
    }

    final List<Rule> rules = new ArrayList<>();
    final List<Panel> panels = new ArrayList<>();
    final List<Btn> buttons = new ArrayList<>();
    final List<Free> free = new ArrayList<>();

    // ---------------------------------------------------------------- persistencia

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("zc_visual", Context.MODE_PRIVATE);
    }

    static VisualModel load(Context c) {
        VisualModel m = new VisualModel();
        try {
            JSONObject o = new JSONObject(sp(c).getString("model", "{}"));
            JSONArray ra = o.optJSONArray("rules");
            if (ra != null) for (int i = 0; i < ra.length(); i++) m.rules.add(ruleFrom(ra.getJSONObject(i)));
            JSONArray pa = o.optJSONArray("panels");
            if (pa != null) for (int i = 0; i < pa.length(); i++) m.panels.add(panelFrom(pa.getJSONObject(i)));
            JSONArray ba = o.optJSONArray("buttons");
            if (ba != null) for (int i = 0; i < ba.length(); i++) m.buttons.add(btnFrom(ba.getJSONObject(i)));
            JSONArray fa = o.optJSONArray("free");
            if (fa != null) for (int i = 0; i < fa.length(); i++) m.free.add(freeFrom(fa.getJSONObject(i)));
        } catch (Throwable ignored) { }
        return m;
    }

    /** Guarda el modelo y regenera el script que ejecuta el motor. */
    void save(Context c) {
        try {
            JSONObject o = new JSONObject();
            JSONArray ra = new JSONArray();
            for (Rule r : rules) ra.put(ruleTo(r));
            o.put("rules", ra);
            JSONArray pa = new JSONArray();
            for (Panel p : panels) pa.put(panelTo(p));
            o.put("panels", pa);
            JSONArray ba = new JSONArray();
            for (Btn b : buttons) ba.put(btnTo(b));
            o.put("buttons", ba);
            JSONArray fa = new JSONArray();
            for (Free f : free) fa.put(freeTo(f));
            o.put("free", fa);
            sp(c).edit().putString("model", o.toString()).apply();
        } catch (Throwable ignored) { }
        ScriptStore.Item it = ScriptStore.get(c, SCRIPT_ID);
        if (it == null) { it = new ScriptStore.Item(); it.id = SCRIPT_ID; it.on = true; }
        it.name = "🧩 " + SL.t(c, "Constructor visual", "Visual builder");
        it.src = toScript(SL.es(c));
        ScriptStore.save(c, it);
    }

    static Step stepFrom(JSONObject o) throws Exception {
        Step s = new Step();
        s.text = o.optString("t");
        s.block = o.optBoolean("b");
        JSONArray k = o.optJSONArray("k");
        if (k != null) for (int i = 0; i < k.length(); i++) s.kids.add(stepFrom(k.getJSONObject(i)));
        JSONArray a = o.optJSONArray("a");
        if (a != null) {
            s.alt = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) s.alt.add(stepFrom(a.getJSONObject(i)));
        }
        return s;
    }

    static JSONObject stepTo(Step s) throws Exception {
        JSONObject o = new JSONObject();
        o.put("t", s.text).put("b", s.block);
        JSONArray k = new JSONArray();
        for (Step c : s.kids) k.put(stepTo(c));
        o.put("k", k);
        if (s.alt != null) {
            JSONArray a = new JSONArray();
            for (Step c : s.alt) a.put(stepTo(c));
            o.put("a", a);
        }
        return o;
    }

    static Rule ruleFrom(JSONObject o) throws Exception {
        Rule r = new Rule();
        r.name = o.optString("n");
        r.head = o.optString("h");
        r.macro = o.optBoolean("m");
        r.on = o.optBoolean("on", true);
        JSONArray a = o.optJSONArray("s");
        if (a != null) for (int i = 0; i < a.length(); i++) r.steps.add(stepFrom(a.getJSONObject(i)));
        return r;
    }

    static JSONObject ruleTo(Rule r) throws Exception {
        JSONObject o = new JSONObject();
        o.put("n", r.name).put("h", r.head).put("m", r.macro).put("on", r.on);
        JSONArray a = new JSONArray();
        for (Step s : r.steps) a.put(stepTo(s));
        o.put("s", a);
        return o;
    }

    static Panel panelFrom(JSONObject o) throws Exception {
        Panel p = new Panel();
        p.name = o.optString("name");
        p.from = o.optString("from", "auto");
        p.anim = o.optString("anim", "slide");
        p.title = o.optString("title");
        p.dur = o.optInt("dur", 260);
        p.cols = o.optInt("cols", 4);
        p.w = o.optInt("w", -1);
        p.h = o.optInt("h", -1);
        p.bg = o.optInt("bg", 0xF0121228);
        p.radius = o.optInt("radius", 26);
        p.tcolor = o.optInt("tcolor", 0xFFFFFFFF);
        p.isize = o.optInt("isize", 52);
        p.outside = o.optBoolean("outside", true);
        p.autoclose = o.optBoolean("autoclose", true);
        JSONArray a = o.optJSONArray("items");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.getJSONObject(i);
            Item it = new Item();
            it.kind = j.optString("kind", "app");
            it.name = j.optString("name");
            it.icon = j.optString("icon");
            it.tkind = j.optString("tkind", "macro");
            it.target = j.optString("target");
            p.items.add(it);
        }
        return p;
    }

    static JSONObject panelTo(Panel p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", p.name).put("from", p.from).put("anim", p.anim).put("title", p.title).put("dur", p.dur)
            .put("cols", p.cols).put("w", p.w).put("h", p.h).put("bg", p.bg).put("radius", p.radius)
            .put("tcolor", p.tcolor).put("isize", p.isize).put("outside", p.outside).put("autoclose", p.autoclose);
        JSONArray a = new JSONArray();
        for (Item it : p.items)
            a.put(new JSONObject().put("kind", it.kind).put("name", it.name).put("icon", it.icon)
                .put("tkind", it.tkind).put("target", it.target));
        o.put("items", a);
        return o;
    }

    static Btn btnFrom(JSONObject o) {
        Btn b = new Btn();
        b.name = o.optString("name");
        b.icon = o.optString("icon", "●");
        b.label = o.optString("label");
        b.app = o.optString("app");
        b.shape = o.optString("shape", "circle");
        b.v = o.optString("v", "down");
        b.hz = o.optString("hz", "right");
        b.x = o.optInt("x");
        b.y = o.optInt("y");
        b.size = o.optInt("size", 48);
        b.bg = o.optInt("bg", 0xFF3D7BFF);
        b.opacity = o.optInt("opacity", 100);
        b.tcolor = o.optInt("tcolor", 0xFFFFFFFF);
        b.visible = o.optBoolean("visible", true);
        return b;
    }

    static JSONObject btnTo(Btn b) throws Exception {
        return new JSONObject().put("name", b.name).put("icon", b.icon).put("label", b.label).put("app", b.app)
            .put("shape", b.shape).put("v", b.v).put("hz", b.hz).put("x", b.x).put("y", b.y).put("size", b.size)
            .put("bg", b.bg).put("opacity", b.opacity).put("tcolor", b.tcolor).put("visible", b.visible);
    }

    static Free freeFrom(JSONObject o) {
        Free f = new Free();
        f.id = o.optString("id", f.id);
        f.type = o.optString("type", "text");
        f.a = o.optString("a");
        f.icon = o.optString("icon");
        f.x = o.optInt("x", 100); f.y = o.optInt("y", 300); f.w = o.optInt("w", 300); f.h = o.optInt("h", 120);
        f.bg = o.optInt("bg", 0); f.tcolor = o.optInt("tc", 0xFFFFFFFF); f.radius = o.optInt("r", 16);
        f.tsize = o.optInt("ts", 18); f.opacity = o.optInt("op", 100); f.fmt = o.optInt("fmt", 0);
        f.visible = o.optBoolean("vis", true); f.bold = o.optBoolean("bold"); f.label = o.optBoolean("lbl", true);
        f.tap = o.optString("tap"); f.hold = o.optString("hold"); f.dbl = o.optString("dbl");
        f.sup = o.optString("sup"); f.sdn = o.optString("sdn"); f.slf = o.optString("slf"); f.srt = o.optString("srt");
        return f;
    }

    static JSONObject freeTo(Free f) throws Exception {
        return new JSONObject().put("id", f.id).put("type", f.type).put("a", f.a).put("icon", f.icon)
            .put("x", f.x).put("y", f.y).put("w", f.w).put("h", f.h).put("bg", f.bg).put("tc", f.tcolor).put("r", f.radius)
            .put("ts", f.tsize).put("op", f.opacity).put("fmt", f.fmt).put("vis", f.visible).put("bold", f.bold).put("lbl", f.label)
            .put("tap", f.tap).put("hold", f.hold).put("dbl", f.dbl).put("sup", f.sup).put("sdn", f.sdn).put("slf", f.slf).put("srt", f.srt);
    }

    // ---------------------------------------------------------------- generador de script

    private static String q(String s) { return "\"" + (s == null ? "" : s.replace("\"", "'").replace("\n", " ")) + "\""; }

    private static String hex(int c) { return String.format("#%08X", c); }

    private static String dirWord(String d, boolean es) {
        switch (d) {
            case "left": return es ? "izquierda" : "left";
            case "right": return es ? "derecha" : "right";
            case "up": return es ? "arriba" : "up";
            case "down": return es ? "abajo" : "down";
            default: return es ? "centro" : "center";
        }
    }

    private static String animWord(String a, boolean es) {
        switch (a) {
            case "zoom": return "zoom";
            case "fade": return es ? "fundido" : "fade";
            case "bounce": return es ? "rebote" : "bounce";
            case "none": return es ? "ninguna" : "none";
            default: return es ? "deslizar" : "slide";
        }
    }

    private static void steps(StringBuilder sb, List<Step> l, int ind, boolean es) {
        String pad = "  ";
        for (int i = 0; i < ind; i++) pad += "  ";
        for (Step s : l) {
            if (s.text.trim().isEmpty()) continue;
            sb.append(pad).append(s.text.trim()).append('\n');
            if (s.block) {
                steps(sb, s.kids, ind + 1, es);
                if (s.alt != null && !s.alt.isEmpty()) {
                    sb.append(pad).append(es ? "sino" : "else").append('\n');
                    steps(sb, s.alt, ind + 1, es);
                }
                sb.append(pad).append(es ? "fin" : "end").append('\n');
            }
        }
    }

    String toScript(boolean es) {
        StringBuilder sb = new StringBuilder();
        String end = es ? "fin" : "end";
        sb.append(es ? "// Generado por el constructor visual. Se regenera al editar desde la interfaz.\n\n"
            : "// Generated by the visual builder. It is regenerated when edited from the interface.\n\n");
        for (Panel p : panels) {
            if (p.name.trim().isEmpty()) continue;
            sb.append(es ? "panel " : "panel ").append(q(p.name)).append('\n');
            if (!p.from.equals("auto")) sb.append("  ").append(es ? "desde " : "from ").append(dirWord(p.from, es)).append('\n');
            sb.append("  ").append(es ? "animacion " : "animation ").append(animWord(p.anim, es)).append('\n');
            sb.append("  ").append(es ? "duracion " : "duration ").append(p.dur).append(" ms\n");
            sb.append("  ").append(es ? "columnas " : "columns ").append(Math.max(1, p.cols)).append('\n');
            if (p.w >= 0) sb.append("  ").append(es ? "ancho " : "width ").append(p.w).append('\n');
            if (p.h >= 0) sb.append("  ").append(es ? "alto " : "height ").append(p.h).append('\n');
            sb.append("  ").append(es ? "fondo " : "background ").append(hex(p.bg)).append('\n');
            sb.append("  ").append(es ? "redondeo " : "radius ").append(p.radius).append('\n');
            if (!p.title.isEmpty()) sb.append("  ").append(es ? "titulo " : "title ").append(q(p.title)).append('\n');
            sb.append("  ").append(es ? "color texto " : "text color ").append(hex(p.tcolor)).append('\n');
            sb.append("  ").append(es ? "tamaño " : "size ").append(p.isize).append('\n');
            sb.append("  ").append(es ? "cerrar fuera " : "close outside ").append(p.outside ? (es ? "si" : "yes") : "no").append('\n');
            sb.append("  ").append(es ? "cerrar al elegir " : "close on select ").append(p.autoclose ? (es ? "si" : "yes") : "no").append('\n');
            for (Item it : p.items) {
                if (it.kind.equals("app")) { if (!it.name.isEmpty()) sb.append("  app ").append(q(it.name)).append('\n'); }
                else if (it.kind.equals("category")) { if (!it.name.isEmpty()) sb.append("  ").append(es ? "categoria " : "category ").append(q(it.name)).append('\n'); }
                else {
                    sb.append("  ").append(es ? "boton " : "button ").append(q(it.name.isEmpty() ? "·" : it.name));
                    if (!it.icon.isEmpty()) sb.append(es ? " icono " : " icon ").append(q(it.icon));
                    if (!it.target.isEmpty()) {
                        String w = it.tkind.equals("shell") ? "shell" : it.tkind.equals("app") ? "app" : it.tkind.equals("panel") ? "panel" : "macro";
                        sb.append(' ').append(w).append(' ').append(q(it.target));
                    }
                    sb.append('\n');
                }
            }
            sb.append(end).append("\n\n");
        }
        for (Btn b : buttons) {
            if (b.name.trim().isEmpty()) continue;
            sb.append(es ? "boton " : "button ").append(q(b.name)).append('\n');
            sb.append("  ").append(es ? "icono " : "icon ").append(q(b.icon)).append('\n');
            if (!b.label.isEmpty()) sb.append("  ").append(es ? "etiqueta " : "label ").append(q(b.label)).append('\n');
            if (!b.app.isEmpty()) sb.append("  app ").append(q(b.app)).append('\n');
            sb.append("  ").append(es ? "posicion " : "position ").append(dirWord(b.v, es)).append(' ').append(dirWord(b.hz, es)).append('\n');
            sb.append("  x ").append(Math.max(0, b.x)).append('\n');
            sb.append("  y ").append(Math.max(0, b.y)).append('\n');
            sb.append("  ").append(es ? "tamaño " : "size ").append(b.size).append('\n');
            sb.append("  ").append(es ? "opacidad " : "opacity ").append(b.opacity).append('\n');
            sb.append("  ").append(es ? "forma " : "shape ").append(b.shape.equals("square") ? (es ? "cuadrado" : "square")
                : b.shape.equals("round") ? (es ? "redondeado" : "round") : (es ? "circulo" : "circle")).append('\n');
            sb.append("  ").append(es ? "fondo " : "background ").append(hex(b.bg)).append('\n');
            sb.append("  ").append(es ? "color texto " : "text color ").append(hex(b.tcolor)).append('\n');
            sb.append("  ").append(es ? "visible " : "visible ").append(b.visible ? (es ? "si" : "yes") : "no").append('\n');
            sb.append(end).append("\n\n");
        }
        for (Rule r : rules) {
            if (!r.on) continue;
            if (r.macro) {
                if (r.name.trim().isEmpty()) continue;
                sb.append("macro ").append(q(r.name)).append('\n');
            } else {
                if (r.head.trim().isEmpty()) continue;
                sb.append(r.head.trim()).append('\n');
            }
            steps(sb, r.steps, 0, es);
            sb.append(end).append("\n\n");
        }
        return sb.toString();
    }
}
