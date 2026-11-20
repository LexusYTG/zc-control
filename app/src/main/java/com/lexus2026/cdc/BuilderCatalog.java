package com.lexus2026.cdc;

import java.util.ArrayList;
import java.util.List;

/**
 * Catálogos del constructor visual: ajustes del launcher, acciones y eventos.
 * Todo son datos: agregar una acción o un evento nuevo es agregar una fila.
 * Los nombres de ajustes (es / en) son alias que ya entiende ScriptLang, así lo que genera la interfaz siempre se puede leer y parsear.
 */
final class BuilderCatalog {
    private BuilderCatalog() {}

    /** Un ajuste del launcher. kind: n número, b sí/no, c color, a color con transparencia, e opciones. */
    static final class Set {
        String grp, key, es, en, kind, unit = "";
        int lo, hi;
        String[] eEs, eEn;
    }

    static final List<Set> SETS = new ArrayList<>();

    private static void s(String grp, String key, String es, String en, String kind, int lo, int hi, String unit) {
        Set x = new Set();
        x.grp = grp; x.key = key; x.es = es; x.en = en; x.kind = kind; x.lo = lo; x.hi = hi; x.unit = unit;
        SETS.add(x);
    }

    private static void e(String grp, String key, String es, String en, String[] eEs, String[] eEn) {
        s(grp, key, es, en, "e", 0, eEs.length - 1, "");
        Set x = SETS.get(SETS.size() - 1);
        x.eEs = eEs;
        x.eEn = eEn;
    }

