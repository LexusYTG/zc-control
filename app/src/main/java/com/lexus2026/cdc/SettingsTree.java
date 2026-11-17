package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Árbol de ajustes de ZC Control. Es la ÚNICA fuente de verdad: la pantalla principal, cada página y el
 * buscador se generan desde acá, en este mismo orden.
 *
 * <pre>
 * ZC Control
 * ├── Sistema            ├── Personalización     ├── Herramientas      └── Administración
 * │   └── Panel de control   ├── Interfaz lateral    ├── Game Control      ├── Permisos
 * │       (apariencia,       └── Launcher radial     └── Copiar / Pegar…   ├── Servicios
 * │        accesos, textos,                                                └── Acerca de
 * │        comportamiento, diseño manual,
 * │        apertura, temas, animaciones,
 * │        automatización)
 * </pre>
 *
 * Tipos de nodo:
 *  - rama   ({@link #B}): página con hijos (los hijos se dibujan en orden: ramas como filas, hojas como controles).
 *  - hoja   ({@link #L}): uno o varios controles reales; el id lo resuelve {@code SettingsPageActivity.renderLeaf}.
 *  - acción ({@link #A}): abre otra pantalla (Game Control, Permisos, etc.).
 */
public final class SettingsTree {
    private SettingsTree() {}

    public interface Target { void open(Activity a); }

    public static final class Node {
        public final String id, title;
        public final List<Node> kids;
        public final Target target;
        public Node parent;

        Node(String id, String title, Target target, Node... kids) {
            this.id = id;
            this.title = title;
            this.target = target;
            this.kids = Arrays.asList(kids);
            for (Node k : kids) k.parent = this;
        }

        public boolean isBranch() { return !kids.isEmpty(); }
        public boolean isAction() { return target != null; }
        public boolean isLeaf() { return kids.isEmpty() && target == null; }

        /** Ruta de ids desde la raíz (sin la raíz), por ejemplo {@code sistema/panel/apariencia/fondo}. */
        public String path() {
            if (parent == null) return "";
            String p = parent.path();
            return p.isEmpty() ? id : p + "/" + id;
        }

        /** Migas de los padres (sin la raíz): «Sistema › Panel de control › Apariencia». */
        public String crumbs() {
            StringBuilder sb = new StringBuilder();
            for (Node p = parent; p != null && p.parent != null; p = p.parent) {
                if (sb.length() > 0) sb.insert(0, " › ");
                sb.insert(0, p.title);
            }
            return sb.toString();
        }
    }

    private static Node B(String id, String title, Node... kids) { return new Node(id, title, null, kids); }

    private static Node L(String id, String title) { return new Node(id, title, null); }

    private static Node A(String id, String title, Target t) { return new Node(id, title, t); }

    private static Target act(final Class<? extends Activity> cls) {
        return a -> a.startActivity(new Intent(a, cls));
    }

    // =====================================================================================================
    //  EL ÁRBOL
    // =====================================================================================================

    public static final Node ROOT = B("root", "ZC Control",

        // ───────────────────────────── Sistema ─────────────────────────────
        B("sistema", "Sistema",
            B("panel", "Panel de control",

                B("apariencia", "Apariencia",
                    B("fondo", "Fondo",
                        L("bg_type", "Tipo de fondo"),
                        L("bg_blur", "Intensidad del difuminado"),
                        L("bg_dim", "Oscurecer"),
                        L("bg_tint", "Tinte")),
                    B("hoja", "Hoja",
                        L("sh_colors", "Color / Segundo color / Dirección"),
                        L("sh_alpha", "Opacidad"),
                        L("sh_radius", "Redondeo arriba / abajo"),
                        L("sh_stroke", "Borde y color del borde"),
                        L("sh_size", "Ancho / Margen lateral / Margen superior")),
                    B("botones", "Botones",
                        L("bt_shape", "Forma, redondeo, disposición"),
                        L("bt_size", "Tamaño, separación, columnas"),
                        L("bt_rot", "Rotación, escala encendido/apagado"),
                        L("bt_text", "Ícono, texto, estado, MAYÚSCULAS"),
                        L("bt_border", "Borde y redondeo de la píldora")),
                    B("colores", "Colores",
                        L("co_theme", "Tema del texto"),
                        L("co_accent", "Acento y opacidad encendido"),
                        L("co_off", "Botón apagado y opacidad"),
                        L("co_text", "Texto e íconos / etiquetas / subtexto")),
                    B("reloj", "Reloj",
                        L("rl_look", "Tamaño, alineación, grosor, fuente, cursiva"),
                        L("rl_time", "Formato de hora, segundos"),
                        L("rl_date", "Fecha: mostrar, formato, tamaño"),
                        L("rl_colors", "Color del reloj / de la fecha"),
                        L("rl_pad", "Margen vertical del encabezado")),
                    B("tipografia", "Tipografía",
                        L("ty_font", "Fuente, escala del texto"),
                        L("ty_style", "Negrita / Cursiva en todo"),
                        L("ty_space", "Espaciado entre letras")),
                    B("tarjetas", "Tarjetas y notificaciones",
                        L("ca_look", "Color, opacidad, redondeo"),
                        L("ca_border", "Borde y color del borde"),
                        L("ca_lines", "Líneas colapsada / expandida"),
                        L("ca_sizes", "Tamaño de título, texto e ícono"),
                        L("ca_extra", "Ícono de la app, mostrar la hora"),
                        L("ca_list", "Separación, alto máximo, botón borrar todo")),
                    B("brillo", "Brillo, volumen y reproductor",
                        L("br_style", "Estilo (horizontal/vertical)"),
                        L("br_thick", "Tamaño de sliders"),
                        L("br_corner", "Redondeo de sliders verticales"),
                        L("br_media", "Mostrar el reproductor"),
                        L("br_mediah", "Alto del reproductor"))),

                B("accesos", "Accesos",
                    L("ac_visible", "Accesos visibles (primera bajada)"),
                    L("ac_custom", "Accesos propios (agregar)"),
                    B("ac_indiv", "Ajuste individual",
                        L("ai_name", "Nombre y glifo"),
                        L("ai_colors", "Color encendido / apagado"),
                        L("ai_span", "Columnas que ocupa")),
                    L("ac_apps", "Apps del panel (agregar, quitar, ordenar)"),
                    B("ac_fab", "Botón flotante de edición",
                        L("fb_mode", "Mostrar (oculto, solo en Controles, siempre)"),
                        L("fb_size", "Tamaño"),
                        L("fb_sym", "Símbolo")),
                    B("ac_head", "Botones del encabezado",
                        L("hd_gear", "Botón de ajustes"),
                        L("hd_edit", "Botón ✎ de editar accesos"),
                        L("hd_size", "Tamaño de ambos"))),

                B("textos", "Textos",
                    L("tx_tabs", "Títulos de pestañas 1 y 2"),
                    L("tx_clear", "Borrar todo"),
                    L("tx_empty", "Sin notificaciones"),
                    L("tx_sym", "Símbolo de ajustes / de editar"),
                    L("tx_edit", "Texto de editar accesos"),
                    L("tx_names", "Nombres de los accesos")),

                B("comportamiento", "Comportamiento",
                    L("cp_close", "Tocar fuera cierra el panel"),
                    L("cp_onact", "Cerrar al abrir un ajuste"),
                    L("cp_haptic", "Vibración al tocar"),
                    L("cp_tabs", "Pestaña inicial / Mostrar pestañas"),
                    L("cp_test", "Botón Probar abre en"),
                    L("cp_grab", "Barrita inferior (mostrar, ancho, grosor)")),

                B("lienzo", "Diseño manual",
                    L("dm_mode", "Modo (Clásico / Lienzo)"),
                    L("dm_cols", "Columnas del lienzo"),
                    L("dm_editor", "Abrir el editor del lienzo")),

                B("apertura", "Apertura del panel",
                    B("barrita", "Barrita de acceso",
                        L("ba_zone", "Zona de apertura"),
                        L("ba_look", "Opacidad, largo, grosor, color, redondeo"),
                        L("ba_touch", "Margen superior, zona táctil, distancia")),
                    B("gestos_barrita", "Gestos de la barrita",
                        L("gb_tap", "Un toque"),
                        L("gb_double", "Doble toque"),
                        L("gb_long", "Mantener apretado"))),

                B("temas", "Temas",
                    L("th_presets", "Presets (Cristal, Neón, Terminal, Papel, Brutalista, AMOLED)"),
                    L("th_slots", "Ranuras 1 a 3 (guardar / cargar)")),
                B("animaciones", "Animaciones",
                    L("an_style", "Estilo, duración, duración de pestañas"),
                    L("an_tap", "Animación al tocar un acceso"),
                    L("an_entry", "Animación de entrada de los accesos")),

                B("automatizacion", "Automatización",
                    L("au_theme", "Tema oscuro por horario (desde, hasta)"),
                    L("au_bat", "Acento rojo con batería baja")))),

        // ───────────────────────────── Personalización ─────────────────────────────
        B("personalizacion", "Personalización",
            B("lateral", "Interfaz lateral",
                L("lt_mode", "Modo (apagado, burbuja, gestos)"),
                B("burbuja", "Burbuja",
                    L("bb_look", "Tamaño, opacidad"),
                    L("bb_hold", "Mantener apretada para moverla"),
                    L("bb_actions", "Mover ahora / Devolver al borde / Abrir panel")),
                B("lat_notif", "Notificaciones",
                    L("ln_anim", "Animación de nuevas, tiempo visible"),
                    L("ln_test", "Probar una notificación")),
                B("ventanas", "Ventanas",
                    L("vt_open", "Abrir en ventana flotante"),
                    L("vt_geom", "Modo, ancho, alto, posición X/Y"),
                    L("vt_bounds", "Pasar tamaño y posición"),
                    L("vt_aux", "Actividad auxiliar y espera"),
                    L("vt_apis", "APIs ocultas / opciones globales"),
                    L("vt_prep", "Preparar ventanas (Shizuku)")),
                B("lat_gestos", "Gestos",
                    L("lg_w", "Ancho de la zona del borde"),
                    L("lg_h", "Alto de la zona del borde"))),
            // El Launcher radial ya existía en el menú anterior y no estaba en el árbol: se conserva acá.
            A("launcher", "Launcher radial", act(LauncherSettingsActivity.class))),

        // ───────────────────────────── Herramientas ─────────────────────────────
        B("herramientas", "Herramientas",
            A("game", "Game Control", act(GamesActivity.class)),
            L("tl_config", "Copiar / Pegar / Aleatorizar / Restablecer todo")),

        // ───────────────────────────── Administración ─────────────────────────────
        B("administracion", "Administración",
            A("permisos", "Permisos", act(MainActivity.class)),
            A("servicios", "Servicios", a -> a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))),
            A("acerca", "Acerca de", a -> SettingsAbout.show(a))));

    // =====================================================================================================

    /** Busca un nodo por su ruta ({@code sistema/panel/...}); la ruta vacía es la raíz. */
    public static Node find(String path) {
        Node n = ROOT;
        if (path == null || path.isEmpty()) return n;
        for (String seg : path.split("/")) {
            Node next = null;
            for (Node k : n.kids) if (k.id.equals(seg)) { next = k; break; }
            if (next == null) return null;
            n = next;
        }
        return n;
    }

    /** ¿Esta página muestra la vista previa en vivo del panel? */
    public static boolean hasPreview(Node page) {
        String p = page.path();
        return p.startsWith("sistema/panel") && !p.startsWith("sistema/panel/automatizacion");
    }

    public static final class Hit {
        public final Node node;
        Hit(Node node) { this.node = node; }
    }

    /** Búsqueda simple (sin mayúsculas ni tildes) por título del nodo y de sus padres. */
    public static List<Hit> search(String q) {
        List<Hit> out = new ArrayList<>();
        String n = norm(q);
        if (n.isEmpty()) return out;
        walk(ROOT, n, out);
        return out;
    }

    private static void walk(Node node, String q, List<Hit> out) {
        for (Node k : node.kids) {
            if (norm(k.title).contains(q)) out.add(new Hit(k));
            walk(k, q, out);
        }
    }

    private static String norm(String s) {
        String t = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD);
        return t.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
