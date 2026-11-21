package com.lexus2026.cdc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lenguaje de descripción del launcher. Un script de este lenguaje ES el launcher: qué elementos existen, dónde están,
 * cómo se ven, cómo aparecen y qué hacen. Se escribe en español o en inglés indistintamente.
 *
 * Estructura: elementos con «fin» (caja, texto, reloj, boton, icono, grilla, espacio), propiedades en líneas propias o en la
 * misma línea, y gestos («al tocar», «al mantener», «al doble toque», «al deslizar arriba») cuyo cuerpo son acciones del
 * lenguaje de scripts de siempre (abrir, poner, esperar, si, repetir, ejecutar, shell…).
 * Además se pueden mostrar, ocultar, alternar y cambiar elementos por nombre, con animación.
 */
final class UiLang {
    private UiLang() {}

    static final int D_WRAP = 0, D_FILL = 1, D_DP = 2, D_PCT = 3;

    static final class Dim {
        int mode;
        float v;
        Dim(int m, float v) { mode = m; this.v = v; }
    }

    static final class Node {
        String kind;                                   // box text clock button icon grid spacer
        int line;
        final Map<String, Object> p = new LinkedHashMap<>();
        final Map<String, String> handlers = new LinkedHashMap<>();   // evento → macro
        final List<Node> kids = new ArrayList<>();
    }

    static final class Cmd {
        static final int SHOW = 0, HIDE = 1, TOGGLE = 2, SET = 3;
        int type;
        String name, dir, anim;
        long dur = 260;
        Node patch;
    }

    static final class Scene {
        final List<Node> roots = new ArrayList<>();
        final List<Cmd> cmds = new ArrayList<>();
        final List<ScriptLang.Err> errors = new ArrayList<>();
        final Map<String, List<String>> macros = new LinkedHashMap<>();
        boolean hideDesk, hideDock;
        String initMacro;

        /** Macros que necesita el motor de scripts para ejecutar los gestos, la inicialización y los comandos de elementos. */
        String macroSource() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<String>> e : macros.entrySet()) {
                sb.append("macro \"").append(e.getKey()).append("\"\n");
                for (String l : e.getValue()) sb.append("  ").append(l).append('\n');
                sb.append("end\n\n");
            }
            for (int i = 0; i < cmds.size(); i++) sb.append("macro \"__uicmd_").append(i).append("\"\n  wait 1 ms\nend\n\n");
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ vocabulario

    private static final Map<String, String> KIND = new HashMap<>();
    private static final Map<String, String> ALIAS = new HashMap<>();
    private static final Set<String> FILLW = new HashSet<>(java.util.Arrays.asList("todo", "todos", "completo", "lleno", "fill", "match", "full", "llenar", "all", "parent", "padre"));
    private static final Set<String> WRAPW = new HashSet<>(java.util.Arrays.asList("auto", "wrap", "contenido", "ajustado", "automatico", "automatic"));

    private static void kind(String k, String words) { for (String w : words.split(",")) KIND.put(ScriptLang.norm(w), k); }

    private static void alias(String k, String words) { for (String w : words.split(",")) ALIAS.put(ScriptLang.norm(w.trim()), k); }

