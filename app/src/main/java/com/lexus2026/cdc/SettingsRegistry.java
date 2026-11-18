package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Árbol de ajustes de ZC Control: SECCIÓN > CATEGORÍA > ajuste. Una sola fuente de verdad: la pantalla
 * principal, las categorías y la búsqueda se generan de aquí. Agregar un ajuste = agregar una línea.
 * Un ajuste sin destino ({@code target == null}) se muestra como «Pronto».
 */
public final class SettingsRegistry {
    private SettingsRegistry() {}

    public interface Target { void open(Activity a); }

    public static final class Leaf {
        public final String title, sub, keywords;
        public final Target target;
        Leaf(String title, String sub, String keywords, Target target) {
            this.title = title; this.sub = sub; this.keywords = keywords; this.target = target;
        }
    }

    public static final class Category {
        public final String id, icon, title, sub;
        public final List<Leaf> leaves;
        Category(String id, String icon, String title, String sub, Leaf... leaves) {
            this.id = id; this.icon = icon; this.title = title; this.sub = sub; this.leaves = Arrays.asList(leaves);
        }
    }

    public static final class Section {
        public final String title;
        public final List<Category> cats;
        Section(String title, Category... cats) { this.title = title; this.cats = Arrays.asList(cats); }
    }

    // ---- destinos ----

    /**
     * Ajuste suelto: dibuja dentro de esta misma interfaz solo las secciones pedidas (los controles reales de
     * siempre, sin pasar por el panel viejo). {@code preview} muestra además la vista previa del panel.
     */
    private static Target sections(final String title, final boolean preview, final String... names) {
        return a -> a.startActivity(new Intent(a, BuilderActivity.class)
            .putExtra("title", title).putExtra("sections", names).putExtra("preview", preview));
    }

    private static Target act(final Class<? extends Activity> cls) {
        return a -> a.startActivity(new Intent(a, cls));
    }

    private static Target intent(final String action) {
        return a -> a.startActivity(new Intent(action));
    }

    private static Leaf L(String title, String sub, String keywords, Target t) { return new Leaf(title, sub, keywords, t); }