    static {
        // ---- escritorio
        s("desk", LauncherSettings.DESK_COLS, "columnas escritorio", "desktop columns", "n", 2, 8, "");
        s("desk", LauncherSettings.DESK_GAP, "espaciado escritorio", "desktop spacing", "n", 0, 40, " dp");
        s("desk", LauncherSettings.APP_ICON, "icono app", "app icon", "n", 32, 96, " dp");
        s("desk", LauncherSettings.LABELS, "etiquetas", "labels", "b", 0, 1, "");
        s("desk", LauncherSettings.LABEL_SIZE, "tamaño etiquetas", "label size", "n", 8, 18, " sp");
        s("desk", LauncherSettings.TEXT_COLOR, "color texto", "text color", "a", 0, 0, "");
        e("desk", LauncherSettings.NEW_MODE, "apps nuevas", "new apps", new String[]{"sueltas", "carpeta"}, new String[]{"loose", "folder"});
        s("desk", LauncherSettings.DESKTOP, "modo escritorio", "desktop mode", "b", 0, 1, "");
        // ---- dock
        s("dock", LauncherSettings.DOCK_SHOW, "mostrar dock", "show dock", "b", 0, 1, "");
        e("dock", LauncherSettings.DOCK_POS, "posicion dock", "dock position", new String[]{"centro", "izquierda", "derecha"}, new String[]{"center", "left", "right"});
        s("dock", LauncherSettings.DOCK_W, "ancho dock", "dock width", "n", 40, 100, " %");
        s("dock", LauncherSettings.DOCK_H, "alto dock", "dock height", "n", 48, 140, " dp");
        s("dock", LauncherSettings.DOCK_Y, "dock y", "dock offset y", "n", 0, 200, " dp");
        s("dock", LauncherSettings.DOCK_COLOR, "color dock", "dock color", "a", 0, 0, "");
        s("dock", LauncherSettings.DOCK_RADIUS, "redondeo dock", "dock radius", "n", 0, 50, " dp");
        s("dock", LauncherSettings.DOCK_ICON, "icono dock", "dock icon", "n", 32, 72, " dp");
        s("dock", LauncherSettings.HOME_SIZE, "tamaño boton central", "center button size", "n", 36, 90, " dp");
        // ---- cajón
        s("drawer", LauncherSettings.DRAWER_COLS, "columnas cajon", "drawer columns", "n", 2, 8, "");
        s("drawer", LauncherSettings.DRAWER_H, "alto cajon", "drawer height", "n", 30, 90, " %");
        s("drawer", LauncherSettings.DRAWER_COLOR, "color cajon", "drawer color", "a", 0, 0, "");
        s("drawer", LauncherSettings.DRAWER_RADIUS, "redondeo cajon", "drawer radius", "n", 0, 50, " dp");
        e("drawer", LauncherSettings.DRAWER_FROM, "animacion cajon", "drawer animation",
            new String[]{"zoom", "izquierda", "derecha", "arriba", "fundido"}, new String[]{"zoom", "left", "right", "top", "fade"});
        s("drawer", LauncherSettings.ANIM, "animacion", "animation", "n", 80, 600, " ms");
        // ---- radial
        s("radial", LauncherSettings.RING1, "primer radial", "first ring", "n", 3, 8, "");
        s("radial", LauncherSettings.RING2, "segundo radial", "second ring", "n", 3, 10, "");
        s("radial", LauncherSettings.ARC, "apertura", "arc", "n", 60, 180, "°");
        s("radial", LauncherSettings.BUBBLE, "tamaño burbuja", "bubble size", "n", 24, 96, " dp");
        s("radial", LauncherSettings.GAP, "separacion", "gap", "n", 0, 30, " dp");
        s("radial", LauncherSettings.STAGE2, "etapa 2", "stage 2", "n", 10, 80, " dp");
        s("radial", LauncherSettings.SENS, "sensibilidad", "sensitivity", "n", 50, 200, " %");
        s("radial", LauncherSettings.HOLD, "tiempo mantener", "hold time", "n", 60, 800, " ms");
        e("radial", LauncherSettings.SHAPE, "forma", "shape", new String[]{"circulo", "cuadrado suave", "cuadrado", "rombo"},
            new String[]{"circle", "rounded", "square", "diamond"});
        s("radial", LauncherSettings.TRACK, "grosor arco", "track width", "n", 0, 40, " dp");
        s("radial", LauncherSettings.R1, "radio 1", "radius 1", "n", 0, 300, " dp");
        s("radial", LauncherSettings.R2GAP, "separacion arcos", "arc gap", "n", 0, 200, " dp");
        s("radial", LauncherSettings.ROT, "rotacion", "rotation", "n", -180, 180, "°");
        s("radial", LauncherSettings.FIT, "ajuste automatico", "auto fit", "b", 0, 1, "");
        s("radial", LauncherSettings.RAD_X, "radial x", "radial offset x", "n", -200, 200, " dp");
        s("radial", LauncherSettings.RAD_Y, "radial y", "radial offset y", "n", -200, 200, " dp");
        // ---- fondo y color
        e("bg", LauncherSettings.BG, "tipo fondo", "background type", new String[]{"sistema", "solido"}, new String[]{"system", "solid"});
        s("bg", LauncherSettings.BG_COLOR, "color fondo", "bg color", "c", 0, 0, "");
        s("bg", LauncherSettings.DIM, "oscurecer", "dim", "n", 0, 80, " %");
        s("bg", LauncherSettings.ACCENT, "acento", "accent", "c", 0, 0, "");
    }

    static Set set(String key) {
        for (Set x : SETS) if (x.key.equals(key)) return x;
        return null;
    }

    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    static String label(Set x, boolean es) { return cap(es ? x.es : x.en); }

    // ------------------------------------------------------------------ plantillas

    /** Fila del catálogo: categoría, nombre, plantilla con {0},{1}… y tipos de parámetro (separados por coma). */
    static final class Tpl {
        String catEs, catEn, labEs, labEn, tEs, tEn, specs;
        boolean block, hasElse;

        String label(boolean es) { return es ? labEs : labEn; }
        String cat(boolean es) { return es ? catEs : catEn; }

        String build(boolean es, String[] v) {
            String t = es ? tEs : tEn;
            for (int i = 0; i < v.length; i++) t = t.replace("{" + i + "}", v[i] == null ? "" : v[i]);
            return t.trim();
        }
    }