    static {
        kind("box", "caja,contenedor,grupo,box,container,group,bloque");
        kind("text", "texto,text");
        kind("clock", "reloj,clock");
        kind("icon", "icono,icon");
        kind("grid", "grilla,cuadricula,grid");
        kind("spacer", "espacio,spacer,hueco");
        alias("pos", "posicion,position,ancla,anchor,ubicacion");
        alias("x", "x,desplazamiento x,offset x");
        alias("y", "y,desplazamiento y,offset y");
        alias("w", "ancho,width");
        alias("h", "alto,height");
        alias("size", "tamaño,size,dimension");
        alias("bg", "fondo,background,bg,color fondo,bg color,background color");
        alias("grad", "degradado,gradient");
        alias("fg", "color,color texto,text color,tinte,tint");
        alias("radius", "redondeo,radius,esquinas,corners");
        alias("border", "borde,border,contorno,outline");
        alias("opacity", "opacidad,opacity");
        alias("margin", "margen,margin");
        alias("pad", "relleno,padding,espaciado interno");
        alias("shadow", "sombra,shadow,elevacion,elevation");
        alias("textsize", "tamaño texto,text size,fuente,font size,letra,tamaño letra");
        alias("bold", "negrita,bold");
        alias("align", "alineacion,align,alinear,alineado");
        alias("rot", "rotacion,rotation,rotar,rotate");
        alias("visible", "visible,visibilidad,visibility");
        alias("name", "nombre,name,id");
        alias("content", "contenido,content,etiqueta,label,leyenda,caption");
        alias("format", "formato,format");
        alias("layout", "disposicion,layout,orden,arreglo");
        alias("cols", "columnas,columns,cols");
        alias("gap", "espaciado,gap,spacing,separacion");
        alias("iconsize", "tamaño iconos,icon size,tamaño icono,iconos tamaño");
        alias("labels", "etiquetas,labels,nombres,names");
        alias("source", "muestra,show,source,fuente de datos,origen,apps de,mostrar apps,data");
        alias("app", "app,aplicacion,application");
        alias("weight", "peso,weight,proporcion");
    }

    // ------------------------------------------------------------------ parser

    private static final class Ctx {
        final Scene sc = new Scene();
        final List<Node> stack = new ArrayList<>();
        int hcount, line;
        // captura del cuerpo de un gesto
        List<String> body;
        String bodyName;
        int depth, bodyLine;
        final List<String> init = new ArrayList<>();
    }

