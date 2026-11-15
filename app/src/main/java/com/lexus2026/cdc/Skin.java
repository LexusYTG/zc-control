package com.lexus2026.cdc;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Configuración de la barra, creada por el usuario en "Make your action bar".
 * Se guarda en SharedPreferences con prefijo "sk_". Los valores por defecto viven solo acá.
 */
public class Skin {
    /** Valores por defecto. Los colores con 0 significan "automático". */
    public static final Map<String, Integer> D;

    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        // colores y tema
        m.put("theme", 0); m.put("accent", 0); m.put("tileOff", 0); m.put("offAlpha", 100); m.put("onAlpha", 100); m.put("textColor", 0);
        // botones
        m.put("shape", 1); m.put("round", 34); m.put("layout", 2); m.put("cols", 2); m.put("size", 62); m.put("gap", 8);
        m.put("iconScale", 52); m.put("labelSize", 12); m.put("showSub", 1);
        // fondo detrás del panel
        m.put("bgMode", 2); m.put("bgColor", 0xFF000000); m.put("dim", 35); m.put("blurR", 4); m.put("imgBlur", 0);
        // hoja
        m.put("sheetColor", 0); m.put("sheetAlpha", 84); m.put("sheetRadius", 28);
        // reloj
        m.put("clockSize", 36); m.put("clockAlign", 0); m.put("clockWeight", 1); m.put("showDate", 1); m.put("showGear", 0);
        // controles
        m.put("sliderStyle", 0); m.put("sliderThick", 60); m.put("compact", 4); m.put("showMedia", 1);
        // tarjetas y notificaciones
        m.put("cardR", 22); m.put("cardAlpha", 100); m.put("notifApp", 0);
        // barrita de acceso
        m.put("pillW", 72); m.put("pillH", 4);
        m.put("fontFamily", 0); m.put("textScale", 100); m.put("textBold", 0); m.put("textItalic", 0); m.put("letterSp", 0);
        m.put("labelCaps", 0); m.put("labelColor", 0); m.put("subColor", 0); m.put("onFg", 0);
        m.put("clockFormat", 0); m.put("showSeconds", 0); m.put("dateFormat", 0); m.put("dateSize", 13);
        m.put("clockColor", 0); m.put("dateColor", 0); m.put("clockItalic", 0); m.put("clockFont", 0);
        m.put("clockPadV", 8); m.put("gearSize", 44); m.put("showEdit", 1);
        m.put("tileStroke", 0); m.put("strokeColor", 0); m.put("tileRot", 0); m.put("onScale", 100); m.put("offScale", 100); m.put("pillRound", 100);
        m.put("sheetColor2", 0); m.put("sheetAngle", 0); m.put("sheetStroke", 0); m.put("sheetStrokeColor", 0);
        m.put("sheetTopRadius", 0); m.put("sheetWidth", 100); m.put("sidePad", 12); m.put("topPad", 4);
        m.put("animDur", 220); m.put("animStyle", 0); m.put("tabAnimDur", 200);
        m.put("vCorner", 21);
        m.put("cardColor", 0); m.put("cardStroke", 0); m.put("cardStrokeColor", 0);
        m.put("notifLines", 2); m.put("notifOpenLines", 8); m.put("notifTitleSize", 14); m.put("notifTextSize", 13);
        m.put("notifShowTime", 1); m.put("notifGap", 6); m.put("notifMaxH", 34); m.put("notifIconSize", 36);
        m.put("mediaH", 130); m.put("showClear", 1);
        m.put("pillColor", 0); m.put("pillRadius", 4); m.put("pillTop", 3); m.put("handleH", 28); m.put("openThr", 12);
        m.put("closeTap", 1); m.put("closeOnAct", 1); m.put("haptic", 0); m.put("startStage", 0); m.put("defaultTab", 0);
        m.put("showTabs", 1); m.put("showGrab", 1); m.put("grabW", 48); m.put("grabH", 5);
        m.put("gestTap", 0); m.put("gestDouble", 0); m.put("gestLong", 0); m.put("fabMode", 1); m.put("fabSize", 52);
        m.put("layoutMode", 0); m.put("canvasCols", 8); m.put("tapAnim", 0); m.put("entryAnim", 0); m.put("autoTheme", 0); m.put("autoFrom", 20); m.put("autoTo", 7); m.put("lowBatRed", 0);
        D = Collections.unmodifiableMap(m);
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cc", Context.MODE_PRIVATE);
    }

    private static String k(String key) { return "sk_" + key; }

    private static int def(String key) {
        Integer v = D.get(key);
        return v != null ? v : 0;
    }

    public static int get(Context c, String key) { return prefs(c).getInt(k(key), def(key)); }

    public static void set(Context c, String key, int v) { prefs(c).edit().putInt(k(key), v).apply(); }

    public static String getStr(Context c, String key, String fallback) { return prefs(c).getString("ss_" + key, fallback); }

    public static void setStr(Context c, String key, String v) { prefs(c).edit().putString("ss_" + key, v).apply(); }

    public static void reset(Context c) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        for (String key : new ArrayList<>(p.getAll().keySet())) {
            if (key.startsWith("sk_") || (key.startsWith("ss_") && !key.equals("ss_customTiles") && !key.equals("ss_canvas") && !key.startsWith("ss_preset"))) e.remove(key);
        }
        e.apply();
    }

    public static String export(Context c) {
        StringBuilder sb = new StringBuilder("ZCSKIN1\n");
        for (Map.Entry<String, ?> en : prefs(c).getAll().entrySet()) {
            String key = en.getKey();
            Object v = en.getValue();
            if (key.startsWith("sk_") && v instanceof Integer) sb.append(key).append('=').append(v).append('\n');
            else if (key.startsWith("ss_") && !key.startsWith("ss_preset") && !key.equals("ss_customTiles") && !key.equals("ss_canvas") && v instanceof String) sb.append(key).append('=').append(((String) v).replace('\n', ' ')).append('\n');
        }
        return sb.toString();
    }

    public static int importAll(Context c, String text) {
        if (text == null || !text.startsWith("ZCSKIN1")) return -1;
        SharedPreferences.Editor e = prefs(c).edit();
        int n = 0;
        for (String line : text.split("\n")) {
            int i = line.indexOf('=');
            if (i <= 3) continue;
            String key = line.substring(0, i);
            String val = line.substring(i + 1);
            if (key.startsWith("sk_")) {
                try { e.putInt(key, Integer.parseInt(val.trim())); n++; } catch (NumberFormatException x) {}
            } else if (key.startsWith("ss_")) {
                e.putString(key, val); n++;
            }
        }
        e.apply();
        return n;
    }

    public static void randomize(Context c) {
        Random r = new Random();
        SharedPreferences.Editor e = prefs(c).edit();
        String[] colors = {"accent", "tileOff", "textColor", "sheetColor", "sheetColor2", "bgColor", "clockColor", "dateColor",
            "strokeColor", "labelColor", "cardColor", "sheetStrokeColor", "pillColor", "cardStrokeColor", "subColor", "onFg"};
        for (String key : colors) e.putInt(k(key), r.nextInt(4) == 0 ? 0 : (0xFF000000 | r.nextInt(0x1000000)));
        int[][] ranges = {
            {0, 6}, {0, 50}, {0, 2}, {2, 6}, {40, 100}, {0, 20}, {35, 75}, {9, 15}, {0, 2}, {0, 80}, {1, 12}, {30, 100}, {0, 48}, {0, 30},
            {0, 3}, {0, 5}, {20, 80}, {0, 2}, {0, 2}, {0, 7}, {80, 130}, {0, 20}, {0, 5}, {0, 20}, {90, 120}, {80, 110}, {0, 40}, {0, 4},
            {0, 5}, {120, 600}, {10, 40}, {0, 1}, {0, 12}, {70, 100}, {4, 28}, {0, 100}
        };
        String[] names = {"shape", "round", "layout", "cols", "size", "gap", "iconScale", "labelSize", "bgMode", "dim", "blurR", "sheetAlpha",
            "sheetRadius", "sheetTopRadius", "sheetAngle", "sheetStroke", "clockSize", "clockAlign", "clockWeight", "fontFamily", "textScale",
            "letterSp", "tileStroke", "tileRot", "onScale", "offScale", "cardR", "cardStroke", "animStyle", "animDur", "vCorner", "sliderStyle",
            "pillRadius", "sheetWidth", "sidePad", "pillRound"};
        for (int i = 0; i < names.length; i++) e.putInt(k(names[i]), ranges[i][0] + r.nextInt(ranges[i][1] - ranges[i][0] + 1));
        e.apply();
    }

    public static final String[] PRESETS = {"Cristal", "Neón", "Terminal", "Papel", "Brutalista", "AMOLED"};

    private static void put(SharedPreferences.Editor e, String key, int v) { e.putInt(k(key), v); }

    public static void applyPreset(Context c, int idx) {
        reset(c);
        SharedPreferences.Editor e = prefs(c).edit();
        switch (idx) {
            case 0:
                put(e, "theme", 2); put(e, "bgMode", 2); put(e, "blurR", 8); put(e, "dim", 20); put(e, "sheetAlpha", 30); put(e, "sheetRadius", 36);
                put(e, "sheetStroke", 1); put(e, "cardAlpha", 40); put(e, "offAlpha", 35); put(e, "onAlpha", 85); put(e, "shape", 1); put(e, "round", 40);
                put(e, "layout", 2); put(e, "tileStroke", 1); put(e, "accent", 0xFF8AB4FF); put(e, "animStyle", 1);
                break;
            case 1:
                put(e, "theme", 2); put(e, "bgMode", 0); put(e, "bgColor", 0xFF05050F); put(e, "dim", 96); put(e, "sheetColor", 0xFF0A0A1A);
                put(e, "sheetAlpha", 95); put(e, "sheetStroke", 2); put(e, "sheetStrokeColor", 0xFFFF2BD6); put(e, "accent", 0xFF00F0FF);
                put(e, "tileOff", 0xFF14142B); put(e, "tileStroke", 2); put(e, "strokeColor", 0xFF00F0FF); put(e, "textColor", 0xFFE6FFFF);
                put(e, "shape", 5); put(e, "layout", 0); put(e, "size", 64); put(e, "onScale", 112); put(e, "animStyle", 2); put(e, "fontFamily", 2);
                put(e, "clockColor", 0xFFFF2BD6); put(e, "tapAnim", 5); put(e, "entryAnim", 2);
                break;
            case 2:
                put(e, "theme", 2); put(e, "bgMode", 0); put(e, "bgColor", 0xFF000000); put(e, "dim", 100); put(e, "sheetColor", 0xFF000000);
                put(e, "sheetAlpha", 100); put(e, "sheetRadius", 0); put(e, "accent", 0xFF33FF33); put(e, "tileOff", 0xFF0B1A0B);
                put(e, "textColor", 0xFF33FF33); put(e, "shape", 2); put(e, "round", 0); put(e, "layout", 1); put(e, "fontFamily", 2); put(e, "cardR", 0);
                put(e, "cardStroke", 1); put(e, "cardStrokeColor", 0xFF33FF33); put(e, "clockWeight", 0); put(e, "tileStroke", 1);
                put(e, "strokeColor", 0xFF33FF33); put(e, "labelCaps", 1); put(e, "animDur", 80); put(e, "animStyle", 4); put(e, "onFg", 0xFF000000);
                break;
            case 3:
                put(e, "theme", 1); put(e, "bgMode", 0); put(e, "bgColor", 0xFFFFFFFF); put(e, "dim", 40); put(e, "sheetColor", 0xFFFAF3E0);
                put(e, "sheetAlpha", 100); put(e, "sheetRadius", 12); put(e, "accent", 0xFF8B5E34); put(e, "tileOff", 0xFFEADCC0);
                put(e, "textColor", 0xFF3E2C1C); put(e, "fontFamily", 1); put(e, "round", 20); put(e, "layout", 1); put(e, "cardColor", 0xFFF1E6CC);
                put(e, "cardR", 10); put(e, "clockWeight", 0);
                break;
            case 4:
                put(e, "theme", 1); put(e, "bgMode", 0); put(e, "bgColor", 0xFFFFE600); put(e, "dim", 100); put(e, "sheetColor", 0xFFFFFFFF);
                put(e, "sheetAlpha", 100); put(e, "sheetRadius", 0); put(e, "sheetStroke", 4); put(e, "sheetStrokeColor", 0xFF000000);
                put(e, "accent", 0xFF000000); put(e, "tileOff", 0xFFFFFFFF); put(e, "tileStroke", 3); put(e, "strokeColor", 0xFF000000);
                put(e, "textColor", 0xFF000000); put(e, "onFg", 0xFFFFFFFF); put(e, "shape", 2); put(e, "round", 0); put(e, "layout", 1);
                put(e, "cardR", 0); put(e, "cardStroke", 3); put(e, "cardStrokeColor", 0xFF000000); put(e, "fontFamily", 2); put(e, "textBold", 1);
                put(e, "labelCaps", 1); put(e, "animStyle", 3); put(e, "tapAnim", 4);
                break;
            default:
                put(e, "theme", 2); put(e, "bgMode", 0); put(e, "bgColor", 0xFF000000); put(e, "dim", 100); put(e, "sheetColor", 0xFF000000);
                put(e, "sheetAlpha", 100); put(e, "tileOff", 0xFF1A1A1A); put(e, "cardColor", 0xFF111111); put(e, "accent", 0xFFFFFFFF);
                put(e, "onFg", 0xFF000000);
                break;
        }
        e.apply();
    }

    public static void saveSlot(Context c, int slot) { setStr(c, "preset" + slot, export(c)); }

    public static int loadSlot(Context c, int slot) {
        String t = getStr(c, "preset" + slot, null);
        if (t == null || t.isEmpty()) return -1;
        reset(c);
        return importAll(c, t);
    }

    private static String clean(String s) { return s == null ? "" : s.replace('\n', ' ').replace('\u001F', ' '); }

    public static List<String[]> customTiles(Context c) {
        List<String[]> out = new ArrayList<>();
        String raw = getStr(c, "customTiles", "");
        if (raw == null) return out;
        for (String line : raw.split("\n")) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\u001F", -1);
            if (f.length >= 4) out.add(new String[]{f[0], f[1], f[2], f[3]});
        }
        return out;
    }

    public static void saveCustomTiles(Context c, List<String[]> l) {
        StringBuilder sb = new StringBuilder();
        for (String[] f : l) {
            sb.append(clean(f[0])).append('\u001F').append(clean(f[1])).append('\u001F').append(clean(f[2])).append('\u001F').append(clean(f[3])).append('\n');
        }
        setStr(c, "customTiles", sb.toString());
    }

    public static File imageFile(Context c) { return new File(c.getFilesDir(), "bg_custom.jpg"); }

    private final SharedPreferences p;

    private int i(String key) { return p.getInt(k(key), def(key)); }

    private boolean b(String key) { return i(key) != 0; }

    private static <T> T pick(T[] a, int idx, T fallback) {
        return idx >= 0 && idx < a.length ? a[idx] : fallback;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public final int theme;
    public final int accent;
    public final int tileOff;
    public final int offAlpha;
    public final int onAlpha;
    public final int textColor;

    public final TileShape shape;
    public final int round;
    public final TileLayout layout;
    public final int cols;
    public final int size;
    public final int gap;
    public final int iconScale;
    public final int labelSize;
    public final boolean showSub;

    public final BgMode bgMode;
    public final int bgColor;
    public final int dim;
    public final int blurR;
    public final int imgBlur;

    public final int sheetColor;
    public final int sheetAlpha;
    public final int sheetRadius;

    public final int clockSize;
    public final int clockAlign;
    public final int clockWeight;
    public final boolean showDate;
    public final boolean showGear;

    public final int sliderStyle;
    public final int sliderThick;
    public final int compact;
    public final boolean showMedia;

    public final int cardR;
    public final int cardAlpha;
    public final boolean notifApp;

    public final int fontFamily, textScale, textBold, textItalic, letterSp, labelCaps, labelColor, subColor, onFg;
    public final int clockFormat, showSeconds, dateFormat, dateSize, clockColor, dateColor, clockItalic, clockFont, clockPadV, gearSize, showEdit;
    public final int tileStroke, strokeColor, tileRot, onScale, offScale, pillRound;
    public final int sheetColor2, sheetAngle, sheetStroke, sheetStrokeColor, sheetTopRadius, sheetWidth, sidePad, topPad;
    public final int animDur, animStyle, tabAnimDur, vCorner;
    public final int cardColor, cardStroke, cardStrokeColor;
    public final int notifLines, notifOpenLines, notifTitleSize, notifTextSize, notifShowTime, notifGap, notifMaxH, notifIconSize;
    public final int mediaH, showClear;
    public final int pillColor, pillRadius, pillTop, handleH, openThr;
    public final int closeTap, closeOnAct, haptic, startStage, defaultTab, showTabs, showGrab, grabW, grabH;
    public final int layoutMode, canvasCols;
    public final int tapAnim, entryAnim, autoTheme, autoFrom, autoTo, lowBatRed, fabMode, fabSize;

    public Skin(Context c) {
        p = prefs(c);
        theme = i("theme");                     // 0 sistema, 1 claro, 2 oscuro
        accent = i("accent");
        tileOff = i("tileOff");
        offAlpha = i("offAlpha");
        onAlpha = i("onAlpha");
        textColor = i("textColor");

        shape = pick(TileShape.values(), i("shape"), TileShape.ROUNDED);
        round = i("round");
        layout = pick(TileLayout.values(), i("layout"), TileLayout.PILL);
        cols = clamp(i("cols"), 2, 6);
        size = clamp(i("size"), 32, 120);
        gap = i("gap");
        iconScale = i("iconScale");
        labelSize = i("labelSize");
        showSub = b("showSub");

        bgMode = pick(BgMode.values(), i("bgMode"), BgMode.BLUR);
        bgColor = i("bgColor");
        dim = i("dim");
        blurR = clamp(i("blurR"), 1, 12);
        imgBlur = i("imgBlur");

        sheetColor = i("sheetColor");
        sheetAlpha = i("sheetAlpha");
        sheetRadius = i("sheetRadius");

        clockSize = i("clockSize");
        clockAlign = i("clockAlign");           // 0 izquierda, 1 centro, 2 derecha
        clockWeight = i("clockWeight");         // 0 fino, 1 normal, 2 negrita
        showDate = b("showDate");
        showGear = b("showGear");

        sliderStyle = i("sliderStyle");         // 0 horizontal, 1 vertical
        sliderThick = i("sliderThick");
        compact = i("compact");
        showMedia = b("showMedia");

        cardR = i("cardR");
        cardAlpha = i("cardAlpha");
        notifApp = b("notifApp");

        fontFamily = clamp(i("fontFamily"), 0, 7);
        textScale = clamp(i("textScale"), 30, 300);
        textBold = i("textBold");
        textItalic = i("textItalic");
        letterSp = i("letterSp");
        labelCaps = i("labelCaps");
        labelColor = i("labelColor");
        subColor = i("subColor");
        onFg = i("onFg");

        clockFormat = i("clockFormat");
        showSeconds = i("showSeconds");
        dateFormat = i("dateFormat");
        dateSize = clamp(i("dateSize"), 6, 48);
        clockColor = i("clockColor");
        dateColor = i("dateColor");
        clockItalic = i("clockItalic");
        clockFont = clamp(i("clockFont"), 0, 8);
        clockPadV = clamp(i("clockPadV"), 0, 120);
        gearSize = clamp(i("gearSize"), 16, 120);
        showEdit = i("showEdit");

        tileStroke = i("tileStroke");
        strokeColor = i("strokeColor");
        tileRot = i("tileRot");
        onScale = clamp(i("onScale"), 20, 200);
        offScale = clamp(i("offScale"), 20, 200);
        pillRound = clamp(i("pillRound"), 0, 100);

        sheetColor2 = i("sheetColor2");
        sheetAngle = i("sheetAngle");
        sheetStroke = i("sheetStroke");
        sheetStrokeColor = i("sheetStrokeColor");
        sheetTopRadius = i("sheetTopRadius");
        sheetWidth = clamp(i("sheetWidth"), 30, 100);
        sidePad = clamp(i("sidePad"), 0, 80);
        topPad = clamp(i("topPad"), 0, 300);

        animDur = clamp(i("animDur"), 0, 3000);
        animStyle = i("animStyle");
        tabAnimDur = clamp(i("tabAnimDur"), 0, 3000);
        vCorner = clamp(i("vCorner"), 10, 60);

        cardColor = i("cardColor");
        cardStroke = i("cardStroke");
        cardStrokeColor = i("cardStrokeColor");
        notifLines = clamp(i("notifLines"), 1, 20);
        notifOpenLines = clamp(i("notifOpenLines"), 1, 40);
        notifTitleSize = clamp(i("notifTitleSize"), 6, 40);
        notifTextSize = clamp(i("notifTextSize"), 6, 40);
        notifShowTime = i("notifShowTime");
        notifGap = i("notifGap");
        notifMaxH = clamp(i("notifMaxH"), 5, 90);
        notifIconSize = clamp(i("notifIconSize"), 12, 120);

        mediaH = clamp(i("mediaH"), 40, 400);
        showClear = i("showClear");

        pillColor = i("pillColor");
        pillRadius = i("pillRadius");
        pillTop = i("pillTop");
        handleH = clamp(i("handleH"), 8, 300);
        openThr = clamp(i("openThr"), 1, 300);

        closeTap = i("closeTap");
        closeOnAct = i("closeOnAct");
        haptic = i("haptic");
        startStage = i("startStage");
        defaultTab = i("defaultTab") != 0 ? 1 : 0;
        showTabs = i("showTabs");
        showGrab = i("showGrab");
        grabW = clamp(i("grabW"), 8, 400);
        grabH = clamp(i("grabH"), 1, 40);
        layoutMode = i("layoutMode");
        canvasCols = clamp(i("canvasCols"), 3, 24);
        tapAnim = i("tapAnim");
        entryAnim = i("entryAnim");
        autoTheme = i("autoTheme");
        autoFrom = clamp(i("autoFrom"), 0, 23);
        autoTo = clamp(i("autoTo"), 0, 23);
        lowBatRed = i("lowBatRed");
        fabMode = i("fabMode");
        fabSize = clamp(i("fabSize"), 24, 120);
    }
}