    private static List<Tpl> parse(String[] rows) {
        List<Tpl> out = new ArrayList<>();
        for (String r : rows) {
            String[] p = r.split("\\|", -1);
            Tpl t = new Tpl();
            String[] c = p[0].split("/");
            t.catEs = c[0];
            t.catEn = c.length > 1 ? c[1] : c[0];
            t.labEs = p[1]; t.labEn = p[2]; t.tEs = p[3]; t.tEn = p[4]; t.specs = p[5];
            t.block = p[6].contains("B");
            t.hasElse = p[6].contains("E");
            out.add(t);
        }
        return out;
    }

    /*
     * Parámetros: app, cat, panel, macro, btn (elegir de listas), text (entre comillas), raw (tal cual), num:lo:hi,
     * time (número + unidad), clock (HH:MM), dir, edge, anim, sv (ajuste + valor), setn (ajuste numérico + número),
     * setb (ajuste sí/no), set (cualquier ajuste), cond (condición guiada), name (nombre de variable).
     */
    static final List<Tpl> ACTIONS = parse(new String[]{
        "Apps y cajón/Apps & drawer|Abrir app|Open app|abrir app {0}|open app {0}|app|",
        "Apps y cajón/Apps & drawer|Abrir el cajón|Open the drawer|abrir cajon|open drawer||",
        "Apps y cajón/Apps & drawer|Abrir una categoría|Open a category|abrir categoria {0}|open category {0}|cat|",
        "Apps y cajón/Apps & drawer|Abrir los ajustes|Open settings|abrir ajustes|open settings||",
        "Apps y cajón/Apps & drawer|Abrir el editor|Open the editor|abrir editor|open editor||",
        "Apps y cajón/Apps & drawer|Abrir un enlace|Open a link|abrir {0}|open {0}|text|",
        "Apps y cajón/Apps & drawer|Cerrar todo|Close everything|cerrar todo|close all||",
        "Apps y cajón/Apps & drawer|Ocultar app|Hide app|ocultar app {0}|hide app {0}|app|",
        "Apps y cajón/Apps & drawer|Mostrar app|Show app|mostrar app {0}|show app {0}|app|",
        "Apps y cajón/Apps & drawer|Fijar app al escritorio|Pin app to desktop|fijar app {0}|pin app {0}|app|",
        "Apps y cajón/Apps & drawer|Desfijar app|Unpin app|desfijar app {0}|unpin app {0}|app|",
        "Apps y cajón/Apps & drawer|App en el dock|App in the dock|poner app {0} en dock {1}|set app {0} in dock {1}|app,num:1:4|",
        "Apps y cajón/Apps & drawer|Quitar del dock|Remove from dock|quitar dock {0}|remove dock {0}|num:1:4|",
        "Apps y cajón/Apps & drawer|Crear categoría|Create category|crear categoria {0}|create category {0}|text|",
        "Apps y cajón/Apps & drawer|App en categoría|App in category|poner app {0} en categoria {1}|set app {0} in category {1}|app,cat|",
        "Apps y cajón/Apps & drawer|Quitar app de categoría|Remove app from category|quitar app {0} de categoria {1}|remove app {0} from category {1}|app,cat|",
        "Paneles/Panels|Abrir panel|Open panel|abrir panel {0}|open panel {0}|panel|",
        "Paneles/Panels|Abrir panel desde un lado|Open panel from a side|abrir panel {0} desde {1} con {2}|open panel {0} from {1} with {2}|panel,dir,anim|",
        "Paneles/Panels|Abrir panel y cerrarlo solo|Open panel, auto-close|abrir panel {0} durante {1}|open panel {0} for {1}|panel,time|",
        "Paneles/Panels|Alternar panel|Toggle panel|alternar panel {0}|toggle panel {0}|panel|",
        "Paneles/Panels|Cerrar panel|Close panel|cerrar panel {0}|close panel {0}|panel|",
        "Botones flotantes/Floating buttons|Ocultar botón|Hide button|ocultar boton {0}|hide button {0}|btn|",
        "Botones flotantes/Floating buttons|Mostrar botón|Show button|mostrar boton {0}|show button {0}|btn|",
        "Botones flotantes/Floating buttons|Alternar botón|Toggle button|alternar boton {0}|toggle button {0}|btn|",
        "Ajustes/Settings|Poner un ajuste|Set a setting|poner {0}|set {0}|sv|",
        "Ajustes/Settings|Sumar a un ajuste|Add to a setting|sumar {0}|add {0}|setn|",
        "Ajustes/Settings|Restar a un ajuste|Subtract from a setting|restar {0}|sub {0}|setn|",
        "Ajustes/Settings|Alternar un ajuste (sí/no)|Toggle a setting (on/off)|alternar {0}|toggle {0}|setb|",
        "Ajustes/Settings|Restablecer un ajuste|Reset a setting|restablecer {0}|reset {0}|set|",
        "Ajustes/Settings|Restablecer todo|Reset everything|restablecer todo|reset all||",
        "Ajustes/Settings|Brillo|Brightness|brillo {0}|brightness {0}|num:0:100|",
        "Ajustes/Settings|Volumen|Volume|volumen {0}|volume {0}|num:0:100|",
        "Avisos/Alerts|Mostrar un aviso|Show a message|aviso {0}|toast {0}|text|",
        "Avisos/Alerts|Vibrar|Vibrate|vibrar {0}|vibrate {0}|time|",
        "Tiempo y flujo/Time & flow|Esperar|Wait|esperar {0}|wait {0}|time|",
        "Tiempo y flujo/Time & flow|Repetir N veces|Repeat N times|repetir {0} veces|repeat {0} times|num:1:100|B",
        "Tiempo y flujo/Time & flow|Mientras…|While…|mientras {0}|while {0}|cond|B",
        "Tiempo y flujo/Time & flow|Si… (con «sino»)|If… (with 'else')|si {0}|if {0}|cond|BE",
        "Tiempo y flujo/Time & flow|Detener la regla|Stop the rule|detener|stop||",
        "Tiempo y flujo/Time & flow|Bloquear la acción original|Block the original action|bloquear|block||",
        "Tiempo y flujo/Time & flow|Ejecutar una macro|Run a macro|ejecutar {0}|run {0}|macro|",
        "Tiempo y flujo/Time & flow|Guardar en una variable|Store in a variable|{0} = {1}|{0} = {1}|name,raw|",
        "Avanzado/Advanced|Comando de shell|Shell command|shell {0}|shell {0}|text|",
        "Avanzado/Advanced|Recargar el launcher|Refresh the launcher|recargar|refresh||",
        "Avanzado/Advanced|Línea de script libre|Free script line|{0}|{0}|raw|"
    });