    public static final List<Section> ALL = Arrays.asList(
        new Section(Lang.t(841),
            new Category("look", "◈", Lang.t(660), Lang.t(661),
                L(Lang.t(662), Lang.t(663), Lang.t(664),
                    sections(Lang.t(662), true, Lang.t(552))),
                L(Lang.t(665), Lang.t(666), "hoja color degradado redondeo borde ancho margen superior lateral",
                    sections(Lang.t(665), true, Lang.t(556))),
                L(Lang.t(667), Lang.t(668), Lang.t(669),
                    sections(Lang.t(667), true, Lang.t(559))),
                L(Lang.t(670), Lang.t(671), Lang.t(672),
                    sections(Lang.t(670), true, Lang.t(557))),
                L(Lang.t(673), Lang.t(674), "fuente sans serif mono negrita cursiva espaciado letras escala texto",
                    sections(Lang.t(673), true, Lang.t(566))),
                L(Lang.t(675), Lang.t(676), Lang.t(677),
                    sections(Lang.t(675), true, Lang.t(565), Lang.t(568)))),

            new Category("panel", "▥", Lang.t(678), Lang.t(679),
                L(Lang.t(680), Lang.t(681), Lang.t(682),
                    sections(Lang.t(680), true, Lang.t(560))),
                L(Lang.t(683), Lang.t(684), "primera bajada compacta accesos visibles acotada",
                    sections(Lang.t(683), true, Lang.t(563))),
                L(Lang.t(685), Lang.t(686), Lang.t(687),
                    sections(Lang.t(685), true, Lang.t(564))),
                L(Lang.t(688), Lang.t(689), Lang.t(690),
                    sections(Lang.t(688), true, Lang.t(594), Lang.t(597), Lang.t(602))),
                L(Lang.t(691), Lang.t(692), Lang.t(693),
                    sections(Lang.t(691), true, Lang.t(573))),
                L(Lang.t(694), Lang.t(695), Lang.t(696),
                    sections(Lang.t(694), true, Lang.t(599))),
                L(Lang.t(697), Lang.t(698), Lang.t(699),
                    sections(Lang.t(697), true, Lang.t(571)))),

            new Category("access", "▣", Lang.t(700), Lang.t(701),
                L(Lang.t(702), Lang.t(703), Lang.t(704),
                    sections(Lang.t(702), true, Lang.t(572))),
                L(Lang.t(705), Lang.t(706), "gestos toque doble toque mantener apretado barrita acciones",
                    sections(Lang.t(705), false, Lang.t(577))),
                L(Lang.t(707), Lang.t(708), Lang.t(709),
                    sections(Lang.t(707), true, Lang.t(591))))),

        new Section(Lang.t(640),
            new Category("themes", "◉", Lang.t(710), Lang.t(711),
                L(Lang.t(659), Lang.t(712), "presets temas ranuras guardar cargar estilo",
                    sections(Lang.t(659), true, Lang.t(576)))),
            new Category("anim", "▦", Lang.t(713), Lang.t(714),
                L(Lang.t(715), Lang.t(716), Lang.t(717),
                    sections(Lang.t(715), true, Lang.t(570))),
                L(Lang.t(718), Lang.t(719), Lang.t(720),
                    sections(Lang.t(718), true, Lang.t(592)))),
            new Category("side", "▤", Lang.t(643), Lang.t(721),
                L(Lang.t(722), Lang.t(723), Lang.t(724),
                    sections(Lang.t(722), false, Lang.t(542))),
                L(Lang.t(725), Lang.t(726), Lang.t(727),
                    sections(Lang.t(725), false, Lang.t(545))),
                L(Lang.t(728), Lang.t(729), "accesos panel lateral apps agregar quitar ordenar",
                    sections(Lang.t(728), false, Lang.t(547))),
                L(Lang.t(730), Lang.t(731), Lang.t(732),
                    sections(Lang.t(730), false, Lang.t(548))),
                L(Lang.t(733), Lang.t(734), Lang.t(735),
                    sections(Lang.t(733), false, Lang.t(549)))),
            new Category("launcher", "▨", Lang.t(736), Lang.t(737),
                L(Lang.t(623), Lang.t(644), Lang.t(738),
                    act(LauncherSettingsActivity.class)))),

        new Section(Lang.t(856),
            new Category("game", "◍", Lang.t(606), Lang.t(739),
                L(Lang.t(606), Lang.t(740), Lang.t(741),
                    act(GamesActivity.class))),
            new Category("auto", "⚙", Lang.t(742), Lang.t(743),
                L(Lang.t(744), Lang.t(745), Lang.t(746),
                    sections(Lang.t(744), false, Lang.t(593)))),
            new Category("tools", "◈", Lang.t(856), Lang.t(747),
                L(Lang.t(748), Lang.t(749), Lang.t(750),
                    sections(Lang.t(856), false, Lang.t(603))))),

        new Section(Lang.t(751),
            new Category("perm", "◐", Lang.t(752), Lang.t(753),
                L(Lang.t(638), Lang.t(754), Lang.t(755),
                    act(MainActivity.class))),
            new Category("svc", "⚙", Lang.t(756), Lang.t(757),
                L(Lang.t(757), Lang.t(649), "servicios accesibilidad activar",
                    intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))),
            new Category("about", "ℹ", Lang.t(758), Lang.t(759),
                L(Lang.t(760), Lang.t(871), Lang.t(761),
                    a -> SettingsAbout.show(a))))
    );

    public static Category category(String id) {
        for (Section s : ALL) for (Category c : s.cats) if (c.id.equals(id)) return c;
        return null;
    }

    public static final class Hit {
        public final Category cat;
        public final Leaf leaf;
        Hit(Category cat, Leaf leaf) { this.cat = cat; this.leaf = leaf; }
    }

    /** Búsqueda simple (sin distinguir mayúsculas ni tildes) por título, descripción y categoría. */
    public static List<Hit> search(String q) {
        List<Hit> out = new ArrayList<>();
        String n = norm(q);
        if (n.isEmpty()) return out;
        for (Section s : ALL) for (Category c : s.cats) for (Leaf l : c.leaves) {
            if (norm(l.title).contains(n) || norm(l.sub).contains(n) || norm(l.keywords).contains(n) || norm(c.title).contains(n)) out.add(new Hit(c, l));
        }
        return out;
    }

    private static String norm(String s) {
        String t = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD);
        return t.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
