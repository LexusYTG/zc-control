package com.lexus2026.cdc;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ScriptLang {
    private ScriptLang() {}

    static final int A_ACT = 0, A_JMP = 1, A_JF = 2, A_WAIT = 3, A_RINIT = 4, A_RTEST = 5, A_CALL = 6, A_STOP = 7, A_BLOCK = 8;

    static boolean ES = true;

    static String m(String es, String en) { return ES ? es : en; }

    static final class Tok {
        String raw, low, c;
        boolean q, isNum, time, color;
        double dval;
        int cval;
    }

    static final class Err {
        final int line;
        final String msg;
        Err(int l, String m) { line = l; msg = m; }
    }

    static final class Act {
        final String k;
        final Object[] p;
        Act(String k, Object... p) { this.k = k; this.p = p; }
    }

    static final class Ins {
        final int op;
        Object a, b;
        final int line;
        Ins(int op, Object a, Object b, int line) { this.op = op; this.a = a; this.b = b; this.line = line; }
    }

    static final class Rule {
        String ev, arg;
        int n = -1, hh = -1, mm, line;
        long ms, period;
        List<Ins> code = new ArrayList<>();
        String lastKey = "";
    }

    static final class PItem {
        String label = "", icon = "", app, macro, shell, panel, category, fromCategory;
    }

    static final class PanelDef {
        String name, from = "auto", anim = "slide", title = "";
        int dur = 260, cols = 4, w = -1, h = -1, bg = 0xF0121228, radius = 26, tcolor = 0xFFFFFFFF, isize = 52;
        boolean outside = true, autoclose = true;
        List<PItem> items = new ArrayList<>();
    }

    static final class ButtonDef {
        String name, icon = "●", label = "", app, pos = "down right", shape = "circle";
        int x, y, size = 48, bg = 0xFF3D7BFF, opacity = 100, radius = -1, tcolor = 0xFFFFFFFF;
        boolean visible = true;
    }

    static final class Program {
        final List<Rule> rules = new ArrayList<>();
        final Map<String, List<Ins>> macros = new HashMap<>();
        final Map<String, PanelDef> panels = new LinkedHashMap<>();
        final Map<String, ButtonDef> buttons = new LinkedHashMap<>();
        final List<Err> errors = new ArrayList<>();
    }

    static final Map<String, String> SYN = new HashMap<>();
    static final Set<String> FILL = new HashSet<>(Arrays.asList("el", "la", "los", "las", "un", "una", "the", "an", "of", "de", "del", "mi", "my", "que", "that", "yo", "i", "me", "se", "lo"));
    static final Set<String> SKIPC = new HashSet<>(Arrays.asList("open", "close", "tap", "hold", "installed", "removed", "when", "times", "to", "in", "from", "and", "or", "not", "with", "for"));
    static final Map<String, String> SETKEY = new HashMap<>();
    static final Map<String, String> PROPK = new HashMap<>();
    static final Map<String, Integer> COLORS = new HashMap<>();
    static final Map<String, String> ANIMS = new HashMap<>();
    static final Set<String> YES = new HashSet<>(Arrays.asList("si", "yes", "on", "true", "activado", "activada", "activar", "encendido", "mostrar", "visible", "show", "enable", "enabled", "1", "habilitado"));
    static final Set<String> NO = new HashSet<>(Arrays.asList("no", "off", "false", "desactivado", "desactivada", "desactivar", "apagado", "ocultar", "oculto", "oculta", "hide", "hidden", "disable", "disabled", "0", "deshabilitado"));
    static final Set<String> COLOR_KEYS = new HashSet<>(Arrays.asList(LauncherSettings.BG_COLOR, LauncherSettings.ACCENT, LauncherSettings.DRAWER_COLOR, LauncherSettings.DOCK_COLOR, LauncherSettings.TEXT_COLOR));
    static final Set<String> BOOL_KEYS = new HashSet<>(Arrays.asList(LauncherSettings.DESKTOP, LauncherSettings.DOCK_SHOW, LauncherSettings.LABELS, LauncherSettings.FIT));
    static final Map<String, Map<String, Integer>> ENUMS = new HashMap<>();

    static final String[] SYN_ROWS = {
        "open=abrir,abre,abro,abri,open,opens,launch,lanzar,lanza",
        "close=cerrar,cierra,cierro,close,closes,dismiss",
        "hide=ocultar,oculta,oculto,esconder,esconde,hide",
        "show=mostrar,muestra,muestro,show,display",
        "toggle=alternar,alterna,toggle,conmutar,invertir",
        "set=poner,pon,pongo,set,cambiar,cambia,change,ajustar,ajusta,establecer,establece",
        "put=colocar,coloca,place,put,mover,mueve,move,asignar,asigna,assign",
        "add=sumar,suma,aumentar,aumenta,subir,sube,incrementar,incrementa,increase,add,agregar,agrega,anadir,anade,raise",
        "sub=restar,resta,reducir,reduce,bajar,baja,disminuir,disminuye,decrease,subtract,lower",
        "reset=restablecer,restablece,reset,resetear,resetea",
        "pin=fijar,fija,pin,anclar,ancla",
        "unpin=desfijar,desfija,unpin,desanclar",
        "remove=quitar,quita,remove,sacar,saca,borrar,borra,delete,eliminar,elimina,clear,vaciar,vacia",
        "create=crear,crea,create",
        "wait=esperar,espera,wait,pausa,pause,sleep,aguardar",
        "toast=aviso,avisar,avisa,mensaje,message,toast,decir,say,notificar,notify,anunciar",
        "vibrate=vibrar,vibra,vibrate",
        "shell=shell,comando,command,terminal,cmd,consola",
        "stop=detener,detente,stop,salir,exit,return,parar",
        "block=bloquear,bloquea,block,cancelar,cancela,cancel,impedir,prevent,suprimir",
        "var=variable,var,let,guardar,guarda,save,recordar,remember",
        "if=si,if",
        "else=sino,else,contrario,otherwise",
        "end=fin,end,endif,finsi",
        "then=entonces,then",
        "do=hacer,do",
        "while=mientras,while",
        "repeat=repetir,repite,repeat,bucle,loop",
        "times=veces,vez,times,time",
        "when=cuando,when,on,al,whenever",
        "every=cada,every",
        "boot=arranca,arranque,arrancar,startup,boot,start,starts,starting,empieza,comienza",
        "resume=reanuda,reanudo,vuelvo,vuelve,resume,resumes,regreso,regresa",
        "home=home,casa,inicio",
        "center=centro,central,center,middle",
        "button=boton,botones,button,buttons,btn,flotante,floating",
        "tap=tocar,toca,toco,tocas,toque,toques,tap,taps,pulsar,pulsa,pulso,presionar,presiona,presiono,click,clic,press,presses",
        "hold=mantener,mantengo,mantiene,mantienes,hold,holds,sostener,sostengo,larga,largo,long",
        "double=doble,dobles,double",
        "swipe=deslizar,desliza,deslizo,deslizas,swipe,swipes,arrastrar,arrastra,arrastro,drag,drags",
        "edge=borde,bordes,edge,edges,margen",
        "app=app,apps,aplicacion,aplicaciones,application,applications",
        "drawer=cajon,cajones,drawer",
        "category=categoria,categorias,category,categories,carpeta,carpetas,folder,folders",
        "newapps=nuevas,nuevos,pending,pendientes",
        "panel=panel,paneles,panels,bandeja,tray",
        "macro=macro,macros,rutina,rutinas,routine,funcion,function,define,definir",
        "background=fondo,escritorio,desktop,background,wallpaper,vacio,empty",
        "installed=instalada,instalado,instala,instalan,installed,install",
        "removed=desinstalada,desinstalado,desinstala,desinstalan,uninstalled,uninstall,removed",
        "run=ejecutar,ejecuta,run,llamar,llama,call,invocar,invoca,correr",
        "sec=segundos,segundo,seg,s,seconds,second,sec,secs",
        "min=minutos,minuto,min,minutes,minute,mins",
        "hour=horas,hora,h,hr,hrs,hours,hour",
        "ms=milisegundos,milisegundo,ms,millis,milliseconds",
        "left=izquierda,izquierdo,izq,left",
        "right=derecha,derecho,der,right",
        "up=arriba,superior,top,up,encima",
        "down=abajo,inferior,bottom,down,debajo",
        "in=en,in,into,dentro",
        "to=a,to,hacia,como",
        "from=de,desde,from,del",
        "for=durante,for,por",
        "with=con,with",
        "and=y,and,e",
        "or=o,or,u",
        "not=no,not",
        "all=todo,todos,todas,all,everything"
    };

    static final String[] SET_ROWS = {
        LauncherSettings.RING1 + "|primer radial|anillo 1|radial 1|ring 1|first ring",
        LauncherSettings.RING2 + "|segundo radial|anillo 2|radial 2|ring 2|second ring",
        LauncherSettings.ARC + "|apertura|angulo arco|arco|arc|arc angle|spread",
        LauncherSettings.BUBBLE + "|tamaño burbuja|tamaño burbujas|burbuja|burbujas|bubble size|bubble|bubbles",
        LauncherSettings.GAP + "|separacion|espacio|gap|spacing",
        LauncherSettings.STAGE2 + "|etapa 2|segunda etapa|stage 2|second stage",
        LauncherSettings.SENS + "|sensibilidad|sensitivity",
        LauncherSettings.HOLD + "|tiempo mantener|retardo radial|tiempo radial|hold time|hold delay|radial delay",
        LauncherSettings.ANIM + "|animacion|duracion animacion|velocidad animacion|animation|animation time|animation speed",
        LauncherSettings.SHAPE + "|forma|forma burbuja|shape|bubble shape",
        LauncherSettings.DOCK_ICON + "|icono dock|tamaño icono dock|dock icon|dock icon size",
        LauncherSettings.APP_ICON + "|icono app|icono apps|tamaño icono|tamaño iconos|app icon|icon size|icons size",
        LauncherSettings.BG + "|tipo fondo|fondo tipo|background type|background mode",
        LauncherSettings.DIM + "|oscurecer|oscurecimiento|atenuacion|dim|dimming",
        LauncherSettings.BG_COLOR + "|fondo|color fondo|fondo color|background|background color|bg color|wallpaper color",
        LauncherSettings.ACCENT + "|acento|color acento|accent|accent color",
        LauncherSettings.NEW_MODE + "|apps nuevas|modo nuevas|new apps|new apps mode",
        LauncherSettings.DOCK_H + "|alto dock|altura dock|alto barra|dock height|bar height",
        LauncherSettings.DOCK_W + "|ancho dock|ancho barra|dock width|bar width",
        LauncherSettings.R1 + "|radio 1|radio primer arco|radius 1|first radius",
        LauncherSettings.R2GAP + "|separacion arcos|distancia arcos|arc gap|ring gap",
        LauncherSettings.TRACK + "|grosor arco|riel|track|track width|arc thickness",
        LauncherSettings.ROT + "|rotacion|rotar radial|rotation|rotate radial",
        LauncherSettings.FIT + "|ajuste automatico|encajar|fit|auto fit",
        LauncherSettings.RAD_X + "|radial x|desplazamiento radial x|radial offset x",
        LauncherSettings.RAD_Y + "|radial y|desplazamiento radial y|radial offset y",
        LauncherSettings.DOCK_POS + "|posicion dock|ubicacion dock|dock position|dock location",
        LauncherSettings.DOCK_X + "|dock x|desplazamiento dock x|dock offset x",
        LauncherSettings.DOCK_Y + "|dock y|desplazamiento dock y|dock offset y|elevacion dock",
        LauncherSettings.DESKTOP + "|modo escritorio|desktop mode|landscape mode",
        LauncherSettings.DESK_COLS + "|columnas escritorio|columnas|desktop columns|home columns|columns",
        LauncherSettings.DRAWER_COLS + "|columnas cajon|columnas apps|drawer columns",
        LauncherSettings.DRAWER_H + "|alto cajon|altura cajon|drawer height",
        LauncherSettings.DRAWER_COLOR + "|color cajon|drawer color",
        LauncherSettings.DRAWER_RADIUS + "|redondeo cajon|radio cajon|drawer radius|drawer corner",
        LauncherSettings.DOCK_COLOR + "|color dock|color barra|dock color|bar color",
        LauncherSettings.DOCK_RADIUS + "|redondeo dock|radio dock|dock radius|dock corner",
        LauncherSettings.LABELS + "|etiquetas|nombres apps|nombres|labels|app names",
        LauncherSettings.LABEL_SIZE + "|tamaño etiquetas|tamaño texto|label size|text size",
        LauncherSettings.HOME_SIZE + "|tamaño boton central|boton central tamaño|tamaño home|center button size|home size",
        LauncherSettings.DOCK_SHOW + "|mostrar dock|dock visible|barra visible|dock|show dock",
        LauncherSettings.TEXT_COLOR + "|color texto|text color",
        LauncherSettings.DESK_GAP + "|espaciado escritorio|separacion filas|row spacing|desktop spacing",
        LauncherSettings.DRAWER_FROM + "|animacion cajon|cajon desde|direccion cajon|drawer animation|drawer from|drawer direction"
    };

    static final String[] PROP_ROWS = {
        "from|desde|from|origen|direccion|direction|entrada|entra desde|enters from",
        "anim|animacion|animation|estilo|style|efecto|effect",
        "dur|duracion|duration|velocidad|speed|tiempo animacion|anim time",
        "cols|columnas|columns|cols",
        "h|alto|altura|height",
        "w|ancho|width",
        "bg|fondo|background|color|color fondo|background color|bg|bg color",
        "radius|redondeo|radio|radius|corner|esquinas|corners|bordes redondeados",
        "title|titulo|title",
        "tcolor|color texto|text color|texto color",
        "size|tamaño icono|tamaño iconos|icon size|tamaño|size",
        "outside|cerrar fuera|close outside|cerrar al tocar fuera|cerrar tocando fuera|close on outside tap|tap outside closes|toque fuera cierra",
        "autoclose|cerrar al elegir|close on select|cerrar al tocar item|cerrar al abrir app|close on open|autocerrar",
        "pos|posicion|position|ubicacion|anchor|ancla|lugar|place",
        "icon|icono|icon|simbolo|emoji",
        "x|x|desplazamiento x|offset x",
        "y|y|desplazamiento y|offset y",
        "shape|forma|shape",
        "opacity|opacidad|opacity",
        "visible|visible|visibilidad",
        "label|etiqueta|label|texto|text",
        "app|app|aplicacion|application"
    };

    static {
        for (String row : SYN_ROWS) {
            int e = row.indexOf('=');
            String c = row.substring(0, e);
            for (String w : row.substring(e + 1).split(",")) SYN.put(norm(w), c);
        }
        for (String row : SET_ROWS) {
            String[] p = row.split("\\|");
            for (int i = 1; i < p.length; i++) SETKEY.put(norm(p[i]), p[0]);
        }
        for (String row : PROP_ROWS) {
            String[] p = row.split("\\|");
            for (int i = 1; i < p.length; i++) PROPK.put(norm(p[i]), p[0]);
        }
        String[] cn = {"rojo,red=E53935", "azul,blue=3D7BFF", "verde,green=43A047", "negro,black=000000", "blanco,white=FFFFFF",
            "amarillo,yellow=FFD600", "naranja,orange=FB8C00", "violeta,morado,purple,violet=8E24AA", "rosa,pink=EC407A",
            "gris,gray,grey=757575", "cian,cyan,celeste=00BCD4"};
        for (String r : cn) {
            int e = r.indexOf('=');
            int v = 0xFF000000 | Integer.parseInt(r.substring(e + 1), 16);
            for (String w : r.substring(0, e).split(",")) COLORS.put(w, v);
        }
        COLORS.put("transparente", 0);
        COLORS.put("transparent", 0);
        for (String r : new String[]{"deslizar,deslizamiento,deslizante,slide,sliding=slide", "zoom,escala,scale=zoom",
            "fundido,fade,desvanecer,desvanecido=fade", "rebote,rebotar,bounce,elastico,elastic,spring=bounce", "ninguna,none,sin=none"}) {
            int e = r.indexOf('=');
            for (String w : r.substring(0, e).split(",")) ANIMS.put(w, r.substring(e + 1));
        }
        en(LauncherSettings.SHAPE, "circulo,circle=0", "cuadrado suave,rounded,redondeado,suave=1", "cuadrado,square=2", "rombo,diamond=3");
        en(LauncherSettings.BG, "sistema,system=0", "solido,solid,color=1");
        en(LauncherSettings.NEW_MODE, "sueltas,sueltos,loose=0", "carpeta,folder=1");
        en(LauncherSettings.DOCK_POS, "centro,center=0", "izquierda,left=1", "derecha,right=2");
        en(LauncherSettings.DRAWER_FROM, "zoom,escala,scale,abajo,bottom,down=0", "izquierda,left=1", "derecha,right=2", "arriba,top,up=3", "fundido,fade=4");
    }

    private static void en(String key, String... rows) {
        Map<String, Integer> mm = new HashMap<>();
        for (String r : rows) {
            int e = r.indexOf('=');
            int v = Integer.parseInt(r.substring(e + 1));
            for (String w : r.substring(0, e).split(",")) mm.put(w, v);
        }
        ENUMS.put(key, mm);
    }

    static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char ch = n.charAt(i);
            if (Character.getType(ch) != Character.NON_SPACING_MARK) sb.append(ch);
        }
        return sb.toString();
    }

    static boolean isComment(String s) {
        return s.startsWith("//") || s.startsWith("--") || s.startsWith("# ") || s.equals("#");
    }

    static Integer parseHex(String h) {
        try {
            if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            if (h.length() == 6) return 0xFF000000 | (int) Long.parseLong(h, 16);
            if (h.length() == 8) return (int) Long.parseLong(h, 16);
        } catch (NumberFormatException e) { }
        return null;
    }

    static Tok word(String w) {
        Tok t = new Tok();
        t.raw = w;
        t.low = norm(w);
        t.c = SYN.get(t.low);
        if (w.matches("\\d{1,2}:\\d{2}")) t.time = true;
        if (w.startsWith("#") && w.length() > 1 && w.substring(1).matches("[0-9a-fA-F]{3,8}")) {
            Integer cv = parseHex(w.substring(1));
            if (cv != null) { t.color = true; t.cval = cv; }
        }
        return t;
    }

    static Tok numTok(String s, double v) {
        Tok t = new Tok();
        t.raw = s;
        t.low = s;
        t.isNum = true;
        t.dval = v;
        return t;
    }

    static void splitWord(List<Tok> out, String w) {
        while (w.length() > 0 && ",;:".indexOf(w.charAt(w.length() - 1)) >= 0) w = w.substring(0, w.length() - 1);
        if (w.isEmpty()) return;
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("^(-?\\d+(?:[.,]\\d+)?)([\\p{L}%]*)$").matcher(w);
        if (mt.matches()) {
            out.add(numTok(mt.group(1), Double.parseDouble(mt.group(1).replace(',', '.'))));
            if (!mt.group(2).isEmpty()) out.add(word(mt.group(2)));
            return;
        }
        out.add(word(w));
    }

    static List<Tok> lex(String line) {
        List<Tok> out = new ArrayList<>();
        int n = line.length(), i = 0;
        while (i < n) {
            char ch = line.charAt(i);
            if (Character.isWhitespace(ch)) { i++; continue; }
            if (ch == '"' || ch == '\'' || ch == '\u201C' || ch == '\u00AB') {
                char close = ch == '\u201C' ? '\u201D' : ch == '\u00AB' ? '\u00BB' : ch;
                StringBuilder sb = new StringBuilder();
                int j = i + 1;
                while (j < n && line.charAt(j) != close) {
                    char x = line.charAt(j);
                    if (x == '\\' && j + 1 < n) {
                        char y = line.charAt(j + 1);
                        sb.append(y == 'n' ? '\n' : y);
                        j += 2;
                    } else { sb.append(x); j++; }
                }
                Tok t = new Tok();
                t.raw = sb.toString();
                t.low = norm(t.raw);
                t.q = true;
                out.add(t);
                i = Math.min(n, j + 1);
                continue;
            }
            if ("=<>!".indexOf(ch) >= 0) {
                int j = i;
                while (j < n && "=<>!".indexOf(line.charAt(j)) >= 0) j++;
                out.add(word(line.substring(i, j)));
                i = j;
                continue;
            }
            int j = i;
            while (j < n && !Character.isWhitespace(line.charAt(j)) && "=<>!\"\u201C\u00AB".indexOf(line.charAt(j)) < 0) j++;
            splitWord(out, line.substring(i, j));
            i = j;
        }
        return out;
    }

    static Integer colorOf(List<Tok> v) {
        for (Tok k : v) {
            if (k.q) continue;
            if (k.color) return k.cval;
            Integer c = COLORS.get(k.low);
            if (c != null) return c;
        }
        return null;
    }

    static Integer boolOf(List<Tok> v) {
        for (Tok k : v) {
            if (YES.contains(k.low)) return 1;
            if (NO.contains(k.low)) return 0;
        }
        return null;
    }

    static Integer enumOf(String key, List<Tok> v) {
        Map<String, Integer> mm = ENUMS.get(key);
        if (mm == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Tok k : v) sb.append(sb.length() > 0 ? " " : "").append(k.low);
        Integer r = mm.get(sb.toString());
        if (r != null) return r;
        for (int i = v.size() - 1; i >= 0; i--) {
            r = mm.get(v.get(i).low);
            if (r != null) return r;
        }
        return null;
    }

    static String dirOf(List<Tok> v) {
        for (Tok k : v) {
            if (k.q || k.c == null) continue;
            if (k.c.equals("left") || k.c.equals("right") || k.c.equals("up") || k.c.equals("down")) return k.c;
            if (k.c.equals("center")) return "center";
        }
        return null;
    }

    static String edgeOf(String d) {
        if (d == null) return null;
        switch (d) {
            case "up": return "top";
            case "down": return "bottom";
            default: return d.equals("left") || d.equals("right") ? d : null;
        }
    }

    static long unitMs(Tok u, long def) {
        if (u == null || u.c == null) return def;
        switch (u.c) {
            case "ms": return 1;
            case "sec": return 1000;
            case "min": return 60000;
            case "hour": return 3600000;
            default: return def;
        }
    }

    static long amountAfter(List<Tok> t, int from, long defMult) {
        for (int k = from; k < t.size(); k++) {
            if (t.get(k).isNum) {
                Tok u = k + 1 < t.size() ? t.get(k + 1) : null;
                return (long) (t.get(k).dval * unitMs(u, defMult));
            }
        }
        return -1;
    }

    static List<Tok> sub(List<Tok> t, int a, int b) {
        return a >= b ? new ArrayList<Tok>() : new ArrayList<>(t.subList(a, Math.min(b, t.size())));
    }

    static String joinRaw(List<Tok> t) {
        StringBuilder sb = new StringBuilder();
        for (Tok k : t) sb.append(sb.length() > 0 ? " " : "").append(k.raw);
        return sb.toString();
    }

    static String firstQuoted(List<Tok> t) {
        for (Tok k : t) if (k.q) return k.raw;
        return null;
    }

    static String bareAfter(List<Tok> t, String noun) {
        int idx = -1;
        for (int i = 0; i < t.size(); i++) if (!t.get(i).q && noun.equals(t.get(i).c)) { idx = i; break; }
        if (idx < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = idx + 1; i < t.size(); i++) {
            Tok k = t.get(i);
            if (k.q) continue;
            if (FILL.contains(k.low)) continue;
            if (k.c != null && SKIPC.contains(k.c)) continue;
            sb.append(sb.length() > 0 ? " " : "").append(k.raw);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    static String nameFor(List<Tok> t, String noun) {
        String q = firstQuoted(t);
        return q != null ? q : bareAfter(t, noun);
    }

    static Object[] matchPhrase(List<Tok> t, int from, Map<String, String> map) {
        List<Integer> pos = new ArrayList<>();
        List<String> words = new ArrayList<>();
        for (int i = from; i < t.size() && words.size() < 6; i++) {
            Tok k = t.get(i);
            if (k.q || FILL.contains(k.low)) continue;
            pos.add(i);
            words.add(k.low);
        }
        for (int len = words.size(); len >= 1; len--) {
            String ph = String.join(" ", words.subList(0, len));
            String key = map.get(ph);
            if (key != null) return new Object[]{key, pos.get(len - 1) + 1};
        }
        return null;
    }

    static List<Tok> valueTail(List<Tok> t, int from) {
        int i = from;
        while (i < t.size()) {
            Tok k = t.get(i);
            if (!k.q && (k.low.equals("=") || "to".equals(k.c) || k.low.equals("en") || k.low.equals("in") || k.low.equals("as"))) i++;
            else break;
        }
        return sub(t, i, t.size());
    }

    static void parse(String src, Program pg) {
        Cx cx = new Cx(pg);
        String[] lines = src.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i].trim();
            if (ln.isEmpty() || isComment(ln)) continue;
            cx.line = i + 1;
            List<Tok> t = lex(ln);
            if (t.isEmpty()) continue;
            try { cx.feed(t); } catch (RuntimeException e) { cx.err(e.getMessage() == null ? "error" : e.getMessage()); }
        }
        cx.finish();
    }

    static final class Blk {
        final String kind;
        int jf = -1, start;
        final List<Integer> ends = new ArrayList<>();
        Blk(String k) { kind = k; }
    }

    static final class Cx {
        final Program pg;
        int mode, line, slot;
        List<Ins> code;
        PanelDef pd;
        ButtonDef bd;
        final List<Blk> blks = new ArrayList<>();
        final List<Object[]> calls = new ArrayList<>();

        Cx(Program p) { pg = p; }

        RuntimeException bad(String m) { return new RuntimeException(m); }

        void err(String m) { pg.errors.add(new Err(line, m)); }

        int emit(int op, Object a, Object b) {
            code.add(new Ins(op, a, b, line));
            return code.size() - 1;
        }

        void act(String k, Object... p) { emit(A_ACT, new Act(k, p), null); }

        void finish() {
            if (mode != 0) err(m("Falta «fin» al final del bloque", "Missing 'end' at the end of the block"));
            for (Object[] c : calls) {
                if (!pg.macros.containsKey((String) c[0]))
                    pg.errors.add(new Err((Integer) c[1], m("No existe la macro «", "Macro not found: '") + c[0] + (ES ? "»" : "'")));
            }
        }

        void feed(List<Tok> t) {
            Tok f = t.get(0);
            if (mode == 0) { root(t); return; }
            if (mode == 3 || mode == 4) {
                if ("end".equals(f.c)) { mode = 0; pd = null; bd = null; return; }
                props(t);
                return;
            }
            stmt(t);
        }

        void root(List<Tok> t) {
            Tok f = t.get(0);
            boolean hasTime = false;
            for (Tok k : t) if (k.time) hasTime = true;
            boolean trig = "when".equals(f.c) || "every".equals(f.c) || "if".equals(f.c)
                || (hasTime && (f.low.equals("a") || f.low.equals("at") || f.low.equals("@")));
            if (trig) {
                Rule r = trigger(t);
                r.line = line;
                pg.rules.add(r);
                code = r.code;
                blks.clear();
                mode = 1;
                return;
            }
            if ("macro".equals(f.c)) {
                String nm = firstQuoted(t);
                if (nm == null && t.size() > 1) nm = joinRaw(sub(t, 1, t.size()));
                if (nm == null || nm.isEmpty()) throw bad(m("La macro necesita un nombre", "The macro needs a name"));
                code = new ArrayList<>();
                pg.macros.put(norm(nm), code);
                blks.clear();
                mode = 2;
                return;
            }
            if ("panel".equals(f.c)) {
                String nm = nameFor(t, "panel");
                if (nm == null) throw bad(m("El panel necesita un nombre", "The panel needs a name"));
                pd = new PanelDef();
                pd.name = nm;
                pg.panels.put(norm(nm), pd);
                mode = 3;
                return;
            }
            if ("button".equals(f.c)) {
                String nm = nameFor(t, "button");
                if (nm == null) throw bad(m("El botón necesita un nombre", "The button needs a name"));
                bd = new ButtonDef();
                bd.name = nm;
                pg.buttons.put(norm(nm), bd);
                mode = 4;
                return;
            }
            throw bad(m("Se esperaba «cuando», «cada», «macro», «panel» o «boton»", "Expected 'when', 'every', 'macro', 'panel' or 'button'"));
        }

        String nameOf(List<Tok> t, String noun, List<String> strs) {
            if (!strs.isEmpty()) return norm(strs.get(0));
            String b = bareAfter(t, noun);
            return b == null ? null : norm(b);
        }

        Rule trigger(List<Tok> t) {
            Rule r = new Rule();
            Set<String> cs = new HashSet<>();
            Tok time = null, num = null;
            List<String> strs = new ArrayList<>();
            String dir = null;
            for (Tok k : t) {
                if (k.q) { strs.add(k.raw); continue; }
                if (k.c != null) {
                    cs.add(k.c);
                    if (dir == null && (k.c.equals("left") || k.c.equals("right") || k.c.equals("up") || k.c.equals("down"))) dir = k.c;
                }
                if (k.time && time == null) time = k;
                if (k.isNum && num == null) num = k;
            }
            if (time != null) {
                String[] hm = time.raw.split(":");
                r.hh = Integer.parseInt(hm[0]);
                r.mm = Integer.parseInt(hm[1]);
                if (r.hh > 23 || r.mm > 59) throw bad(m("Hora inválida", "Invalid time"));
                r.ev = "at";
            } else if (cs.contains("every")) {
                if (num == null) throw bad(m("«cada» necesita un número (ej: cada 30 minutos)", "'every' needs a number (e.g. every 30 minutes)"));
                int i = t.indexOf(num);
                Tok u = i + 1 < t.size() ? t.get(i + 1) : null;
                r.ev = "every";
                r.period = Math.max(500L, (long) (num.dval * unitMs(u, 1000)));
            } else if (cs.contains("edge")) {
                String d = edgeOf(dir);
                if (d == null) throw bad(m("Indicá el borde: izquierdo, derecho, arriba o abajo", "Specify the edge: left, right, top or bottom"));
                r.ev = "swipe_edge";
                r.arg = d;
            } else if (cs.contains("center") && (!cs.contains("button") || strs.isEmpty())) {
                if (cs.contains("swipe")) {
                    r.ev = "swipe_center";
                    r.arg = dir;
                } else if (cs.contains("hold")) {
                    r.ev = "hold_center";
                    r.ms = 450;
                    if (num != null) {
                        int i = t.indexOf(num);
                        Tok u = i + 1 < t.size() ? t.get(i + 1) : null;
                        r.ms = (long) (num.dval * unitMs(u, 1));
                    }
                } else {
                    r.ev = "tap_center";
                    r.n = cs.contains("double") ? 2 : num != null ? (int) num.dval : 1;
                }
            } else if (cs.contains("background")) {
                r.ev = cs.contains("double") ? "bg_double" : cs.contains("hold") ? "bg_hold" : "bg_tap";
            } else if (cs.contains("button") && !strs.isEmpty()) {
                r.ev = cs.contains("hold") ? "btn_hold" : "btn_tap";
                r.arg = norm(strs.get(0));
            } else if (cs.contains("app")) {
                r.ev = cs.contains("installed") ? "app_installed" : cs.contains("removed") ? "app_removed"
                    : cs.contains("hold") ? "app_hold" : "app_open";
                r.arg = nameOf(t, "app", strs);
            } else if (cs.contains("drawer") || cs.contains("category")) {
                r.ev = cs.contains("close") ? "drawer_close" : "drawer_open";
                r.arg = nameOf(t, cs.contains("category") ? "category" : "drawer", strs);
            } else if (cs.contains("panel")) {
                r.ev = cs.contains("close") ? "panel_close" : "panel_open";
                r.arg = nameOf(t, "panel", strs);
            } else if (cs.contains("resume")) r.ev = "resume";
            else if (cs.contains("home")) r.ev = "home";
            else if (cs.contains("boot")) r.ev = "boot";
            else throw bad(m("No entiendo el evento. Ejemplos: «cuando toco el boton central 2 veces», «cada 30 minutos», «a las 08:00»",
                "Unknown event. Examples: 'when I tap the center button 2 times', 'every 30 minutes', 'at 08:00'"));
            return r;
        }

        Blk top(String kind) {
            if (blks.isEmpty() || !blks.get(blks.size() - 1).kind.equals(kind))
                throw bad(m("«sino» sin un «si» abierto", "'else' without an open 'if'"));
            return blks.get(blks.size() - 1);
        }

        List<Tok> trimTail(List<Tok> t, String... cs) {
            List<Tok> l = new ArrayList<>(t);
            while (!l.isEmpty()) {
                Tok k = l.get(l.size() - 1);
                boolean cut = false;
                for (String c : cs) if (!k.q && c.equals(k.c)) cut = true;
                if (!cut) break;
                l.remove(l.size() - 1);
            }
            return l;
        }

        void endBlock() {
            if (!blks.isEmpty()) {
                Blk b = blks.remove(blks.size() - 1);
                if (b.kind.equals("if")) {
                    if (b.jf >= 0) code.get(b.jf).b = code.size();
                    for (int e : b.ends) code.get(e).a = code.size();
                } else {
                    emit(A_JMP, b.start, null);
                    code.get(b.jf).b = code.size();
                }
            } else {
                mode = 0;
                code = null;
            }
        }

        boolean isName(Tok k) { return !k.q && !k.isNum && k.raw.matches("[\\p{L}_][\\p{L}\\p{N}_]*"); }

        void assign(String name, List<Tok> t, int from) {
            int i = from;
            if (t != null && i < t.size() && t.get(i).low.equals("=")) i++;
            List<Tok> ex = t == null ? new ArrayList<Tok>() : sub(t, i, t.size());
            if (ex.isEmpty()) ex.add(numTok("0", 0));
            act("var", name, ex);
        }

        void stmt(List<Tok> t) {
            Tok f = t.get(0);
            String c = f.c == null ? "" : f.c;
            if (t.size() >= 2 && isName(f) && t.get(1).low.equals("=") && !"var".equals(c) && !"if".equals(c)) { assign(f.low, t, 2); return; }
            switch (c) {
                case "end": endBlock(); return;
                case "else": {
                    Blk b = top("if");
                    int j = emit(A_JMP, null, null);
                    b.ends.add(j);
                    if (b.jf >= 0) { code.get(b.jf).b = code.size(); b.jf = -1; }
                    if (t.size() > 1 && "if".equals(t.get(1).c))
                        b.jf = emit(A_JF, trimTail(sub(t, 2, t.size()), "then", "do"), null);
                    return;
                }
                case "if": {
                    List<Tok> cond = trimTail(sub(t, 1, t.size()), "then", "do");
                    if (cond.isEmpty()) throw bad(m("Falta la condición", "Missing condition"));
                    Blk b = new Blk("if");
                    b.jf = emit(A_JF, cond, null);
                    blks.add(b);
                    return;
                }
                case "while": {
                    List<Tok> cond = trimTail(sub(t, 1, t.size()), "do", "then");
                    if (cond.isEmpty()) throw bad(m("Falta la condición", "Missing condition"));
                    Blk b = new Blk("while");
                    b.start = code.size();
                    b.jf = emit(A_JF, cond, null);
                    blks.add(b);
                    return;
                }
                case "repeat": {
                    List<Tok> ex = trimTail(sub(t, 1, t.size()), "times", "do");
                    if (ex.isEmpty()) throw bad(m("Falta cuántas veces", "Missing repeat count"));
                    int sl = slot++ % 32;
                    emit(A_RINIT, sl, ex);
                    Blk b = new Blk("repeat");
                    b.start = code.size();
                    b.jf = emit(A_RTEST, sl, null);
                    blks.add(b);
                    return;
                }
                case "wait": {
                    List<Tok> a = sub(t, 1, t.size());
                    long mult = 1000;
                    if (!a.isEmpty() && a.get(a.size() - 1).c != null && unitMs(a.get(a.size() - 1), 0) > 0) {
                        mult = unitMs(a.get(a.size() - 1), 1000);
                        a.remove(a.size() - 1);
                    }
                    if (a.isEmpty()) throw bad(m("Falta cuánto esperar (ej: esperar 2 s)", "Missing wait time (e.g. wait 2 s)"));
                    emit(A_WAIT, a, mult);
                    return;
                }
                case "stop": emit(A_STOP, null, null); return;
                case "block": emit(A_BLOCK, null, null); return;
                case "var": {
                    if (t.size() < 2) throw bad(m("Falta el nombre de la variable", "Missing variable name"));
                    assign(t.get(1).low, t, 2);
                    return;
                }
                case "run": {
                    boolean sh = false;
                    for (Tok k : t) if (!k.q && "shell".equals(k.c)) sh = true;
                    if (sh) { act("shell", templ(t, "shell")); return; }
                    String nm = firstQuoted(t);
                    if (nm == null) nm = bareAfter(t, "macro");
                    if (nm == null) nm = joinRaw(sub(t, 1, t.size()));
                    if (nm.isEmpty()) throw bad(m("Falta el nombre de la macro", "Missing macro name"));
                    emit(A_CALL, norm(nm), null);
                    calls.add(new Object[]{norm(nm), line});
                    return;
                }
                case "shell": act("shell", templ(t, "shell")); return;
                case "toast": act("toast", templ(t, "toast")); return;
                case "vibrate": act("vibrate", sub(t, 1, t.size())); return;
                case "open": open(t); return;
                case "close": close(t); return;
                case "hide": hideShow(t, false); return;
                case "show": hideShow(t, true); return;
                case "toggle": toggle(t); return;
                case "set": case "put": setOrPut(t); return;
                case "add": addOrIncr(t, 1); return;
                case "sub": addOrIncr(t, -1); return;
                case "reset": {
                    boolean all = false;
                    for (Tok k : t) if (!k.q && "all".equals(k.c)) all = true;
                    if (all) { act("reset_all"); return; }
                    Object[] mt = matchPhrase(t, 1, SETKEY);
                    if (mt == null) throw bad(m("Ajuste desconocido para restablecer", "Unknown setting to reset"));
                    act("reset", mt[0]);
                    return;
                }
                case "pin": act("pin", appName(t)); return;
                case "unpin": act("unpin", appName(t)); return;
                case "remove": removeStmt(t); return;
                case "create": {
                    boolean cat = false;
                    for (Tok k : t) if (!k.q && "category".equals(k.c)) cat = true;
                    if (!cat) throw bad(m("Solo se puede crear una categoría: crear categoria \"Nombre\"", "Only categories can be created: create category \"Name\""));
                    String nm = firstQuoted(t);
                    if (nm == null) nm = bareAfter(t, "category");
                    if (nm == null) throw bad(m("Falta el nombre de la categoría", "Missing category name"));
                    String icon = "";
                    boolean seen = false;
                    for (Tok k : t) {
                        if (k.q && seen) { icon = k.raw; break; }
                        if (k.q && k.raw.equals(nm)) seen = true;
                    }
                    act("cat_create", nm, icon);
                    return;
                }
                default:
            }
            switch (f.low) {
                case "brillo": case "brightness": act("brightness", sub(t, 1, t.size())); return;
                case "volumen": case "volume": act("volume", sub(t, 1, t.size())); return;
                case "recargar": case "reload": case "refresh": case "refrescar": case "actualizar": act("refresh"); return;
                default:
            }
            throw bad(m("No entiendo: «", "Unknown statement: '") + joinRaw(t) + (ES ? "»" : "'"));
        }

        String templ(List<Tok> t, String verbC) {
            String q = firstQuoted(t);
            if (q != null) return q;
            List<Tok> r = new ArrayList<>();
            boolean seen = false;
            for (Tok k : t) {
                if (!seen && !k.q && verbC.equals(k.c)) { seen = true; continue; }
                if (seen || !verbC.equals(t.get(0).c)) r.add(k);
            }
            if (r.isEmpty()) r = sub(t, 1, t.size());
            return joinRaw(r);
        }

        String appName(List<Tok> t) {
            String nm = nameFor(t, "app");
            if (nm == null) nm = joinRaw(sub(t, 1, t.size()));
            if (nm == null || nm.isEmpty()) throw bad(m("Falta el nombre de la app", "Missing app name"));
            return nm;
        }

        boolean has(List<Tok> t, String c) {
            for (Tok k : t) if (!k.q && c.equals(k.c)) return true;
            return false;
        }

        boolean hasLow(List<Tok> t, String... ws) {
            for (Tok k : t) if (!k.q) for (String w : ws) if (k.low.equals(w)) return true;
            return false;
        }

        void open(List<Tok> t) {
            if (has(t, "panel")) { act("panel_open", panelArgs(t)); return; }
            if (has(t, "category")) {
                String nm = nameFor(t, "category");
                if (nm == null) throw bad(m("Falta el nombre de la categoría", "Missing category name"));
                act("open_cat", nm);
                return;
            }
            String q = firstQuoted(t);
            if (q != null && q.toLowerCase(java.util.Locale.ROOT).startsWith("http")) { act("open_url", q); return; }
            for (Tok k : t) if (!k.q && k.low.startsWith("http")) { act("open_url", k.raw); return; }
            if (hasLow(t, "editor", "scripts", "script", "automatizacion", "automation")) { act("open_editor"); return; }
            if (hasLow(t, "control") && has(t, "center")) { act("open_cc"); return; }
            if (hasLow(t, "ajustes", "settings", "configuracion", "config", "preferencias")) {
                if (hasLow(t, "sistema", "system", "android")) act("open_syssettings");
                else act("open_launcher_settings");
                return;
            }
            if (has(t, "newapps")) { act("open_new"); return; }
            if (has(t, "app")) {
                String nm = nameFor(t, "app");
                if (nm == null) act("open_all"); else act("open_app", nm);
                return;
            }
            if (has(t, "drawer")) { act("open_all"); return; }
            if (q != null) { act("open_app", q); return; }
            String nm = joinRaw(sub(t, 1, t.size()));
            if (nm.isEmpty()) throw bad(m("¿Qué querés abrir?", "What do you want to open?"));
            act("open_app", nm);
        }

        Object[] panelArgs(List<Tok> t) {
            String nm = nameFor(t, "panel");
            if (nm == null) throw bad(m("Falta el nombre del panel", "Missing panel name"));
            String dir = null, anim = null;
            Long closeMs = null, durMs = null;
            for (int i = 0; i < t.size(); i++) {
                Tok k = t.get(i);
                if (k.q) continue;
                String lw = k.low;
                if (lw.equals("desde") || lw.equals("from")) dir = dirOf(sub(t, i + 1, Math.min(t.size(), i + 4)));
                else if (lw.equals("con") || lw.equals("with") || lw.equals("animacion") || lw.equals("animation") || lw.equals("estilo") || lw.equals("style") || lw.equals("efecto") || lw.equals("effect")) {
                    for (int j = i + 1; j < Math.min(t.size(), i + 3); j++) {
                        String a = ANIMS.get(t.get(j).low);
                        if (a != null) { anim = a; break; }
                    }
                } else if (lw.equals("durante") || lw.equals("for")) {
                    long a = amountAfter(t, i + 1, 1000);
                    if (a > 0) closeMs = a;
                } else if (lw.equals("duracion") || lw.equals("duration")) {
                    long a = amountAfter(t, i + 1, 1);
                    if (a > 0) durMs = a;
                }
            }
            return new Object[]{nm, dir, anim, closeMs, durMs};
        }

        void close(List<Tok> t) {
            if (has(t, "panel")) {
                String nm = nameFor(t, "panel");
                act("panel_close", nm);
                return;
            }
            if (has(t, "all")) { act("close_all"); return; }
            act("close_drawer");
        }

        void hideShow(List<Tok> t, boolean show) {
            if (has(t, "app")) { act(show ? "app_show" : "app_hide", appName(t)); return; }
            if (has(t, "panel")) {
                String nm = nameFor(t, "panel");
                if (show) { if (nm == null) throw bad(m("Falta el nombre del panel", "Missing panel name")); act("panel_open", panelArgs(t)); }
                else act("panel_close", nm);
                return;
            }
            if (has(t, "button")) {
                String nm = nameFor(t, "button");
                if (nm == null) throw bad(m("Falta el nombre del botón", "Missing button name"));
                act(show ? "btn_show" : "btn_hide", nm);
                return;
            }
            Object[] mt = matchPhrase(t, 1, SETKEY);
            if (mt != null) {
                List<Tok> v = new ArrayList<>();
                v.add(numTok(show ? "1" : "0", show ? 1 : 0));
                act("set", mt[0], v);
                return;
            }
            if (show && firstQuoted(t) != null) { act("toast", firstQuoted(t)); return; }
            throw bad(m("No sé qué ", "Don't know what to ") + (show ? m("mostrar", "show") : m("ocultar", "hide")));
        }

        void toggle(List<Tok> t) {
            if (has(t, "panel")) {
                String nm = nameFor(t, "panel");
                if (nm == null) throw bad(m("Falta el nombre del panel", "Missing panel name"));
                act("panel_toggle", panelArgs(t));
                return;
            }
            if (has(t, "button")) {
                String nm = nameFor(t, "button");
                if (nm == null) throw bad(m("Falta el nombre del botón", "Missing button name"));
                act("btn_toggle", nm);
                return;
            }
            Object[] mt = matchPhrase(t, 1, SETKEY);
            if (mt == null) throw bad(m("Ajuste desconocido para alternar", "Unknown setting to toggle"));
            act("toggle_set", mt[0]);
        }

        void setOrPut(List<Tok> t) {
            if (has(t, "app")) {
                String nm = nameFor(t, "app");
                if (nm == null) throw bad(m("Falta el nombre de la app", "Missing app name"));
                boolean dock = hasLow(t, "dock");
                if (dock) {
                    Tok n = null;
                    for (Tok k : t) if (k.isNum) n = k;
                    if (n == null) throw bad(m("Falta la posición del dock (1 a 4)", "Missing dock slot (1 to 4)"));
                    List<Tok> ex = new ArrayList<>();
                    ex.add(n);
                    act("dock_put", nm, ex);
                    return;
                }
                if (has(t, "category")) {
                    String cat = categoryAfter(t);
                    if (cat == null) throw bad(m("Falta la categoría", "Missing category"));
                    act("cat_add_app", nm, cat);
                    return;
                }
                throw bad(m("Indicá «en dock N» o «en categoria X»", "Specify 'in dock N' or 'in category X'"));
            }
            Object[] mt = matchPhrase(t, 1, SETKEY);
            if (mt == null) throw bad(m("Ajuste desconocido. Mirá la lista de ajustes en «Referencia»", "Unknown setting. See the settings list in 'Reference'"));
            List<Tok> v = valueTail(t, (Integer) mt[1]);
            if (v.isEmpty()) throw bad(m("Falta el valor del ajuste", "Missing setting value"));
            act("set", mt[0], v);
        }

        String categoryAfter(List<Tok> t) {
            int qs = 0;
            String first = null, second = null;
            for (Tok k : t) if (k.q) { qs++; if (first == null) first = k.raw; else if (second == null) second = k.raw; }
            if (second != null) return second;
            String b = bareAfter(t, "category");
            return b != null ? b : (qs == 1 && !has(t, "app") ? first : null);
        }

        void addOrIncr(List<Tok> t, int sign) {
            if (sign > 0 && has(t, "app") && has(t, "category")) {
                String nm = nameFor(t, "app");
                String cat = categoryAfter(t);
                if (nm == null || cat == null) throw bad(m("Falta app o categoría", "Missing app or category"));
                act("cat_add_app", nm, cat);
                return;
            }
            Object[] mt = matchPhrase(t, 1, SETKEY);
            if (mt != null) {
                List<Tok> v = valueTail(t, (Integer) mt[1]);
                act("incr", mt[0], v, sign);
                return;
            }
            if (t.size() >= 2 && isName(t.get(1))) {
                act("var_incr", t.get(1).low, sub(t, 2, t.size()), sign);
                return;
            }
            throw bad(m("Ajuste o variable desconocida", "Unknown setting or variable"));
        }

        void removeStmt(List<Tok> t) {
            boolean cat = has(t, "category"), app = has(t, "app"), dock = hasLow(t, "dock");
            if (cat && app) {
                String nm = nameFor(t, "app");
                String c2 = categoryAfter(t);
                if (nm == null || c2 == null) throw bad(m("Falta app o categoría", "Missing app or category"));
                act("cat_remove_app", nm, c2);
                return;
            }
            if (cat) {
                String nm = nameFor(t, "category");
                if (nm == null) throw bad(m("Falta el nombre de la categoría", "Missing category name"));
                act("cat_delete", nm);
                return;
            }
            if (dock && app) { act("dock_clear_app", appName(t)); return; }
            if (dock) {
                Tok n = null;
                for (Tok k : t) if (k.isNum) n = k;
                if (n == null) throw bad(m("Falta la posición del dock (1 a 4)", "Missing dock slot (1 to 4)"));
                List<Tok> ex = new ArrayList<>();
                ex.add(n);
                act("dock_clear", ex);
                return;
            }
            throw bad(m("Usá: quitar dock N, quitar app X de categoria Y o borrar categoria X", "Use: remove dock N, remove app X from category Y or delete category X"));
        }

        void props(List<Tok> t) {
            Tok f = t.get(0);
            if (mode == 3) {
                if ("app".equals(f.c)) {
                    PItem it = new PItem();
                    it.app = nameFor(t, "app");
                    if (it.app == null) throw bad(m("Falta el nombre de la app", "Missing app name"));
                    pd.items.add(it);
                    return;
                }
                if ("category".equals(f.c)) {
                    PItem it = new PItem();
                    it.fromCategory = nameFor(t, "category");
                    if (it.fromCategory == null) throw bad(m("Falta el nombre de la categoría", "Missing category name"));
                    pd.items.add(it);
                    return;
                }
                if ("button".equals(f.c) || f.low.equals("item") || f.low.equals("elemento") || f.low.equals("accion") || f.low.equals("action") || f.low.equals("opcion") || f.low.equals("option")) {
                    pd.items.add(item(t));
                    return;
                }
            }
            Object[] mt = matchPhrase(t, 0, PROPK);
            if (mt == null) throw bad(m("Propiedad desconocida: «", "Unknown property: '") + joinRaw(t) + (ES ? "»" : "'"));
            String key = (String) mt[0];
            List<Tok> v = valueTail(t, (Integer) mt[1]);
            if (v.isEmpty()) throw bad(m("Falta el valor de «", "Missing value for '") + key + (ES ? "»" : "'"));
            if (mode == 3) panelProp(key, v); else buttonProp(key, v);
        }

        int intOf(List<Tok> v, long defMult) {
            long a = amountAfter(v, 0, defMult);
            if (a < 0) throw bad(m("Se esperaba un número", "A number was expected"));
            return (int) a;
        }

        String strOf(List<Tok> v) {
            String q = firstQuoted(v);
            return q != null ? q : joinRaw(v);
        }

        void panelProp(String key, List<Tok> v) {
            switch (key) {
                case "from": {
                    String d = dirOf(v);
                    if (d == null) throw bad(m("Dirección: izquierda, derecha, arriba, abajo o centro", "Direction: left, right, up, down or center"));
                    pd.from = d;
                    break;
                }
                case "anim": {
                    String a = null;
                    for (Tok k : v) if (a == null) a = ANIMS.get(k.low);
                    if (a == null) throw bad(m("Animación: deslizar, zoom, fundido, rebote o ninguna", "Animation: slide, zoom, fade, bounce or none"));
                    pd.anim = a;
                    break;
                }
                case "dur": pd.dur = intOf(v, 1); break;
                case "cols": pd.cols = Math.max(1, intOf(v, 1)); break;
                case "h": pd.h = intOf(v, 1); break;
                case "w": pd.w = intOf(v, 1); break;
                case "bg": {
                    Integer c = colorOf(v);
                    if (c == null) throw bad(m("Color inválido (ej: #1B1B3A o azul)", "Invalid color (e.g. #1B1B3A or blue)"));
                    pd.bg = c;
                    break;
                }
                case "radius": pd.radius = intOf(v, 1); break;
                case "title": pd.title = strOf(v); break;
                case "tcolor": {
                    Integer c = colorOf(v);
                    if (c == null) throw bad(m("Color inválido", "Invalid color"));
                    pd.tcolor = c;
                    break;
                }
                case "size": pd.isize = intOf(v, 1); break;
                case "outside": { Integer b = boolOf(v); pd.outside = b == null || b == 1; break; }
                case "autoclose": { Integer b = boolOf(v); pd.autoclose = b == null || b == 1; break; }
                default: throw bad(m("Esa propiedad no sirve en un panel", "That property does not apply to a panel"));
            }
        }

        void buttonProp(String key, List<Tok> v) {
            switch (key) {
                case "icon": bd.icon = strOf(v); break;
                case "label": bd.label = strOf(v); break;
                case "app": bd.app = strOf(v); break;
                case "pos": bd.pos = joinRaw(v); break;
                case "x": bd.x = intOf(v, 1); break;
                case "y": bd.y = intOf(v, 1); break;
                case "size": bd.size = intOf(v, 1); break;
                case "opacity": bd.opacity = Math.max(0, Math.min(100, intOf(v, 1))); break;
                case "radius": bd.radius = intOf(v, 1); break;
                case "shape": {
                    String s = strOf(v).toLowerCase(java.util.Locale.ROOT);
                    s = norm(s);
                    bd.shape = s.contains("cuadrad") || s.contains("square") ? "square" : s.contains("redond") || s.contains("round") || s.contains("suave") ? "round" : "circle";
                    break;
                }
                case "visible": { Integer b = boolOf(v); bd.visible = b == null || b == 1; break; }
                case "bg": {
                    Integer c = colorOf(v);
                    if (c == null) throw bad(m("Color inválido (ej: #3D7BFF o azul)", "Invalid color (e.g. #3D7BFF or blue)"));
                    bd.bg = c;
                    break;
                }
                case "tcolor": {
                    Integer c = colorOf(v);
                    if (c == null) throw bad(m("Color inválido", "Invalid color"));
                    bd.tcolor = c;
                    break;
                }
                default: throw bad(m("Esa propiedad no sirve en un botón", "That property does not apply to a button"));
            }
        }

        PItem item(List<Tok> t) {
            PItem it = new PItem();
            List<String> strs = new ArrayList<>();
            for (Tok k : t) if (k.q) strs.add(k.raw);
            if (strs.isEmpty()) throw bad(m("El elemento necesita un nombre entre comillas", "The item needs a quoted name"));
            it.label = strs.get(0);
            for (int i = 1; i < t.size(); i++) {
                Tok k = t.get(i);
                if (k.q) continue;
                Tok nx = i + 1 < t.size() ? t.get(i + 1) : null;
                String val = nx == null ? null : nx.raw;
                String lw = k.low;
                if (val == null) continue;
                if (lw.equals("icono") || lw.equals("icon") || lw.equals("emoji")) { it.icon = val; i++; }
                else if (lw.equals("macro") || lw.equals("rutina") || lw.equals("routine") || "run".equals(k.c)) { it.macro = norm(val); i++; }
                else if ("app".equals(k.c)) { it.app = val; i++; }
                else if ("shell".equals(k.c)) { it.shell = val; i++; }
                else if ("panel".equals(k.c)) { it.panel = val; i++; }
                else if ("category".equals(k.c)) { it.category = val; i++; }
            }
            if (it.macro != null) calls.add(new Object[]{it.macro, line});
            return it;
        }
    }
}