    static final List<Tpl> EVENTS = parse(new String[]{
        "Botón central/Center button|Tocar N veces|Tap N times|cuando toco el boton central {0} veces|when I tap the center button {0} times|num:1:4|",
        "Botón central/Center button|Mantener pulsado|Hold|cuando mantengo el boton central {0}|when I hold the center button {0}|time|",
        "Botón central/Center button|Deslizar|Swipe|cuando deslizo el boton central {0}|when I swipe the center button {0}|dir|",
        "Fondo/Background|Tocar el fondo|Tap the background|cuando toco el fondo|when I tap the background||",
        "Fondo/Background|Doble toque en el fondo|Double tap the background|cuando hago doble toque en el fondo|when I double tap the background||",
        "Fondo/Background|Mantener el fondo|Hold the background|cuando mantengo el fondo|when I hold the background||",
        "Bordes/Edges|Deslizar desde un borde|Swipe from an edge|cuando deslizo desde el borde {0}|when I swipe from the {0} edge|edge|",
        "Botones flotantes/Floating buttons|Tocar un botón|Tap a button|cuando toco el boton {0}|when I tap the button {0}|btn|",
        "Botones flotantes/Floating buttons|Mantener un botón|Hold a button|cuando mantengo el boton {0}|when I hold the button {0}|btn|",
        "Apps/Apps|Abrir una app|Open an app|cuando abro la app {0}|when I open the app {0}|app|",
        "Apps/Apps|Mantener una app|Hold an app|cuando mantengo la app {0}|when I hold the app {0}|app|",
        "Apps/Apps|Instalar una app|Install an app|cuando instalo una app|when I install an app||",
        "Apps/Apps|Desinstalar una app|Uninstall an app|cuando desinstalo una app|when I uninstall an app||",
        "Cajón y paneles/Drawer & panels|Abrir el cajón|Open the drawer|cuando abro el cajon|when I open the drawer||",
        "Cajón y paneles/Drawer & panels|Cerrar el cajón|Close the drawer|cuando cierro el cajon|when I close the drawer||",
        "Cajón y paneles/Drawer & panels|Abrir una categoría|Open a category|cuando abro la categoria {0}|when I open the category {0}|cat|",
        "Cajón y paneles/Drawer & panels|Cerrar una categoría|Close a category|cuando cierro la categoria {0}|when I close the category {0}|cat|",
        "Cajón y paneles/Drawer & panels|Abrir un panel|Open a panel|cuando abro el panel {0}|when I open the panel {0}|panel|",
        "Cajón y paneles/Drawer & panels|Cerrar un panel|Close a panel|cuando cierro el panel {0}|when I close the panel {0}|panel|",
        "Sistema/System|Arranca el launcher|The launcher starts|cuando arranca|when it starts||",
        "Sistema/System|Vuelvo al launcher|I return to the launcher|cuando vuelvo|when I return||",
        "Sistema/System|Pulso Home|I press Home|cuando pulso home|when I press home||",
        "Tiempo/Time|Cada cierto tiempo|Every so often|cada {0}|every {0}|time|",
        "Tiempo/Time|A una hora fija|At a fixed time|a las {0}|at {0}|clock|"
    });

