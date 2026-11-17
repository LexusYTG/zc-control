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
        new Section("Sistema",
            new Category("look", "◈", "Apariencia del panel", "Fondo, colores, tipografía y botones",
                L("Fondo y desenfoque", "Qué hay detrás del panel", "tipo de fondo desenfoque blur opacidad oscurecer tinte imagen color",
                    sections("Fondo y desenfoque", true, "FONDO DETRÁS DEL PANEL")),
                L("Hoja del panel", "Color, degradado, bordes y márgenes", "hoja color degradado redondeo borde ancho margen superior lateral",
                    sections("Hoja del panel", true, "HOJA DEL PANEL")),
                L("Colores", "Tema, acento y botones", "tema claro oscuro acento encendido apagado opacidad texto íconos",
                    sections("Colores", true, "COLORES")),
                L("Botones", "Forma, tamaño, disposición y etiquetas", "forma redondeo disposición tamaño separación columnas ícono texto borde rotación escala mayúsculas píldora",
                    sections("Botones", true, "BOTONES")),
                L("Tipografía", "Fuente, escala y estilo", "fuente sans serif mono negrita cursiva espaciado letras escala texto",
                    sections("Tipografía", true, "TIPOGRAFÍA")),
                L("Tarjetas y notificaciones", "Aspecto de las tarjetas", "tarjetas redondeo opacidad borde líneas título tamaño hora separación ícono borrar todo reproductor sliders verticales",
                    sections("Tarjetas y notificaciones", true, "TARJETAS Y NOTIFICACIONES", "DETALLE DE NOTIFICACIONES Y TARJETAS"))),

            new Category("panel", "▥", "Panel de control", "Contenido y comportamiento",
                L("Encabezado y reloj", "Reloj, fecha y botones del encabezado", "reloj fecha formato 12 24 segundos fuente cursiva alineación ajustes engranaje editar",
                    sections("Encabezado y reloj", true, "RELOJ")),
                L("Primera bajada", "Accesos de la vista acotada", "primera bajada compacta accesos visibles acotada",
                    sections("Primera bajada", true, "PRIMERA BAJADA")),
                L("Brillo, volumen y reproductor", "Sliders y reproductor multimedia", "brillo volumen sliders horizontal vertical reproductor multimedia música",
                    sections("Brillo, volumen y reproductor", true, "BRILLO Y VOLUMEN")),
                L("Accesos rápidos", "Accesos propios, orden y nombres", "accesos rápidos tiles propios agregar eliminar color columnas nombres etiquetas",
                    sections("Accesos rápidos", true, "ACCESOS PROPIOS", "AJUSTE INDIVIDUAL DE CADA ACCESO", "NOMBRES DE LOS ACCESOS")),
                L("Distribución", "Cuadrícula automática o lienzo libre", "distribución diseño manual lienzo editor columnas",
                    sections("Distribución", true, "DISEÑO MANUAL (LIENZO)")),
                L("Textos", "Títulos y mensajes del panel", "textos títulos pestañas borrar todo sin notificaciones símbolos editar",
                    sections("Textos", true, "TEXTOS")),
                L("Comportamiento", "Cierre, vibración y pestañas", "comportamiento cerrar tocar fuera vibración háptico pestaña inicial barrita de abajo probar",
                    sections("Comportamiento", true, "COMPORTAMIENTO"))),

            new Category("access", "▣", "Apertura del panel", "Barrita, gestos y botón de edición",
                L("Barrita de acceso", "Tamaño, color y zona táctil", "barrita acceso largo grosor color redondeo margen zona táctil distancia bajar",
                    sections("Barrita de acceso", true, "BARRITA DE ACCESO")),
                L("Gestos de la barrita", "Un toque, doble toque y mantener", "gestos toque doble toque mantener apretado barrita acciones",
                    sections("Gestos de la barrita", false, "GESTOS DE LA BARRITA")),
                L("Botón flotante de edición", "Mostrar, tamaño y posición", "botón flotante fab edición editar tamaño",
                    sections("Botón flotante de edición", true, "BOTÓN FLOTANTE DE EDICIÓN")))),

        new Section("Personalización",
            new Category("themes", "◉", "Temas", "Presets guardados",
                L("Presets", "Guardar y cargar estilos", "presets temas ranuras guardar cargar estilo",
                    sections("Presets", true, "PRESETS"))),
            new Category("anim", "▦", "Animaciones", "Transiciones y efectos",
                L("Animación del panel", "Estilo y duración", "animación estilo duración pestañas desacelera rebote",
                    sections("Animación del panel", true, "ANIMACIÓN")),
                L("Efectos", "Al tocar y de entrada", "efectos animación al tocar entrada accesos rebote giro",
                    sections("Efectos", true, "EFECTOS"))),
            new Category("side", "▤", "Interfaz lateral", "Burbuja, gestos, ventanas y notificaciones",
                L("Modo y burbuja", "Apagada, burbuja o gestos", "interfaz lateral modo burbuja tamaño opacidad lienzo mover borde derecho",
                    sections("Modo y burbuja", false, "INTERFAZ LATERAL")),
                L("Notificaciones dinámicas", "Aviso animado de la burbuja", "notificaciones dinámicas animación tiempo visible probar",
                    sections("Notificaciones dinámicas", false, "INTERFAZ LATERAL · NOTIFICACIONES")),
                L("Accesos del panel lateral", "Agregar, quitar y ordenar apps", "accesos panel lateral apps agregar quitar ordenar",
                    sections("Accesos del panel lateral", false, "INTERFAZ LATERAL · ACCESOS DEL PANEL")),
                L("Ventanas flotantes", "Tamaño, posición y modo de ventana", "ventanas flotantes freeform modo ancho alto posición bounds auxiliar reflexión shizuku",
                    sections("Ventanas flotantes", false, "INTERFAZ LATERAL · VENTANAS")),
                L("Gestos del borde", "Zona sensible del borde derecho", "gestos borde ancho alto zona navegación tres botones",
                    sections("Gestos del borde", false, "INTERFAZ LATERAL · GESTOS"))),
            new Category("launcher", "▨", "Launcher", "Launcher radial: arcos, tamaño y forma",
                L("Launcher radial", "Arcos, tamaño y forma", "launcher radial arcos dock escritorio ocultar apps categorías",
                    act(LauncherSettingsActivity.class)))),

        new Section("Herramientas",
            new Category("game", "◍", "Game Control", "Modo juego, optimización y perfiles",
                L("Game Control", "Activación, watchdog y perfiles", "juegos game control modo juego optimización watchdog perfiles medidor",
                    act(GamesActivity.class))),
            new Category("auto", "⚙", "Automatización", "Reglas y acciones",
                L("Reglas automáticas", "Tema por horario y batería baja", "reglas automáticas tema oscuro horario batería baja rojo",
                    sections("Reglas automáticas", false, "REGLAS AUTOMÁTICAS"))),
            new Category("tools", "◈", "Herramientas", "Copiar, pegar y aleatorizar la configuración",
                L("Configuración", "Copiar, pegar, aleatorizar y restablecer", "herramientas copiar pegar configuración aleatorizar restablecer exportar importar",
                    sections("Herramientas", false, "HERRAMIENTAS")))),

        new Section("Administración",
            new Category("perm", "◐", "Permisos", "Estado, concesión y accesos directos",
                L("Permisos y estado", "Servicio, accesibilidad y Shizuku", "permisos estado servicio accesibilidad shizuku no molestar modificar ajustes suplantar panel del sistema restablecer",
                    act(MainActivity.class))),
            new Category("svc", "⚙", "Servicios", "Servicio de accesibilidad",
                L("Servicio de accesibilidad", "Necesario para el panel", "servicios accesibilidad activar",
                    intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))),
            new Category("about", "ℹ", "Acerca de", "Información de ZC Control",
                L("Versión", "ZC Control", "acerca de versión información",
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
