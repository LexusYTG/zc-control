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

    public static final Node ROOT = B("root", Lang.t(871),

        // ───────────────────────────── Sistema ─────────────────────────────
        B("sistema", Lang.t(841),
            B("panel", Lang.t(678),

                B("apariencia", Lang.t(861),
                    B("fondo", Lang.t(762),
                        L("bg_type", Lang.t(838)),
                        L("bg_blur", Lang.t(554)),
                        L("bg_dim", Lang.t(555)),
                        L("bg_tint", Lang.t(839))),
                    B("hoja", Lang.t(763),
                        L("sh_colors", Lang.t(764)),
                        L("sh_alpha", Lang.t(553)),
                        L("sh_radius", Lang.t(765)),
                        L("sh_stroke", Lang.t(766)),
                        L("sh_size", Lang.t(767))),
                    B("botones", Lang.t(670),
                        L("bt_shape", Lang.t(768)),
                        L("bt_size", Lang.t(769)),
                        L("bt_rot", Lang.t(770)),
                        L("bt_text", Lang.t(771)),
                        L("bt_border", Lang.t(772))),
                    B("colores", Lang.t(667),
                        L("co_theme", Lang.t(840)),
                        L("co_accent", Lang.t(773)),
                        L("co_off", Lang.t(774)),
                        L("co_text", Lang.t(775))),
                    B("reloj", Lang.t(776),
                        L("rl_look", Lang.t(777)),
                        L("rl_time", Lang.t(778)),
                        L("rl_date", Lang.t(779)),
                        L("rl_colors", Lang.t(780)),
                        L("rl_pad", Lang.t(562))),
                    B("tipografia", Lang.t(673),
                        L("ty_font", Lang.t(781)),
                        L("ty_style", Lang.t(782)),
                        L("ty_space", Lang.t(567))),
                    B("tarjetas", Lang.t(675),
                        L("ca_look", Lang.t(783)),
                        L("ca_border", Lang.t(766)),
                        L("ca_lines", Lang.t(784)),
                        L("ca_sizes", Lang.t(785)),
                        L("ca_extra", Lang.t(786)),
                        L("ca_list", Lang.t(787))),
                    B("brillo", Lang.t(685),
                        L("br_style", Lang.t(788)),
                        L("br_thick", Lang.t(789)),
                        L("br_corner", Lang.t(790)),
                        L("br_media", Lang.t(843)),
                        L("br_mediah", Lang.t(569)))),

                B("accesos", Lang.t(791),
                    L("ac_visible", Lang.t(792)),
                    L("ac_custom", Lang.t(793)),
                    B("ac_indiv", Lang.t(794),
                        L("ai_name", Lang.t(795)),
                        L("ai_colors", Lang.t(796)),
                        L("ai_span", Lang.t(598))),
                    L("ac_apps", Lang.t(797)),
                    B("ac_fab", Lang.t(707),
                        L("fb_mode", Lang.t(798)),
                        L("fb_size", Lang.t(558)),
                        L("fb_sym", Lang.t(851))),
                    B("ac_head", Lang.t(799),
                        L("hd_gear", Lang.t(800)),
                        L("hd_edit", Lang.t(801)),
                        L("hd_size", Lang.t(802)))),

                B("textos", Lang.t(694),
                    L("tx_tabs", Lang.t(803)),
                    L("tx_clear", Lang.t(600)),
                    L("tx_empty", Lang.t(601)),
                    L("tx_sym", Lang.t(804)),
                    L("tx_edit", Lang.t(805)),
                    L("tx_names", Lang.t(658))),

                B("comportamiento", Lang.t(697),
                    L("cp_close", Lang.t(844)),
                    L("cp_onact", Lang.t(845)),
                    L("cp_haptic", Lang.t(846)),
                    L("cp_tabs", Lang.t(806)),
                    L("cp_test", Lang.t(807)),
                    L("cp_grab", Lang.t(808))),

                B("lienzo", Lang.t(809),
                    L("dm_mode", Lang.t(810)),
                    L("dm_cols", Lang.t(574)),
                    L("dm_editor", Lang.t(575))),

                B("apertura", Lang.t(700),
                    B("barrita", Lang.t(702),
                        L("ba_zone", Lang.t(847)),
                        L("ba_look", Lang.t(811)),
                        L("ba_touch", Lang.t(812))),
                    B("gestos_barrita", Lang.t(705),
                        L("gb_tap", Lang.t(848)),
                        L("gb_double", Lang.t(849)),
                        L("gb_long", Lang.t(850)))),

                B("temas", Lang.t(710),
                    L("th_presets", Lang.t(813)),
                    L("th_slots", Lang.t(814))),
                B("animaciones", Lang.t(713),
                    L("an_style", Lang.t(815)),
                    L("an_tap", Lang.t(852)),
                    L("an_entry", Lang.t(853))),

                B("automatizacion", Lang.t(742),
                    L("au_theme", Lang.t(816)),
                    L("au_bat", Lang.t(817))))),

        // ───────────────────────────── Personalización ─────────────────────────────
        B("personalizacion", Lang.t(640),
            B("lateral", Lang.t(643),
                L("lt_mode", Lang.t(818)),
                B("burbuja", Lang.t(543),
                    L("bb_look", Lang.t(819)),
                    L("bb_hold", Lang.t(544)),
                    L("bb_actions", Lang.t(820))),
                B("lat_notif", Lang.t(874),
                    L("ln_anim", Lang.t(821)),
                    L("ln_test", Lang.t(546))),
                B("ventanas", Lang.t(822),
                    L("vt_open", Lang.t(823)),
                    L("vt_geom", Lang.t(824)),
                    L("vt_bounds", Lang.t(825)),
                    L("vt_aux", Lang.t(826)),
                    L("vt_apis", Lang.t(827)),
                    L("vt_prep", Lang.t(828))),
                B("lat_gestos", Lang.t(829),
                    L("lg_w", Lang.t(550)),
                    L("lg_h", Lang.t(551)))),
            // El Launcher radial ya existía en el menú anterior y no estaba en el árbol: se conserva acá.
            A("launcher", Lang.t(623), act(LauncherSettingsActivity.class))),

        // ───────────────────────────── Herramientas ─────────────────────────────
        B("herramientas", Lang.t(856),
            A("game", Lang.t(606), act(GamesActivity.class)),
            L("tl_config", Lang.t(830))),

        // ───────────────────────────── Administración ─────────────────────────────
        B("administracion", Lang.t(751),
            A("permisos", Lang.t(752), act(PermissionCenterActivity.class)),
            A("servicios", Lang.t(756), a -> a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))),
            A("acerca", Lang.t(758), a -> SettingsAbout.show(a))));

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
