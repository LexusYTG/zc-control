package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.content.ClipData;
import android.content.Context;
import android.content.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Página genérica de ajustes. Se dibuja a partir de un nodo de {@link SettingsTree}: las ramas hijas son filas
 * que abren otra página y las hojas son los controles reales, en el orden exacto del árbol. Todo se guarda al instante.
 */
public class SettingsPageActivity extends Activity {
    public static final String EXTRA_PATH = "path";

    private static final int MPB = -1;
    private static final int WCB = -2;
    private static final int REQ_IMG = 42;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout previewBox;
    private PanelView preview = null;
    private LinearLayout controls;
    private ScrollView scroll;
    private int stage = 2;
    private int tab = 0;
    private boolean pendingHandle = false;
    private int selTile = 0;
    private boolean keepZero = false;
    private SettingsTree.Node node;       // página cuyos hijos se dibujan
    private String leafOnly = null;        // si se abrió una hoja suelta: solo se dibuja esa
    private boolean withPreview;

    private final int txt = ZUi.TXT;
    private final int sub = ZUi.SUB;
    private final int acc = ZUi.ACC;
    private final int chipOff = ZUi.CARD_HI;
    private final int chipOn = 0xFF3B54B8;

    /** Abre la página de un nodo del árbol. */
    public static void open(Context c, SettingsTree.Node n) {
        Intent it = new Intent(c, SettingsPageActivity.class).putExtra(EXTRA_PATH, n.path());
        if (!(c instanceof Activity)) it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(it);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private final Runnable rebuildRunnable = () -> {
        doRebuildPreview();
        if (pendingHandle) {
            pendingHandle = false;
            ControlService s = ControlService.instance;
            if (s != null) s.rebuildHandle();
        }
    };

    private void changed() { changed(false); }

    /** Se llama en cada cambio; junta varios cambios seguidos (por ejemplo, arrastrar un slider) en una sola reconstrucción. */
    private void changed(boolean handle) {
        if (handle) pendingHandle = true;
        ui.removeCallbacks(rebuildRunnable);
        ui.postDelayed(rebuildRunnable, 120);
    }

    private void stateBtn(LinearLayout bar, String label, final int s, final int t) {
        Button bt = new Button(this);
        bt.setText(label);
        bt.setAllCaps(false);
        bt.setTextSize(12f);
        bt.setPadding(0, 0, 0, 0);
        bt.setOnClickListener(v -> { stage = s; tab = t; if (preview != null) preview.previewState(stage, tab); });
        bar.addView(bt, new LinearLayout.LayoutParams(0, WCB, 1f));
    }


    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final SettingsTree.Node shown = SettingsTree.find(getIntent() != null ? getIntent().getStringExtra(EXTRA_PATH) : null);
        if (shown == null || shown.isAction() || shown.parent == null && !shown.isBranch()) { finish(); return; }
        if (shown.isLeaf()) { leafOnly = shown.id; node = shown.parent; } else node = shown;
        withPreview = SettingsTree.hasPreview(shown);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ZUi.BG);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(14), dp(16), dp(8));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(28f);
        back.setTextColor(txt);
        back.setPadding(0, 0, dp(14), dp(2));
        back.setOnClickListener(v -> finish());
        head.addView(back);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(shown.title);
        title.setTextSize(20f);
        title.setTextColor(txt);
        title.setTypeface(null, Typeface.BOLD);
        titles.addView(title);
        String crumbs = shown.crumbs();
        if (!crumbs.isEmpty()) titles.addView(label(crumbs, 12f, sub, 0));
        head.addView(titles, new LinearLayout.LayoutParams(0, WCB, 1f));
        root.addView(head);

        // ---- vista previa (solo en las páginas que afectan al panel) ----
        previewBox = new FrameLayout(this);
        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setImageBitmap(demoBitmap(360, 640));
        previewBox.addView(bg, new FrameLayout.LayoutParams(MPB, MPB));
        // Capa que se come los toques: la vista previa no se maneja, se mira.
        View shield = new View(this);
        shield.setClickable(true);
        previewBox.addView(shield, new FrameLayout.LayoutParams(MPB, MPB));
        root.addView(previewBox, new LinearLayout.LayoutParams(MPB, (int) (getResources().getDisplayMetrics().heightPixels * 0.30f)));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        stateBtn(bar, "Acotada", 1, 0); stateBtn(bar, "Completa", 2, 0); stateBtn(bar, "Controles", 2, 1);
        Button real = new Button(this);
        real.setText("▶ Real");
        real.setAllCaps(false);
        real.setTextSize(12f);
        real.setPadding(0, 0, 0, 0);
        real.setOnClickListener(v -> {
            ControlService svc = ControlService.instance;
            if (svc != null) svc.open();
            else Toast.makeText(SettingsPageActivity.this, "El servicio todavía no está activo", Toast.LENGTH_LONG).show();
        });
        bar.addView(real, new LinearLayout.LayoutParams(0, WCB, 1f));
        Button eye = new Button(this);
        eye.setText("👁");
        eye.setAllCaps(false);
        eye.setTextSize(12f);
        eye.setPadding(0, 0, 0, 0);
        eye.setOnClickListener(v -> previewBox.setVisibility(previewBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        bar.addView(eye, new LinearLayout.LayoutParams(0, WCB, 0.6f));
        root.addView(bar);
        if (!withPreview) { previewBox.setVisibility(View.GONE); bar.setVisibility(View.GONE); }

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(18), 0, dp(18), dp(40));
        scroll = new ScrollView(this);
        scroll.addView(controls);
        root.addView(scroll, new LinearLayout.LayoutParams(MPB, 0, 1f));
        Insets.apply(root);
        setContentView(root);

        buildControls();
        doRebuildPreview();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(rebuildRunnable);
        super.onDestroy();
    }

    // ======================= vista previa =======================

    private void doRebuildPreview() {
        if (!withPreview) return;
        if (preview != null) previewBox.removeView(preview);
        final PanelView p = new PanelView(this, () -> { });
        preview = p;
        previewBox.addView(p, 1, new FrameLayout.LayoutParams(MPB, MPB));
        p.post(() -> {
            p.previewState(stage, tab);
            if (new Skin(this).bgMode == BgMode.BLUR) {
                Bitmap small = Bitmap.createScaledBitmap(demoBitmap(360, 640), 96, 170, true);
                p.setBackdrop(Blur.apply(small, Skin.get(this, "blurR"), 3));
            }
        });
    }

    /** "Pantalla de inicio" de mentira para que se note la transparencia y el difuminado. */
    private Bitmap demoBitmap(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0f, 0f, (float) w, (float) h,
            new int[]{0xFF1E3C72, 0xFFB24592, 0xFFF15F79}, null, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, (float) w, (float) h, p);
        p.setShader(null);
        int[] cols = {0xFFFFD54F, 0xFF4DD0E1, 0xFF81C784, 0xFFFFFFFF};
        float cell = w / 4f;
        for (int r = 0; r < 8; r++) {
            for (int q = 0; q < 4; q++) {
                p.setColor(cols[(r + q) % 4]);
                float l = q * cell + cell * 0.2f;
                float t = r * cell + cell * 0.2f;
                c.drawRoundRect(new RectF(l, t, l + cell * 0.6f, t + cell * 0.6f), cell * 0.16f, cell * 0.16f, p);
            }
        }
        return bmp;
    }


    // ======================= controles =======================

    private TextView label(String text) { return label(text, 14f, txt, 8); }

    private TextView label(String text, float size) { return label(text, size, txt, 8); }

    private TextView label(String text, float size, int color, int top) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, dp(top), 0, 0);
        return t;
    }


    /** Todos los controles se dibujan siempre (no hay paginado). */
    private boolean item(int w) { return true; }

    private void note(String text) {
        if (!item(0)) return;
        controls.addView(label(text, 12f, sub, 4));
    }

    private void slider(String name, int lo, int hi, String unit, boolean handle, IntSupplier getV, IntConsumer setV) {
        slider(name, lo, hi, unit, handle, getV, setV, null);
    }

    private void slider(final String name, final int lo, int hi, final String unit, final boolean handle,
                        IntSupplier getV, final IntConsumer setV, final IntFunction<String> show) {
        if (!item(1)) return;
        final TextView tv = label("", 14f);
        final IntFunction<String> fmt = v -> name + ": " + (show != null ? show.apply(v) : v + unit);
        int cur = Math.max(lo, Math.min(hi, getV.getAsInt()));
        tv.setText(fmt.apply(cur));
        controls.addView(tv);
        SeekBar sb = new SeekBar(this);
        sb.setMax(hi - lo);
        sb.setProgress(cur - lo);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = p + lo;
                tv.setText(fmt.apply(v));
                if (fromUser) { setV.accept(v); changed(handle); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        controls.addView(sb);
    }

    private void skSlider(String name, String key, int lo, int hi) { skSlider(name, key, lo, hi, "", false); }

    private void skSlider(String name, String key, int lo, int hi, String unit) { skSlider(name, key, lo, hi, unit, false); }

    private void skSlider(String name, String key, int lo, int hi, String unit, boolean handle) {
        slider(name, lo, hi, unit, handle, () -> Skin.get(this, key), v -> Skin.set(this, key, v));
    }

    private void chips(List<String> options, int selected, IntConsumer onPick) {
        if (!item(1)) return;
        chipsRaw(options, selected, onPick);
    }

    private void chipsRow(String name, List<String> options, int selected, IntConsumer onPick) {
        if (!item(1)) return;
        controls.addView(label(name));
        chipsRaw(options, selected, onPick);
    }

    private void chipsRaw(List<String> options, int selected, final IntConsumer onPick) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(6), 0, dp(2));
        final List<TextView> views = new ArrayList<>();
        final IntConsumer paint = sel -> {
            for (int i = 0; i < views.size(); i++) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(i == sel ? chipOn : chipOff);
                g.setCornerRadius((float) dp(18));
                views.get(i).setBackground(g);
            }
        };
        for (int i = 0; i < options.size(); i++) {
            final int idx = i;
            TextView v = new TextView(this);
            v.setText(options.get(i));
            v.setTextSize(13f);
            v.setTextColor(txt);
            v.setGravity(Gravity.CENTER);
            v.setPadding(dp(14), dp(9), dp(14), dp(9));
            v.setOnClickListener(x -> { paint.accept(idx); onPick.accept(idx); });
            views.add(v);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WCB, WCB);
            lp.rightMargin = dp(8);
            row.addView(v, lp);
        }
        paint.accept(selected);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        controls.addView(hs);
    }

    private void choice(String name, String key, List<String> options) { choice(name, key, options, false, null); }

    private void choice(String name, String key, List<String> options, boolean rebuild) { choice(name, key, options, rebuild, null); }

    /** Opciones de un valor entero del Skin. Si [rebuild], se rearma la lista (hay controles que dependen de esta opción). */
    private void choice(String name, String key, List<String> options, final boolean rebuild, final IntConsumer extra) {
        if (!item(1)) return;
        controls.addView(label(name));
        chipsRaw(options, Skin.get(this, key), i -> {
            Skin.set(this, key, i);
            if (extra != null) extra.accept(i);
            if (rebuild) buildControls();
            changed();
        });
    }

    private void textInput(String name, final String key, String def) {
        if (!item(1)) return;
        controls.addView(label(name));
        EditText et = new EditText(this);
        et.setText(Skin.getStr(this, key, def));
        et.setTextColor(txt);
        et.setHintTextColor(sub);
        et.setSingleLine(true);
        et.setTextSize(14f);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                Skin.setStr(SettingsPageActivity.this, key, s.toString());
                changed();
            }
        });
        controls.addView(et);
    }

    private static int parseInt0(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private void textEdit(String name, String initial, final Consumer<String> onChange) {
        if (!item(1)) return;
        controls.addView(label(name));
        EditText et = new EditText(this);
        et.setText(initial);
        et.setTextColor(txt);
        et.setSingleLine(true);
        et.setTextSize(14f);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { onChange.accept(s.toString()); }
        });
        controls.addView(et);
    }

    private void enableTile(String key) {
        android.content.SharedPreferences sp = Skin.prefs(this);
        String raw = sp.getString("tiles_custom", null);
        if (raw == null) raw = sp.getString("tiles_aosp", null);
        if (raw == null) raw = String.join("\n", PanelView.TILE_KEYS);
        sp.edit().putString("tiles_custom", raw + "\n" + key).apply();
    }

    private void toolBtn(String text, final Runnable action) {
        if (!item(1)) return;
        Button bt = new Button(this);
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MPB, WCB);
        lp.topMargin = dp(8);
        controls.addView(bt, lp);
    }

    private void toggle(String name, final String key) {
        if (!item(1)) return;
        Switch sw = new Switch(this);
        sw.setText(name);
        sw.setTextSize(14f);
        sw.setTextColor(txt);
        sw.setPadding(0, dp(10), 0, dp(2));
        sw.setChecked(Skin.get(this, key) != 0);
        sw.setOnCheckedChangeListener((v, c) -> { Skin.set(SettingsPageActivity.this, key, c ? 1 : 0); changed(); });
        controls.addView(sw);
    }

    private final int[] palette = {
        0xFF6750A4, 0xFF3482FF, 0xFF2BC48A, 0xFFFF9F0A, 0xFFE53935,
        0xFFFF4081, 0xFFFFC107, 0xFF00BCD4, 0xFFFFFFFF, 0xFF8E8E93, 0xFF1C1C1E
    };

    private void colorChip(LinearLayout sw, List<View> views, List<Integer> colors, Runnable paint, String key, int c, String text) {
        TextView v = new TextView(this);
        v.setGravity(Gravity.CENTER);
        v.setText(text);
        v.setTextSize(13f);
        v.setTextColor(0xFFFFFFFF);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(c == 0 ? 0xFF444444 : c);
        v.setBackground(g);
        v.setOnClickListener(x -> { Skin.set(SettingsPageActivity.this, key, c); paint.run(); changed(key.startsWith("pill")); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.rightMargin = dp(8);
        sw.addView(v, lp);
        views.add(v);
        colors.add(c);
    }

    private void hsvBar(LinearLayout custom, final float[] hsv, final String key, final Runnable paint,
                        String n, int max, int init, final int idx, final float scale) {
        custom.addView(label(n, 12f, sub, 2));
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(init);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                hsv[idx] = p * scale;
                Skin.set(SettingsPageActivity.this, key, Color.HSVToColor(hsv));
                paint.run(); changed(key.startsWith("pill"));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        custom.addView(sb);
    }

    private void colorRow(String name, final String key, boolean allowAuto) {
        if (!item(2)) return;
        controls.addView(label(name));
        final LinearLayout sw = new LinearLayout(this);
        sw.setPadding(0, dp(6), 0, dp(4));
        final List<View> views = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();
        final IntSupplier cur = () -> Skin.get(this, key);
        final Runnable paint = () -> {
            for (int i = 0; i < views.size(); i++) {
                boolean sel = colors.get(i) == cur.getAsInt();
                ((GradientDrawable) views.get(i).getBackground()).setStroke(sel ? dp(3) : dp(1), sel ? 0xFFFFFFFF : 0x66FFFFFF);
            }
        };
        if (allowAuto) colorChip(sw, views, colors, paint, key, 0, "A");
        for (int c : palette) colorChip(sw, views, colors, paint, key, c, "");

        // Color personalizado con matiz / saturación / brillo
        final LinearLayout custom = new LinearLayout(this);
        custom.setOrientation(LinearLayout.VERTICAL);
        custom.setVisibility(View.GONE);
        float[] hsv = new float[3];
        Color.colorToHSV(cur.getAsInt() == 0 ? 0xFF6750A4 : cur.getAsInt(), hsv);
        hsvBar(custom, hsv, key, paint, "Matiz", 360, (int) hsv[0], 0, 1f);
        hsvBar(custom, hsv, key, paint, "Saturación", 100, (int) (hsv[1] * 100), 1, 0.01f);
        hsvBar(custom, hsv, key, paint, "Brillo", 100, (int) (hsv[2] * 100), 2, 0.01f);
        TextView plus = new TextView(this);
        plus.setGravity(Gravity.CENTER);
        plus.setText("+");
        plus.setTextSize(20f);
        plus.setTextColor(txt);
        GradientDrawable pg = new GradientDrawable();
        pg.setShape(GradientDrawable.OVAL);
        pg.setColor(chipOff);
        pg.setStroke(dp(1), 0x66FFFFFF);
        plus.setBackground(pg);
        plus.setOnClickListener(x -> custom.setVisibility(custom.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        sw.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(40)));
        paint.run();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(sw);
        controls.addView(hs);
        controls.addView(custom);
    }

    // ---- imagen de fondo ----
    private void imageRow() {
        if (!item(2)) return;
        boolean has = Skin.imageFile(this).exists();
        controls.addView(label(has ? "Imagen elegida ✓" : "Todavía no elegiste ninguna imagen", 13f, has ? txt : sub, 8));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(6), 0, dp(2));
        Button pick = new Button(this);
        pick.setText(has ? "Cambiar imagen" : "Elegir imagen");
        pick.setAllCaps(false);
        pick.setOnClickListener(v -> {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("image/*");
            it.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(it, REQ_IMG);
        });
        row.addView(pick, new LinearLayout.LayoutParams(0, WCB, 1f));
        if (has) {
            Button del = new Button(this);
            del.setText("Quitar");
            del.setAllCaps(false);
            del.setOnClickListener(v -> { Skin.imageFile(SettingsPageActivity.this).delete(); buildControls(); changed(); });
            row.addView(del, new LinearLayout.LayoutParams(0, WCB, 1f));
        }
        controls.addView(row);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMG || res != RESULT_OK) return;
        final Uri uri = data != null ? data.getData() : null;
        if (uri == null) return;
        new Thread(() -> {
            final boolean ok = saveImage(uri);
            ui.post(() -> {
                if (ok) {
                    Skin.set(this, "bgMode", BgMode.IMAGE.ordinal());
                    buildControls(); changed();
                } else Toast.makeText(this, "No se pudo usar esa imagen", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    /** Copia la imagen elegida a la app (reducida y con la rotación corregida), así no depende de permisos del selector. */
    private boolean saveImage(Uri uri) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) BitmapFactory.decodeStream(in, null, opts);
            }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return false;
            int sample = 1;
            while (Math.max(opts.outWidth, opts.outHeight) / sample > 2400) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bmp = null;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) bmp = BitmapFactory.decodeStream(in, null, o2);
            }
            if (bmp == null) return false;
            int orient = 1;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    try { orient = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1); } catch (Exception e) { orient = 1; }
                }
            }
            float deg = orient == 6 ? 90f : orient == 3 ? 180f : orient == 8 ? 270f : 0f;
            if (deg != 0f) {
                Matrix m = new Matrix();
                m.postRotate(deg);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }
            float k = 1600f / Math.max(bmp.getWidth(), bmp.getHeight());
            if (k < 1f) bmp = Bitmap.createScaledBitmap(bmp, Math.max(1, (int) (bmp.getWidth() * k)), Math.max(1, (int) (bmp.getHeight() * k)), true);
            try (FileOutputStream fo = new FileOutputStream(Skin.imageFile(this))) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, fo);
            }
            return true;
        } catch (Throwable e) { return false; }
    }

    // ======================= interfaz lateral (burbuja / gestos) =======================

    private SideHub sideHub() {
        ControlService cs = ControlService.instance;
        return cs != null ? cs.side() : null;
    }

    private void sideSlider(final String name, final String key, final int lo, int hi, final String unit) {
        if (!item(1)) return;
        final TextView tv = label("", 14f);
        int cur = Math.max(lo, Math.min(hi, SideConfig.get(this, key)));
        tv.setText(name + ": " + cur + unit);
        controls.addView(tv);
        SeekBar sb = new SeekBar(this);
        sb.setMax(hi - lo);
        sb.setProgress(cur - lo);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tv.setText(name + ": " + (p + lo) + unit);
                if (fromUser) SideConfig.set(SettingsPageActivity.this, key, p + lo);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        controls.addView(sb);
    }

    private void sideToggle(String name, final String key) {
        if (!item(1)) return;
        Switch sw = new Switch(this);
        sw.setText(name);
        sw.setTextSize(14f);
        sw.setTextColor(txt);
        sw.setPadding(0, dp(10), 0, dp(2));
        sw.setChecked(SideConfig.get(this, key) != 0);
        sw.setOnCheckedChangeListener((v, c) -> SideConfig.set(SettingsPageActivity.this, key, c ? 1 : 0));
        controls.addView(sw);
    }

    private TextView sideMini(String text, boolean enabled, final Runnable action) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18f);
        t.setTextColor(txt);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setAlpha(enabled ? 1f : 0.25f);
        if (enabled) t.setOnClickListener(v -> action.run());
        return t;
    }

    /** Un renglón de la lista de accesos laterales: ícono, nombre y ▲ ▼ ✕. */
    private void sideShortcutRow(final int idx, final String pkg, final int count) {
        if (!item(1)) return;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(SideLauncher.icon(this, pkg));
        row.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
        boolean ok = SideLauncher.installedPkg(this, pkg);
        TextView name = label(ok ? SideLauncher.label(this, pkg) : pkg + " (no instalada)", 14f, ok ? txt : sub, 0);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, WCB, 1f);
        nlp.leftMargin = dp(12);
        row.addView(name, nlp);
        row.addView(sideMini("▲", idx > 0, () -> { SideConfig.moveShortcut(this, idx, idx - 1); buildControls(); }));
        row.addView(sideMini("▼", idx < count - 1, () -> { SideConfig.moveShortcut(this, idx, idx + 1); buildControls(); }));
        row.addView(sideMini("✕", true, () -> { SideConfig.removeShortcut(this, pkg); buildControls(); }));
        controls.addView(row);
    }

    /** Diálogo para marcar qué apps aparecen en el panel. Las ya elegidas conservan su orden. */
    private void pickSideApps() {
        Toast.makeText(this, "Cargando apps…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final List<SideLauncher.App> all = SideLauncher.installed(this);
            ui.post(() -> {
                if (isFinishing()) return;
                final List<String> cur = SideConfig.shortcuts(this);
                final CharSequence[] names = new CharSequence[all.size()];
                final boolean[] on = new boolean[all.size()];
                for (int i = 0; i < names.length; i++) {
                    names[i] = all.get(i).label;
                    on[i] = cur.contains(all.get(i).pkg);
                }
                new AlertDialog.Builder(this)
                    .setTitle("Apps del panel lateral")
                    .setMultiChoiceItems(names, on, (dlg, which, checked) -> on[which] = checked)
                    .setPositiveButton("Guardar", (dlg, w) -> {
                        List<String> out = new ArrayList<>();
                        for (String pkg : cur) {
                            boolean keep = true;
                            for (int i = 0; i < names.length; i++) if (all.get(i).pkg.equals(pkg) && !on[i]) keep = false;
                            if (keep) out.add(pkg);
                        }
                        for (int i = 0; i < names.length; i++) if (on[i] && !out.contains(all.get(i).pkg)) out.add(all.get(i).pkg);
                        SideConfig.saveShortcuts(this, out);
                        buildControls();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            });
        }).start();
    }

    // =====================================================================================================
    //  Páginas: ramas = filas, hojas = controles (en el orden del árbol)
    // =====================================================================================================

    private void buildControls() {
        final int keep = keepZero ? 0 : (scroll != null ? scroll.getScrollY() : 0);
        keepZero = false;
        controls.removeAllViews();

        if (leafOnly != null) {
            renderLeaf(leafOnly);
            scroll.post(() -> scroll.scrollTo(0, keep));
            return;
        }

        if (node.id.equals("ac_indiv")) renderTileChips();

        LinearLayout card = null;
        for (final SettingsTree.Node k : node.kids) {
            if (k.isLeaf()) {
                card = null;
                renderLeaf(k.id);
                continue;
            }
            if (card == null) {
                card = ZUi.box(this, LinearLayout.VERTICAL);
                card.setBackground(ZUi.shape(this, ZUi.CARD, 20));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(MPB, WCB);
                cp.topMargin = dp(14);
                controls.addView(card, cp);
            } else {
                card.addView(ZUi.divider(this), new LinearLayout.LayoutParams(-1, 1));
            }
            card.addView(ZUi.row(this, null, k.title, null, false, v -> {
                if (k.isAction()) k.target.open(this);
                else SettingsPageActivity.open(this, k);
            }));
        }
        scroll.post(() -> scroll.scrollTo(0, keep));
    }

    private List<String> tileKeys() {
        List<String> keys = new ArrayList<>(Arrays.asList(PanelView.TILE_KEYS));
        for (String[] f : Skin.customTiles(this)) keys.add("ct:" + f[0]);
        return keys;
    }

    private List<String> tileNames() {
        List<String> names = new ArrayList<>(Arrays.asList(PanelView.TILE_KEYS));
        for (String[] f : Skin.customTiles(this)) names.add("★ " + (f[2].isEmpty() ? "Acceso" : f[2]));
        return names;
    }

    /** Selector de acceso para la página «Ajuste individual». */
    private void renderTileChips() {
        List<String> names = tileNames();
        if (selTile >= names.size()) selTile = 0;
        chips(names, selTile, i -> { selTile = i; buildControls(); });
    }

    private String selKey() {
        List<String> keys = tileKeys();
        if (selTile >= keys.size()) selTile = 0;
        return keys.get(selTile);
    }

    private static final String[] ZONE_KEYS = {"left", "center", "right", "full"};
    private static final List<String> GEST_OPTS = Arrays.asList("Nada", "Abrir el panel", "Abrir el editor de accesos",
        "Captura de pantalla", "Bloquear pantalla", "Menú de apagado", "Inicio", "Atrás", "Recientes",
        "Notificaciones del sistema", "Ajustes rápidos del sistema", "Abrir una app (paquete)", "Ejecutar comando (Shizuku)");

    private void gesture(String name, String key, String paramName) {
        choice(name, key, GEST_OPTS);
        textInput(paramName, key + "P", "");
    }

    private void renderLeaf(String id) {
        Skin sk = new Skin(this);
        switch (id) {

            // ───────────── Apariencia › Fondo ─────────────
            case "bg_type": {
                List<String> bgLabels = new ArrayList<>();
                for (BgMode m : BgMode.values()) bgLabels.add(m.label);
                choice("Tipo de fondo", "bgMode", bgLabels, true, i -> {
                    BgMode m = BgMode.values()[i];
                    int dim = Skin.get(this, "dim");
                    if (m == BgMode.SOLID && dim < 50) Skin.set(this, "dim", 92);
                    if (m != BgMode.SOLID && dim > 70) Skin.set(this, "dim", 35);
                });
                if (sk.bgMode == BgMode.IMAGE) imageRow();
                if (sk.bgMode == BgMode.TRANSPARENT) note("El panel flota sobre lo que estés usando, sin oscurecer ni difuminar.");
                break;
            }
            case "bg_blur":
                if (sk.bgMode == BgMode.BLUR) skSlider("Intensidad del difuminado", "blurR", 1, 12);
                else if (sk.bgMode == BgMode.IMAGE) skSlider("Desenfoque de la imagen", "imgBlur", 0, 12);
                break;
            case "bg_dim":
                if (sk.bgMode == BgMode.SOLID) skSlider("Opacidad", "dim", 0, 100, "%");
                else if (sk.bgMode != BgMode.TRANSPARENT) skSlider("Oscurecer", "dim", 0, 90, "%");
                break;
            case "bg_tint":
                if (sk.bgMode == BgMode.SOLID) colorRow("Color del fondo", "bgColor", false);
                else if (sk.bgMode != BgMode.TRANSPARENT) colorRow("Tinte", "bgColor", false);
                if (sk.bgMode == BgMode.BLUR) note("Usa el difuminado del sistema (Android 12 o superior). Si tu equipo no lo permite, el fondo se oscurece. No toma capturas de pantalla.");
                break;

            // ───────────── Apariencia › Hoja ─────────────
            case "sh_colors":
                colorRow("Color de la hoja", "sheetColor", true);
                colorRow("Segundo color (degradado)", "sheetColor2", true);
                choice("Dirección del degradado", "sheetAngle", Arrays.asList("Arriba a abajo", "Izquierda a derecha", "Abajo a arriba", "Derecha a izquierda"));
                break;
            case "sh_alpha":
                skSlider("Opacidad de la hoja (0 = sin hoja)", "sheetAlpha", 0, 100, "%");
                break;
            case "sh_radius":
                skSlider("Redondeo de arriba", "sheetTopRadius", 0, 48, " dp");
                skSlider("Redondeo de abajo", "sheetRadius", 0, 48, " dp");
                break;
            case "sh_stroke":
                skSlider("Borde de la hoja", "sheetStroke", 0, 8, " dp");
                colorRow("Color del borde de la hoja", "sheetStrokeColor", true);
                break;
            case "sh_size":
                skSlider("Ancho de la hoja", "sheetWidth", 50, 100, "%");
                skSlider("Margen lateral", "sidePad", 0, 40, " dp");
                skSlider("Margen superior", "topPad", 0, 120, " dp");
                break;

            // ───────────── Apariencia › Botones ─────────────
            case "bt_shape": {
                List<String> shapeLabels = new ArrayList<>();
                for (TileShape s : TileShape.values()) shapeLabels.add(s.sym + " " + s.label);
                choice("Forma", "shape", shapeLabels, true);
                if (sk.shape == TileShape.ROUNDED) skSlider("Redondeo de la forma", "round", 0, 50, "%");
                List<String> layoutLabels = new ArrayList<>();
                for (TileLayout l : TileLayout.values()) layoutLabels.add(l.label);
                choice("Disposición", "layout", layoutLabels, true);
                break;
            }
            case "bt_size":
                skSlider("Tamaño", "size", 36, 110, " dp");
                skSlider("Separación", "gap", 0, 24, " dp");
                skSlider("Columnas", "cols", 2, 6);
                if (sk.layout == TileLayout.PILL) note("En píldora se usan hasta 3 columnas.");
                break;
            case "bt_rot":
                skSlider("Rotación de cada botón", "tileRot", 0, 359, "°");
                skSlider("Escala encendido", "onScale", 60, 140, "%");
                skSlider("Escala apagado", "offScale", 40, 140, "%");
                break;
            case "bt_text":
                skSlider("Tamaño del ícono", "iconScale", 30, 80, "%");
                if (sk.layout != TileLayout.ICON) skSlider("Tamaño del texto", "labelSize", 9, 16, " sp");
                if (sk.layout == TileLayout.PILL) toggle("Mostrar el estado (texto chico)", "showSub");
                toggle("Etiquetas en MAYÚSCULAS", "labelCaps");
                break;
            case "bt_border":
                skSlider("Grosor del borde", "tileStroke", 0, 8, " dp");
                colorRow("Color del borde", "strokeColor", true);
                if (sk.layout == TileLayout.PILL) skSlider("Redondeo de la píldora", "pillRound", 0, 100, "%");
                break;

            // ───────────── Apariencia › Colores ─────────────
            case "co_theme":
                choice("Tema del texto", "theme", Arrays.asList("Sistema", "Claro", "Oscuro"));
                break;
            case "co_accent":
                colorRow("Acento (botón encendido)", "accent", true);
                skSlider("Opacidad encendido", "onAlpha", 20, 100, "%");
                break;
            case "co_off":
                colorRow("Botón apagado", "tileOff", true);
                skSlider("Opacidad apagado", "offAlpha", 0, 100, "%");
                break;
            case "co_text":
                colorRow("Texto e íconos", "textColor", true);
                colorRow("Color de las etiquetas", "labelColor", true);
                colorRow("Color del subtexto", "subColor", true);
                colorRow("Color del texto encendido", "onFg", true);
                break;

            // ───────────── Apariencia › Reloj ─────────────
            case "rl_look":
                skSlider("Tamaño", "clockSize", 16, 96, " sp");
                choice("Alineación", "clockAlign", Arrays.asList("Izquierda", "Centro", "Derecha"));
                choice("Grosor", "clockWeight", Arrays.asList("Fino", "Normal", "Negrita"));
                choice("Fuente del reloj", "clockFont", Arrays.asList("Según grosor", "Sans", "Serif", "Mono", "Casual", "Condensada", "Cursiva", "Fina", "Media"));
                toggle("Reloj en cursiva", "clockItalic");
                break;
            case "rl_time":
                choice("Formato de hora", "clockFormat", Arrays.asList("24 h", "12 h"));
                toggle("Mostrar segundos", "showSeconds");
                break;
            case "rl_date":
                toggle("Mostrar la fecha", "showDate");
                choice("Formato de fecha", "dateFormat", Arrays.asList("domingo 15 de noviembre", "dom 15 nov", "15/11/2026", "2026-11-15"));
                skSlider("Tamaño de la fecha", "dateSize", 8, 32, " sp");
                break;
            case "rl_colors":
                colorRow("Color del reloj", "clockColor", true);
                colorRow("Color de la fecha", "dateColor", true);
                break;
            case "rl_pad":
                skSlider("Margen vertical del encabezado", "clockPadV", 0, 48, " dp");
                break;

            // ───────────── Apariencia › Tipografía ─────────────
            case "ty_font":
                choice("Fuente", "fontFamily", Arrays.asList("Sans", "Serif", "Mono", "Casual", "Condensada", "Cursiva", "Fina", "Media"));
                skSlider("Escala del texto", "textScale", 50, 200, "%");
                break;
            case "ty_style":
                toggle("Negrita en todo", "textBold");
                toggle("Cursiva en todo", "textItalic");
                break;
            case "ty_space":
                skSlider("Espaciado entre letras", "letterSp", 0, 40);
                break;

            // ───────────── Apariencia › Tarjetas y notificaciones ─────────────
            case "ca_look":
                colorRow("Color de las tarjetas", "cardColor", true);
                skSlider("Opacidad", "cardAlpha", 0, 100, "%");
                skSlider("Redondeo", "cardR", 0, 40, " dp");
                break;
            case "ca_border":
                skSlider("Borde de las tarjetas", "cardStroke", 0, 6, " dp");
                colorRow("Color del borde de las tarjetas", "cardStrokeColor", true);
                break;
            case "ca_lines":
                skSlider("Líneas de texto (colapsada)", "notifLines", 1, 6);
                skSlider("Líneas de texto (expandida)", "notifOpenLines", 2, 16);
                break;
            case "ca_sizes":
                skSlider("Tamaño del título", "notifTitleSize", 8, 24, " sp");
                skSlider("Tamaño del texto", "notifTextSize", 8, 24, " sp");
                skSlider("Tamaño del ícono", "notifIconSize", 20, 64, " dp");
                break;
            case "ca_extra":
                toggle("Ícono de la app en las notificaciones", "notifApp");
                toggle("Mostrar la hora", "notifShowTime");
                break;
            case "ca_list":
                skSlider("Separación entre notificaciones", "notifGap", 0, 24, " dp");
                skSlider("Alto máximo de la lista", "notifMaxH", 10, 80, "%");
                toggle("Mostrar el botón de borrar todo", "showClear");
                break;

            // ───────────── Apariencia › Brillo, volumen y reproductor ─────────────
            case "br_style":
                choice("Estilo", "sliderStyle", Arrays.asList("Horizontal", "Vertical"));
                break;
            case "br_thick":
                skSlider("Tamaño de los sliders", "sliderThick", 40, 90, " dp");
                break;
            case "br_corner":
                skSlider("Redondeo de los sliders verticales", "vCorner", 10, 40);
                break;
            case "br_media":
                toggle("Mostrar el reproductor", "showMedia");
                break;
            case "br_mediah":
                skSlider("Alto del reproductor", "mediaH", 80, 260, " dp");
                break;

            // ───────────── Accesos ─────────────
            case "ac_visible":
                skSlider("Accesos visibles", "compact", 0, 8);
                note("Son los primeros de tu lista. Para ordenarlos, usá el botón ✎ del panel real.");
                break;
            case "ac_custom":
                renderCustomTiles();
                break;
            case "ai_name":
                textInput("Nombre", "lbl_" + selKey(), tileNames().get(selTile));
                textInput("Glifo (emoji o texto en lugar del ícono)", "glyph_" + selKey(), "");
                break;
            case "ai_colors":
                colorRow("Color encendido", "tOn_" + selKey(), true);
                colorRow("Color apagado", "tOff_" + selKey(), true);
                break;
            case "ai_span":
                skSlider("Columnas que ocupa", "tSpan_" + selKey(), 1, 6);
                break;
            case "ac_apps": {
                final List<String> apps = SideConfig.shortcuts(this);
                if (apps.isEmpty()) note("Todavía no hay apps en el panel.");
                for (int i = 0; i < apps.size(); i++) sideShortcutRow(i, apps.get(i), apps.size());
                toolBtn("＋ Agregar o quitar apps", this::pickSideApps);
                note("También podés editar los accesos desde el propio panel: tocá ✎ o mantené apretada una app.");
                break;
            }
            case "fb_mode":
                choice("Mostrar", "fabMode", Arrays.asList("Oculto", "Solo en Controles", "Siempre"));
                break;
            case "fb_size":
                skSlider("Tamaño", "fabSize", 32, 96, " dp");
                break;
            case "fb_sym":
                textInput("Símbolo", "txtFab", "✎");
                note("Se arrastra a cualquier lado y recuerda dónde lo dejaste.");
                break;
            case "hd_gear":
                toggle("Botón de ajustes ⚙", "showGear");
                break;
            case "hd_edit":
                toggle("Botón ✎ para editar accesos", "showEdit");
                break;
            case "hd_size":
                skSlider("Tamaño de los botones ⚙ y ✎", "gearSize", 24, 80, " dp");
                break;

            // ───────────── Textos ─────────────
            case "tx_tabs":
                textInput("Título de la pestaña 1", "txtTab0", "Notificaciones");
                textInput("Título de la pestaña 2", "txtTab1", "Controles");
                break;
            case "tx_clear":
                textInput("Texto de borrar todo", "txtClear", "Borrar todo");
                break;
            case "tx_empty":
                textInput("Texto sin notificaciones", "txtEmpty", "Sin notificaciones");
                break;
            case "tx_sym":
                textInput("Símbolo de ajustes", "txtGear", "⚙");
                textInput("Símbolo de editar", "txtEditBtn", "✎");
                break;
            case "tx_edit":
                textInput("Texto del botón de editar accesos", "txtEditTiles", "Editar accesos");
                break;
            case "tx_names":
                if (item(0)) controls.addView(label("Nombres de los accesos", 12f, acc, 18));
                for (String tk : PanelView.TILE_KEYS) textInput(tk, "lbl_" + tk, tk);
                break;

            // ───────────── Comportamiento ─────────────
            case "cp_close":
                toggle("Tocar fuera cierra el panel", "closeTap");
                break;
            case "cp_onact":
                toggle("Cerrar al abrir un ajuste", "closeOnAct");
                break;
            case "cp_haptic":
                toggle("Vibración al tocar", "haptic");
                break;
            case "cp_tabs":
                choice("Pestaña inicial", "defaultTab", Arrays.asList("Notificaciones", "Controles"));
                toggle("Mostrar las pestañas", "showTabs");
                break;
            case "cp_test":
                choice("Con el botón Probar abre en", "startStage", Arrays.asList("Vista acotada", "Vista completa"));
                break;
            case "cp_grab":
                toggle("Mostrar la barrita de abajo", "showGrab");
                skSlider("Ancho de la barrita de abajo", "grabW", 16, 200, " dp");
                skSlider("Grosor de la barrita de abajo", "grabH", 2, 16, " dp");
                break;

            // ───────────── Diseño manual ─────────────
            case "dm_mode":
                choice("Modo de diseño", "layoutMode", Arrays.asList("Clásico (cuadrícula automática)", "Lienzo (lo armo a mano)"));
                break;
            case "dm_cols":
                skSlider("Columnas del lienzo", "canvasCols", 4, 16);
                break;
            case "dm_editor":
                toolBtn("Abrir el editor del lienzo", () -> startActivity(new Intent(this, LayoutEditorActivity.class)));
                note("En el lienzo ubicás cada elemento donde quieras y del tamaño que quieras: accesos, reloj, reproductor, notificaciones, sliders, imágenes, GIF, videos, widgets y textos.");
                break;

            // ───────────── Apertura del panel ─────────────
            case "ba_zone": {
                String cz = Skin.prefs(this).getString("zone", "full");
                int zi = Math.max(0, Arrays.asList(ZONE_KEYS).indexOf(cz));
                chipsRow("Zona de apertura", Arrays.asList("Izquierda", "Centro", "Derecha", "Todo arriba"), zi, i -> {
                    Skin.prefs(this).edit().putString("zone", ZONE_KEYS[i]).apply();
                    ControlService s = ControlService.instance;
                    if (s != null) s.rebuildHandle();
                });
                break;
            }
            case "ba_look":
                slider("Opacidad de la barrita (0 = invisible)", 0, 100, "%", true,
                    () -> Skin.prefs(this).getInt("pill_alpha", 60),
                    v -> Skin.prefs(this).edit().putInt("pill_alpha", v).apply());
                skSlider("Largo de la barrita", "pillW", 24, 240, " dp", true);
                skSlider("Grosor de la barrita", "pillH", 2, 14, " dp", true);
                colorRow("Color de la barrita", "pillColor", true);
                skSlider("Redondeo de la barrita", "pillRadius", 0, 20, " dp", true);
                break;
            case "ba_touch":
                skSlider("Margen superior de la barrita", "pillTop", 0, 40, " dp", true);
                skSlider("Alto de la zona táctil", "handleH", 12, 160, " dp", true);
                skSlider("Distancia para empezar a bajar", "openThr", 2, 120, " dp");
                break;
            case "gb_tap":
                gesture("Un toque", "gestTap", "Paquete o comando del toque");
                break;
            case "gb_double":
                gesture("Doble toque", "gestDouble", "Paquete o comando del doble toque");
                break;
            case "gb_long":
                gesture("Mantener apretado", "gestLong", "Paquete o comando de mantener apretado");
                note("Bajar desde la barrita sigue abriendo el panel aunque tengas gestos asignados.");
                break;

            // ───────────── Temas ─────────────
            case "th_presets":
                if (item(0)) controls.addView(label("Presets", 12f, acc, 18));
                chips(Arrays.asList(Skin.PRESETS), -1, i -> { Skin.applyPreset(this, i); buildControls(); changed(true); });
                break;
            case "th_slots":
                for (int sl = 1; sl <= 3; sl++) {
                    final int slot = sl;
                    toolBtn("Guardar en la ranura " + slot, () -> {
                        Skin.saveSlot(this, slot);
                        Toast.makeText(this, "Guardado en la ranura " + slot, Toast.LENGTH_SHORT).show();
                    });
                    toolBtn("Cargar la ranura " + slot, () -> {
                        int n = Skin.loadSlot(this, slot);
                        if (n < 0) Toast.makeText(this, "Esa ranura está vacía", Toast.LENGTH_SHORT).show();
                        else { buildControls(); changed(true); }
                    });
                }
                break;

            // ───────────── Animaciones ─────────────
            case "an_style":
                choice("Estilo", "animStyle", Arrays.asList("Desacelera", "Suave", "Rebote hacia afuera", "Rebota", "Lineal", "Se echa hacia atrás"));
                skSlider("Duración", "animDur", 0, 1200, " ms");
                skSlider("Duración de las pestañas", "tabAnimDur", 0, 1200, " ms");
                break;
            case "an_tap":
                choice("Animación al tocar un acceso", "tapAnim", Arrays.asList("Ninguna", "Rebote", "Giro", "Vuelta", "Temblor", "Pulso"));
                break;
            case "an_entry":
                choice("Animación de entrada de los accesos", "entryAnim", Arrays.asList("Ninguna", "Sube y aparece", "Crece", "Entra desde la izquierda"));
                break;

            // ───────────── Interfaz lateral ─────────────
            case "lt_mode": {
                final boolean threeBtn = SideConfig.threeButtonNav(this);
                chipsRow("Modo de la interfaz", Arrays.asList("Apagada", "Burbuja", "Gestos (3 botones)"), SideConfig.getMode(this), i -> {
                    if (i == SideConfig.MODE_GESTURE && !threeBtn) {
                        Toast.makeText(this, "El modo gestos solo funciona con la navegación de tres botones", Toast.LENGTH_LONG).show();
                    } else {
                        SideConfig.setMode(this, i);
                    }
                    buildControls();
                });
                note("Los dos modos usan los mismos accesos, ventanas y notificaciones; solo cambia cómo se abre el panel: tocando la burbuja o deslizando desde el borde derecho.");
                note("Servicio: " + (ControlService.instance != null ? "activo" : "inactivo")
                    + "  ·  Acceso a notificaciones: " + (MediaListener.instance != null ? "activo" : "pendiente")
                    + "  ·  Navegación: " + (threeBtn ? "tres botones" : "gestos o dos botones"));
                break;
            }
            case "bb_look":
                sideSlider("Tamaño de la burbuja", "bubbleSize", 32, 72, " dp");
                sideSlider("Opacidad de la burbuja", "bubbleAlpha", 30, 100, "%");
                break;
            case "bb_hold":
                sideSlider("Mantener apretada para moverla", "holdSecs", 3, 15, " s");
                break;
            case "bb_actions":
                toolBtn("Mover la burbuja ahora (modo lienzo)", () -> {
                    SideHub h = sideHub();
                    if (h != null && SideConfig.getMode(this) == SideConfig.MODE_BUBBLE) { h.enterCanvas(); moveTaskToBack(true); }
                    else Toast.makeText(this, "Activá el modo burbuja primero", Toast.LENGTH_SHORT).show();
                });
                toolBtn("Devolver la burbuja al borde derecho", () -> { SideConfig.resetPos(this); });
                toolBtn("Abrir el panel ahora", () -> {
                    SideHub h = sideHub();
                    if (h != null && SideConfig.getMode(this) != SideConfig.MODE_OFF) { h.open(); moveTaskToBack(true); }
                    else Toast.makeText(this, "Activá la burbuja o los gestos primero", Toast.LENGTH_SHORT).show();
                });
                note("Si te estorba: mantené apretada la burbuja unos segundos (por defecto 10). Se abre el modo lienzo y la arrastrás adonde quieras.");
                break;
            case "ln_anim":
                sideToggle("Mostrar notificaciones nuevas con animación", "noteOn");
                sideSlider("Tiempo visible", "noteSecs", 2, 15, " s");
                break;
            case "ln_test":
                toolBtn("Probar una notificación", () -> {
                    SideHub h = sideHub();
                    if (h == null || !h.test()) Toast.makeText(this, "Activá la burbuja o los gestos primero", Toast.LENGTH_SHORT).show();
                    else moveTaskToBack(true);
                });
                note("Solo avisa de notificaciones normales: no muestra las silenciosas, las fijas, las de progreso ni las de la propia app. Tocá la tarjeta para abrir la app; deslizala hacia arriba para descartarla.");
                break;
            case "vt_open":
                sideToggle("Abrir las apps en ventana flotante", "floatWin");
                break;
            case "vt_geom":
                sideSlider("Modo de ventana que se pide (5 = libre, 1 = completa, 6 = dividida)", "winMode", 1, 6, "");
                sideSlider("Ancho de la ventana", "winSize", 20, 100, "%");
                sideSlider("Alto de la ventana", "winH", 20, 100, "%");
                sideSlider("Posición horizontal (0 = izquierda, 100 = derecha)", "winX", 0, 100, "%");
                sideSlider("Posición vertical (0 = arriba, 100 = abajo)", "winY", 0, 100, "%");
                break;
            case "vt_bounds":
                sideToggle("Pasar tamaño y posición al lanzar", "winBounds");
                break;
            case "vt_aux":
                sideToggle("Lanzar antes la actividad auxiliar invisible (como Taskbar)", "winHack");
                sideSlider("Espera entre la auxiliar y la app", "winHackMs", 0, 600, " ms");
                break;
            case "vt_apis":
                sideToggle("Desbloquear APIs ocultas por reflexión", "winExempt");
                sideToggle("Escribir las opciones globales de ventana libre al lanzar", "winSettings");
                break;
            case "vt_prep":
                toolBtn("Preparar ventanas flotantes (Shizuku)", () -> {
                    if (Privileged.available()) {
                        SideLauncher.prepareFreeform();
                        Toast.makeText(this, "Listo. En algunos equipos hay que reiniciar para que tome efecto", Toast.LENGTH_LONG).show();
                    } else Toast.makeText(this, "Necesita Shizuku activo", Toast.LENGTH_LONG).show();
                });
                note("Funciona si el sistema y la app lo permiten. Si no, la app se abre normalmente en pantalla completa.");
                break;
            case "lg_w":
                note(SideConfig.threeButtonNav(this) ? "Tu equipo usa navegación de tres botones: el modo gestos está disponible."
                    : "Tu equipo usa navegación por gestos (o dos botones): el modo gestos queda desactivado para no chocar con los gestos de Android.");
                sideSlider("Ancho de la zona del borde", "edgeW", 12, 40, " dp");
                break;
            case "lg_h":
                sideSlider("Alto de la zona del borde", "edgeH", 30, 100, "%");
                break;

            // ───────────── Automatización ─────────────
            case "au_theme":
                toggle("Tema oscuro por horario", "autoTheme");
                skSlider("Desde las", "autoFrom", 0, 23, " h");
                skSlider("Hasta las", "autoTo", 0, 23, " h");
                break;
            case "au_bat":
                toggle("Acento rojo con batería baja (menos de 20%)", "lowBatRed");
                break;

            // ───────────── Herramientas ─────────────
            case "tl_config":
                toolBtn("Copiar configuración", () -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("zc-control", Skin.export(this)));
                    Toast.makeText(this, "Configuración copiada", Toast.LENGTH_SHORT).show();
                });
                toolBtn("Pegar configuración", () -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    ClipData cd = cm.getPrimaryClip();
                    CharSequence tx = cd != null && cd.getItemCount() > 0 ? cd.getItemAt(0).coerceToText(this) : null;
                    int n = tx == null ? -1 : Skin.importAll(this, tx.toString());
                    if (n < 0) Toast.makeText(this, "El portapapeles no tiene una configuración válida", Toast.LENGTH_LONG).show();
                    else { Toast.makeText(this, "Se aplicaron " + n + " valores", Toast.LENGTH_SHORT).show(); changed(true); }
                });
                toolBtn("🎲 Aleatorizar todo", () -> { Skin.randomize(this); changed(true); });
                Button reset = new Button(this);
                reset.setText("Restablecer todo");
                reset.setAllCaps(false);
                reset.setOnClickListener(v -> { Skin.reset(SettingsPageActivity.this); changed(true); });
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MPB, WCB);
                rlp.topMargin = dp(28);
                controls.addView(reset, rlp);
                break;

            default:
                break;
        }
    }

    /** Accesos propios: lista editable + botón de agregar. */
    private void renderCustomTiles() {
        final List<String[]> cts = Skin.customTiles(this);
        for (final String[] f : cts) {
            controls.addView(label("★ " + (f[2].isEmpty() ? "Acceso" : f[2]), 14f, acc, 14));
            chips(Arrays.asList("Abrir app", "Abrir enlace", "Comando", "Interruptor"), parseInt0(f[1]), i -> {
                f[1] = String.valueOf(i);
                Skin.saveCustomTiles(this, cts);
                changed();
            });
            textEdit("Nombre", f[2], v -> { f[2] = v; Skin.saveCustomTiles(this, cts); changed(); });
            textEdit("Destino (paquete, enlace o comando; interruptor: encendido&&&apagado)", f[3], v -> {
                f[3] = v;
                Skin.saveCustomTiles(this, cts);
                changed();
            });
            toolBtn("Eliminar este acceso", () -> { cts.remove(f); Skin.saveCustomTiles(this, cts); buildControls(); changed(); });
        }
        toolBtn("＋ Agregar acceso propio", () -> {
            String id = Long.toString(System.currentTimeMillis(), 36);
            cts.add(new String[]{id, "0", "Nuevo", ""});
            Skin.saveCustomTiles(this, cts);
            enableTile("ct:" + id);
            buildControls();
            changed();
        });
    }
}