    static Scene parse(String src, boolean es) {
        ScriptLang.ES = es;
        Ctx c = new Ctx();
        String[] lines = src == null ? new String[0] : src.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            c.line = i + 1;
            String raw = lines[i];
            String t = raw.trim();
            if (t.isEmpty() || ScriptLang.isComment(t)) continue;
            try {
                feed(c, t, raw);
            } catch (IllegalArgumentException e) {
                c.sc.errors.add(new ScriptLang.Err(c.line, e.getMessage()));
            }
        }
        if (c.body != null) c.sc.errors.add(new ScriptLang.Err(c.bodyLine, ScriptLang.m("Este gesto no tiene su «fin»", "This gesture is missing its 'end'")));
        if (!c.stack.isEmpty()) {
            Node n = c.stack.get(c.stack.size() - 1);
            c.sc.errors.add(new ScriptLang.Err(n.line, ScriptLang.m("Falta el «fin» de este elemento", "This element is missing its 'end'")));
        }
        if (!c.init.isEmpty()) {
            c.sc.initMacro = "__ui_init";
            c.sc.macros.put("__ui_init", c.init);
        }
        return c.sc;
    }

    private static IllegalArgumentException bad(String es, String en) { return new IllegalArgumentException(ScriptLang.m(es, en)); }

    private static boolean isContainer(Node n) { return n.kind.equals("box") || n.kind.equals("button"); }

    private static String kindOfTok(ScriptLang.Tok t) {
        if (t.q) return null;
        if ("button".equals(t.c)) return "button";
        return KIND.get(t.low);
    }

    private static void feed(Ctx c, String t, String raw) {
        List<ScriptLang.Tok> tk = ScriptLang.lex(t);
        if (tk.isEmpty()) return;
        ScriptLang.Tok f = tk.get(0);

        // ---- cuerpo de un gesto: líneas de acciones hasta el «fin» que lo cierra
        if (c.body != null) {
            String fc = f.c == null ? "" : f.c;
            if (fc.equals("end")) {
                if (c.depth == 0) { c.sc.macros.put(c.bodyName, c.body); c.body = null; return; }
                c.depth--;
            } else if (fc.equals("if") || fc.equals("while") || fc.equals("repeat")) {
                c.depth++;
            }
            c.body.add(translate(c, t, tk));
            return;
        }

        if (f.c != null && f.c.equals("end") && tk.size() == 1) {
            if (c.stack.isEmpty()) throw bad("«fin» sin un elemento abierto", "'end' without an open element");
            c.stack.remove(c.stack.size() - 1);
            return;
        }

        Node cur = c.stack.isEmpty() ? null : c.stack.get(c.stack.size() - 1);

        // ---- gestos
        if ("when".equals(f.c) && tk.size() > 1) {
            if (cur == null) throw bad("Los gestos van dentro de un elemento", "Gestures go inside an element");
            String ev = eventOf(tk);
            c.bodyName = "__ui_h" + (++c.hcount);
            c.body = new ArrayList<>();
            c.depth = 0;
            c.bodyLine = c.line;
            cur.handlers.put(ev, c.bodyName);
            return;
        }

        // ---- elemento nuevo (los de hoja no admiten hijos: ahí «texto» es una propiedad)
        String k = kindOfTok(f);
        boolean leaf = cur != null && !isContainer(cur);
        if (k != null) {
            if (leaf) throw bad("«" + f.raw + "» no puede ir dentro de un elemento de hoja; cerrá el anterior con «fin»",
                "'" + f.raw + "' cannot go inside a leaf element; close the previous one with 'end'");
            Node n = new Node();
            n.kind = k;
            n.line = c.line;
            boolean selfClosed = tk.size() > 1 && "end".equals(tk.get(tk.size() - 1).c);
            header(n, tk, selfClosed ? tk.size() - 1 : tk.size());
            if (cur == null) c.sc.roots.add(n); else cur.kids.add(n);
            if (!selfClosed) c.stack.add(n);
            return;
        }

        // ---- propiedades del elemento abierto
        if (cur != null) {
            props(cur, tk, 0);
            return;
        }

        // ---- nivel superior: ocultar piezas del launcher original, o acciones de inicio
        if ("hide".equals(f.c) && tk.size() >= 2) {
            String w = tk.get(1).low;
            if (w.equals("dock") || w.equals("barra") || w.equals("bar")) { c.sc.hideDock = true; return; }
            if (w.equals("escritorio") || w.equals("desktop") || w.equals("fondo") || w.equals("background") || w.equals("grilla") || w.equals("grid")) { c.sc.hideDesk = true; return; }
        }
        c.init.add(translate(c, t, tk));
    }

    private static String eventOf(List<ScriptLang.Tok> tk) {
        Set<String> cs = new HashSet<>();
        String dir = null;
        for (ScriptLang.Tok x : tk) {
            if (x.q || x.c == null) continue;
            cs.add(x.c);
            if (dir == null && (x.c.equals("left") || x.c.equals("right") || x.c.equals("up") || x.c.equals("down"))) dir = x.c;
        }
        if (cs.contains("swipe")) {
            if (dir == null) throw bad("Indicá la dirección: arriba, abajo, izquierda o derecha", "Specify the direction: up, down, left or right");
            return "swipe_" + dir;
        }
        if (cs.contains("double")) return "double";
        if (cs.contains("hold")) return "hold";
        if (cs.contains("tap")) return "tap";
        throw bad("Gesto desconocido. Usá: al tocar, al mantener, al doble toque, al deslizar arriba/abajo/izquierda/derecha",
            "Unknown gesture. Use: on tap, on hold, on double tap, on swipe up/down/left/right");
    }

    private static void header(Node n, List<ScriptLang.Tok> tk, int end) {
        int i = 1;
        // primer texto entre comillas: contenido (texto, reloj, botón, icono usa app) o nombre (resto)
        if (i < end && tk.get(i).q) {
            String s = tk.get(i).raw;
            if (n.kind.equals("text") || n.kind.equals("button")) n.p.put("content", s);
            else if (n.kind.equals("icon")) n.p.put("app", s);
            else if (n.kind.equals("clock")) n.p.put("format", s);
            else n.p.put("name", s);
            i++;
        }
        if (i < end) {
            // palabra suelta de disposición justo después: «caja fila», «caja columna»
            String lay = layoutWord(tk.get(i));
            if (lay != null) { n.p.put("layout", lay); i++; }
        }
        if (i < end) props(n, tk.subList(0, end), i);
    }

    private static String layoutWord(ScriptLang.Tok t) {
        if (t.q) return null;
        switch (t.low) {
            case "fila": case "row": case "horizontal": return "row";
            case "columna": case "column": case "vertical": return "col";
            case "apilado": case "stack": case "capas": case "layers": return "stack";
            default: return null;
        }
    }

    // ------------------------------------------------------------------ propiedades

    private static int aliasLen(List<ScriptLang.Tok> t, int i, String[] key) {
        if (t.get(i).q) return 0;
        for (int L = Math.min(3, t.size() - i); L >= 1; L--) {
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            for (int k = 0; k < L; k++) {
                ScriptLang.Tok x = t.get(i + k);
                if (x.q || x.isNum) { ok = false; break; }
                sb.append(k > 0 ? " " : "").append(x.low);
            }
            if (!ok) continue;
            String kk = ALIAS.get(sb.toString());
            if (kk != null) { key[0] = kk; return L; }
        }
        return 0;
    }

    static void props(Node n, List<ScriptLang.Tok> t, int from) {
        int i = from;
        String[] key = new String[1];
        while (i < t.size()) {
            ScriptLang.Tok x = t.get(i);
            if ("end".equals(x.c) && i == t.size() - 1) break;
            int len = aliasLen(t, i, key);
            if (len == 0) {
                String lay = layoutWord(x);
                if (lay != null) { n.p.put("layout", lay); i++; continue; }
                if (x.q && !n.p.containsKey("content")) { n.p.put("content", x.raw); i++; continue; }
                throw bad("Propiedad desconocida: «" + x.raw + "»", "Unknown property: '" + x.raw + "'");
            }
            int j = i + len;
            String[] k2 = new String[1];
            while (j < t.size() && aliasLen(t, j, k2) == 0 && !("end".equals(t.get(j).c) && j == t.size() - 1)) j++;
            put(n, key[0], new ArrayList<>(t.subList(i + len, j)), t.get(i).raw);
            i = j;
        }
    }

    private static Integer intOf(List<ScriptLang.Tok> v, String what) {
        for (ScriptLang.Tok k : v) if (k.isNum) return (int) Math.round(k.dval);
        throw bad("«" + what + "» necesita un número", "'" + what + "' needs a number");
    }

    private static Float floatOf(List<ScriptLang.Tok> v, String what) {
        for (ScriptLang.Tok k : v) if (k.isNum) return (float) k.dval;
        throw bad("«" + what + "» necesita un número", "'" + what + "' needs a number");
    }

    private static Dim dimOf(List<ScriptLang.Tok> v, String what) {
        for (int i = 0; i < v.size(); i++) {
            ScriptLang.Tok k = v.get(i);
            if (k.isNum) {
                boolean pct = i + 1 < v.size() && v.get(i + 1).raw.equals("%");
                return new Dim(pct ? D_PCT : D_DP, (float) k.dval);
            }
            if (!k.q && FILLW.contains(k.low)) return new Dim(D_FILL, 0);
            if (!k.q && WRAPW.contains(k.low)) return new Dim(D_WRAP, 0);
        }
        throw bad("«" + what + "» necesita un número, «todo» o «auto»", "'" + what + "' needs a number, 'fill' or 'auto'");
    }

    private static Integer colorOf(List<ScriptLang.Tok> v, String what) {
        Integer c = ScriptLang.colorOf(v);
        if (c == null) throw bad("Color inválido en «" + what + "» (ej: #1B1B3A o azul)", "Invalid color in '" + what + "' (e.g. #1B1B3A or blue)");
        return c;
    }

    private static void put(Node n, String key, List<ScriptLang.Tok> v, String word) {
        if (v.isEmpty() && !key.equals("bold")) throw bad("Falta el valor de «" + word + "»", "Missing value for '" + word + "'");
        switch (key) {
            case "pos": {
                String vv = null, hh = null;
                boolean center = false;
                for (ScriptLang.Tok k : v) {
                    if (k.q || k.c == null) continue;
                    if (k.c.equals("up")) vv = "top";
                    else if (k.c.equals("down")) vv = "bottom";
                    else if (k.c.equals("left")) hh = "left";
                    else if (k.c.equals("right")) hh = "right";
                    else if (k.c.equals("center")) center = true;
                }
                if (center) { if (vv == null && hh == null) { vv = "center"; hh = "center"; } else if (vv == null) vv = "center"; else if (hh == null) hh = "center"; }
                if (vv == null && hh == null) throw bad("Posición: arriba, abajo, izquierda, derecha o centro", "Position: top, bottom, left, right or center");
                n.p.put("pos", new String[]{vv == null ? "top" : vv, hh == null ? "left" : hh});
                break;
            }
            case "x": case "y": case "cols": case "gap": case "iconsize": case "opacity": case "shadow":
                n.p.put(key, intOf(v, word)); break;
            case "w": n.p.put("w", dimOf(v, word)); break;
            case "h": n.p.put("h", dimOf(v, word)); break;
            case "size": { Dim d = dimOf(v, word); n.p.put("w", d); n.p.put("h", d); break; }
            case "bg": n.p.put("bg", colorOf(v, word)); break;
            case "fg": n.p.put("fg", colorOf(v, word)); break;
            case "grad": {
                List<Integer> cs = new ArrayList<>();
                int ang = 270;
                for (ScriptLang.Tok k : v) {
                    if (k.q) continue;
                    if (k.color) cs.add(k.cval);
                    else if (ScriptLang.COLORS.containsKey(k.low)) cs.add(ScriptLang.COLORS.get(k.low));
                    else if ("left".equals(k.c) || "right".equals(k.c)) ang = k.c.equals("left") ? 180 : 0;
                    else if ("up".equals(k.c)) ang = 90;
                    else if ("down".equals(k.c)) ang = 270;
                }
                if (cs.size() < 2) throw bad("El degradado necesita dos colores", "A gradient needs two colors");
                n.p.put("grad", new int[]{cs.get(0), cs.get(1), ang});
                break;
            }
            case "radius": {
                boolean round = false;
                for (ScriptLang.Tok k : v) if (!k.q && (k.low.equals("circulo") || k.low.equals("circle") || k.low.equals("redondo") || k.low.equals("round"))) round = true;
                n.p.put("radius", round ? -1 : intOf(v, word));
                break;
            }
            case "border": {
                Integer col = ScriptLang.colorOf(v);
                int w = 1;
                for (ScriptLang.Tok k : v) if (k.isNum) w = (int) Math.round(k.dval);
                n.p.put("border", new int[]{col == null ? 0xFFFFFFFF : col, w});
                break;
            }
            case "margin": case "pad": {
                List<Integer> nums = new ArrayList<>();
                for (ScriptLang.Tok k : v) if (k.isNum) nums.add((int) Math.round(k.dval));
                if (nums.isEmpty()) throw bad("«" + word + "» necesita números", "'" + word + "' needs numbers");
                int a = nums.get(0), b = nums.size() > 1 ? nums.get(1) : a, cc = nums.size() > 2 ? nums.get(2) : a, d = nums.size() > 3 ? nums.get(3) : b;
                // un número: todos; dos: horizontal y vertical; cuatro: izquierda, arriba, derecha, abajo
                n.p.put(key, nums.size() == 2 ? new int[]{a, b, a, b} : new int[]{a, b, cc, d});
                break;
            }
            case "textsize": n.p.put("textsize", floatOf(v, word)); break;
            case "rot": n.p.put("rot", floatOf(v, word)); break;
            case "weight": n.p.put("weight", floatOf(v, word)); break;
            case "bold": { Integer b = ScriptLang.boolOf(v); n.p.put("bold", b == null || b == 1); break; }
            case "visible": { Integer b = ScriptLang.boolOf(v); if (b == null) throw bad("«visible» necesita sí o no", "'visible' needs yes or no"); n.p.put("visible", b == 1); break; }
            case "labels": { Integer b = ScriptLang.boolOf(v); if (b == null) throw bad("«etiquetas» necesita sí o no", "'labels' needs yes or no"); n.p.put("labels", b == 1); break; }
            case "align": {
                String a = null;
                for (ScriptLang.Tok k : v) if (k.c != null && (k.c.equals("left") || k.c.equals("right") || k.c.equals("center"))) a = k.c;
                if (a == null) throw bad("Alineación: izquierda, centro o derecha", "Alignment: left, center or right");
                n.p.put("align", a);
                break;
            }
            case "name": case "content": case "format": case "app": {
                String s = ScriptLang.firstQuoted(v);
                n.p.put(key, s != null ? s : ScriptLang.joinRaw(v));
                break;
            }
            case "layout": {
                String l = null;
                for (ScriptLang.Tok k : v) if (l == null) l = layoutWord(k);
                if (l == null) throw bad("Disposición: fila, columna o apilado", "Layout: row, column or stack");
                n.p.put("layout", l);
                break;
            }
            case "source": {
                String type = null, name = "";
                for (ScriptLang.Tok k : v) {
                    if (k.q) { name = k.raw; continue; }
                    if ("category".equals(k.c)) type = "cat";
                    else if (type == null && (k.low.equals("apps") || k.low.equals("todas") || k.low.equals("todo") || k.low.equals("todos") || k.low.equals("all") || "all".equals(k.c))) type = "all";
                    else if (type == null && (k.low.equals("fijadas") || k.low.equals("fijados") || k.low.equals("pinned") || k.low.equals("escritorio") || k.low.equals("desktop"))) type = "pinned";
                    else if (type == null && (k.low.equals("nuevas") || k.low.equals("nuevos") || k.low.equals("new") || k.low.equals("pendientes") || k.low.equals("pending"))) type = "new";
                }
                if (type == null) throw bad("Origen: apps, fijadas, nuevas o categoria \"Nombre\"", "Source: apps, pinned, new or category \"Name\"");
                if (type.equals("cat") && name.isEmpty()) throw bad("Falta el nombre de la categoría", "Missing the category name");
                n.p.put("source", new String[]{type, name});
                break;
            }
            default: throw bad("Propiedad sin soporte: " + word, "Unsupported property: " + word);
        }
    }

    // ------------------------------------------------------------------ acciones sobre elementos

    private static final Set<String> ELEM = new HashSet<>(java.util.Arrays.asList("elemento", "element", "item", "widget"));

    /**
     * Convierte una línea de acción del cuerpo de un gesto. Las acciones sobre elementos de la pantalla
     * («mostrar elemento "x" desde abajo con rebote», «cambiar elemento "x" fondo rojo») se sustituyen por una llamada
     * a una macro interna que el intérprete reconoce; el resto va tal cual al lenguaje de scripts.
     */
    private static String translate(Ctx c, String t, List<ScriptLang.Tok> tk) {
        if (tk.size() >= 3 && tk.get(1).low != null && ELEM.contains(tk.get(1).low) && tk.get(2).q) {
            String v = tk.get(0).c == null ? "" : tk.get(0).c;
            Cmd cm = new Cmd();
            cm.name = ScriptLang.norm(tk.get(2).raw);
            switch (v) {
                case "show": cm.type = Cmd.SHOW; break;
                case "hide": cm.type = Cmd.HIDE; break;
                case "toggle": cm.type = Cmd.TOGGLE; break;
                case "set": case "put": cm.type = Cmd.SET; break;
                default: throw bad("Con «elemento» podés usar: mostrar, ocultar, alternar o cambiar", "With 'element' you can use: show, hide, toggle or set");
            }
            if (cm.type == Cmd.SET) {
                cm.patch = new Node();
                cm.patch.kind = "patch";
                props(cm.patch, tk, 3);
            } else {
                for (int i = 3; i < tk.size(); i++) {
                    ScriptLang.Tok k = tk.get(i);
                    if (k.q) continue;
                    if (k.c != null && (k.c.equals("left") || k.c.equals("right") || k.c.equals("up") || k.c.equals("down"))) cm.dir = k.c;
                    String an = ScriptLang.ANIMS.get(k.low);
                    if (an != null) cm.anim = an;
                }
                long ms = ScriptLang.amountAfter(tk, 3, 1);
                if (ms >= 0) cm.dur = Math.min(ms, 5000);
            }
            c.sc.cmds.add(cm);
            return "run \"__uicmd_" + (c.sc.cmds.size() - 1) + "\"";
        }
        return t;
    }
}