    // ------------------------------------------------------------------ vocabulario

    static final String[] DIR_KEYS = {"left", "right", "up", "down"};
    static final String[] DIR_ES = {"izquierda", "derecha", "arriba", "abajo"};
    static final String[] DIR_EN = {"left", "right", "up", "down"};
    static final String[] EDGE_ES = {"izquierdo", "derecho", "arriba", "abajo"};
    static final String[] EDGE_EN = {"left", "right", "top", "bottom"};
    static final String[] ANIM_ES = {"deslizar", "zoom", "fundido", "rebote", "ninguna"};
    static final String[] ANIM_EN = {"slide", "zoom", "fade", "bounce", "none"};
    static final String[] ANIM_KEYS = {"slide", "zoom", "fade", "bounce", "none"};
    static final String[] VARS_ES = {"bateria", "cargando", "hora", "minuto", "dia", "mes", "ano", "cajon", "app", "veces", "aleatorio"};
    static final String[] VARS_EN = {"battery", "charging", "hour", "minute", "day", "month", "year", "drawer", "app", "times", "random"};
    static final String[] COLOR_ES = {"rojo", "azul", "verde", "negro", "blanco", "amarillo", "naranja", "violeta", "rosa", "gris", "cian", "transparente"};
    static final String[] COLOR_EN = {"red", "blue", "green", "black", "white", "yellow", "orange", "purple", "pink", "gray", "cyan", "transparent"};
    static final int[] COLOR_VAL = {0xFFE53935, 0xFF3D7BFF, 0xFF43A047, 0xFF000000, 0xFFFFFFFF, 0xFFFFD600, 0xFFFB8C00, 0xFF8E24AA, 0xFFEC407A,
        0xFF757575, 0xFF00BCD4, 0x00000000};
}
