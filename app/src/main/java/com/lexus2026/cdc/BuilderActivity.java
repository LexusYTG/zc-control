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

/** "Make your action bar": arma tu propia barra con vista previa en vivo. Todo se guarda al instante. */
public class BuilderActivity extends Activity {
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
    private int curPage = 0;
    private boolean keepZero = false;
    private boolean dry = false;
    private int gCur = 0;
    private int gCountPage = 0;
    private String curSection = "";
    private final List<String> pageTitles = new ArrayList<>();
    private Button navPrev;
    private Button navNext;
    private TextView navTitle;

    private final int txt = ZUi.TXT;
    private final int sub = ZUi.SUB;
    private final int acc = ZUi.ACC;
    private final int chipOff = ZUi.CARD_HI;
    private final int chipOn = 0xFF3B54B8;

    // Modo «ajuste suelto»: solo se dibujan las secciones pedidas (extras title / sections / preview).
    private java.util.Set<String> only = null;
    private String onlyTitle = null;
    private boolean onlyPreview = false;
    private boolean secOn = false;

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
        String[] secs = getIntent() != null ? getIntent().getStringArrayExtra("sections") : null;
        if (secs != null) {
            only = new java.util.HashSet<>(java.util.Arrays.asList(secs));
            onlyTitle = getIntent().getStringExtra("title");
            onlyPreview = getIntent().getBooleanExtra("preview", false);
        }
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
        TextView title = new TextView(this);
        title.setText(only != null && onlyTitle != null ? onlyTitle : Lang.t(5));
        title.setTextSize(20f);
        title.setTextColor(txt);
        title.setTypeface(null, Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, WCB, 1f));
        root.addView(head);

        // ---- vista previa ----
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
        stateBtn(bar, Lang.t(6), 1, 0); stateBtn(bar, Lang.t(7), 2, 0); stateBtn(bar, Lang.t(8), 2, 1);
        Button real = new Button(this);
        real.setText(Lang.t(9));
        real.setAllCaps(false);
        real.setTextSize(12f);
        real.setPadding(0, 0, 0, 0);
        real.setOnClickListener(v -> {
            ControlService svc = ControlService.instance;
            if (svc != null) svc.open();
            else Toast.makeText(BuilderActivity.this, Lang.t(10), Toast.LENGTH_LONG).show();
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
        if (only != null && !onlyPreview) { previewBox.setVisibility(View.GONE); bar.setVisibility(View.GONE); }

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(8), 0, dp(8), dp(4));
        navPrev = navBtn("‹", () -> goPage(curPage - 1));
        navNext = navBtn("›", () -> goPage(curPage + 1));
        navTitle = new TextView(this);
        navTitle.setTextColor(txt);
        navTitle.setTextSize(14f);
        navTitle.setGravity(Gravity.CENTER);
        navTitle.setTypeface(null, Typeface.BOLD);
        navTitle.setOnClickListener(v -> showPages());
        nav.addView(navPrev, new LinearLayout.LayoutParams(dp(56), WCB));
        nav.addView(navTitle, new LinearLayout.LayoutParams(0, WCB, 1f));
        nav.addView(navNext, new LinearLayout.LayoutParams(dp(56), WCB));
        root.addView(nav);
        if (only != null) nav.setVisibility(View.GONE);

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

        // Si se abrió desde el panel lateral o desde la pantalla principal, salta directo a esa sección.
        String jump = getIntent() != null ? getIntent().getStringExtra("page_title") : null;
        if (jump != null) {
            for (int i = 0; i < pageTitles.size(); i++) {
                if (pageTitles.get(i).startsWith(jump)) { goPage(i); break; }
            }
        }
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(rebuildRunnable);
        super.onDestroy();
    }

    // ======================= vista previa =======================

    private void doRebuildPreview() {
        if (only != null && !onlyPreview) return;
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

    private Button navBtn(String text, final Runnable action) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setTextSize(18f);
        bt.setOnClickListener(v -> action.run());
        return bt;
    }

    private void goPage(int p) {
        if (p < 0 || p >= pageTitles.size()) return;
        curPage = p;
        keepZero = true;
        buildControls();
    }

    private void showPages() {
        CharSequence[] names = new CharSequence[pageTitles.size()];
        for (int i = 0; i < names.length; i++) names[i] = (i + 1) + ". " + pageTitles.get(i);
        new AlertDialog.Builder(this).setTitle(Lang.t(11)).setItems(names, (d, w) -> goPage(w)).show();
    }

    private void updateNav() {
        navTitle.setText((curPage + 1) + " / " + pageTitles.size() + "  ·  " + pageTitles.get(curPage));
        navPrev.setAlpha(curPage > 0 ? 1f : 0.3f);
        navNext.setAlpha(curPage < pageTitles.size() - 1 ? 1f : 0.3f);
    }

    private void newPage() {
        gCur++;
        gCountPage = 0;
    }

    private boolean item(int w) {
        if (only != null) return !dry && secOn;
        if (w > 0) {
            if (gCountPage + w > 12) {
                newPage();
                if (dry && pageTitles.size() <= gCur) pageTitles.add(curSection + " …");
            }
            gCountPage += w;
        }
        return !dry && gCur == curPage;
    }

    private void section(String title) {
        curSection = title;
        if (only != null) {
            secOn = only.contains(title);
            if (dry || !secOn || only.size() == 1) return;   // con una sola sección el título ya está arriba
            addSectionHeader(title);
            return;
        }
        if (gCountPage >= 5) newPage();
        if (dry && pageTitles.size() <= gCur) pageTitles.add(title);
        if (dry || gCur != curPage) return;
        addSectionHeader(title);
    }

    private void addSectionHeader(String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(12f);
        t.setTextColor(acc);
        t.setTypeface(null, Typeface.BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(18), 0, dp(4));
        controls.addView(t);
    }

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
                Skin.setStr(BuilderActivity.this, key, s.toString());
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
        sw.setOnCheckedChangeListener((v, c) -> { Skin.set(BuilderActivity.this, key, c ? 1 : 0); changed(); });
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
        v.setOnClickListener(x -> { Skin.set(BuilderActivity.this, key, c); paint.run(); changed(key.startsWith("pill")); });
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
                Skin.set(BuilderActivity.this, key, Color.HSVToColor(hsv));
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
        hsvBar(custom, hsv, key, paint, Lang.t(12), 360, (int) hsv[0], 0, 1f);
        hsvBar(custom, hsv, key, paint, Lang.t(13), 100, (int) (hsv[1] * 100), 1, 0.01f);
        hsvBar(custom, hsv, key, paint, Lang.t(873), 100, (int) (hsv[2] * 100), 2, 0.01f);
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
        controls.addView(label(has ? Lang.t(14) : Lang.t(15), 13f, has ? txt : sub, 8));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(6), 0, dp(2));
        Button pick = new Button(this);
        pick.setText(has ? Lang.t(16) : Lang.t(17));
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
            del.setText(Lang.t(18));
            del.setAllCaps(false);
            del.setOnClickListener(v -> { Skin.imageFile(BuilderActivity.this).delete(); buildControls(); changed(); });
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
                } else Toast.makeText(this, Lang.t(19), Toast.LENGTH_LONG).show();
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
                if (fromUser) SideConfig.set(BuilderActivity.this, key, p + lo);
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
        sw.setOnCheckedChangeListener((v, c) -> SideConfig.set(BuilderActivity.this, key, c ? 1 : 0));
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
        TextView name = label(ok ? SideLauncher.label(this, pkg) : pkg + Lang.t(20), 14f, ok ? txt : sub, 0);
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
        Toast.makeText(this, Lang.t(21), Toast.LENGTH_SHORT).show();
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
                    .setTitle(Lang.t(22))
                    .setMultiChoiceItems(names, on, (dlg, which, checked) -> on[which] = checked)
                    .setPositiveButton(Lang.t(23), (dlg, w) -> {
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
                    .setNegativeButton(Lang.t(24), null)
                    .show();
            });
        }).start();
    }

    private void buildSide() {
        // ---- modo y burbuja ----
        section(Lang.t(542));
        final boolean threeBtn = SideConfig.threeButtonNav(this);
        chipsRow(Lang.t(25), Arrays.asList(Lang.t(26), Lang.t(543), Lang.t(27)), SideConfig.getMode(this), i -> {
            if (i == SideConfig.MODE_GESTURE && !threeBtn) {
                Toast.makeText(this, Lang.t(28), Toast.LENGTH_LONG).show();
            } else {
                SideConfig.setMode(this, i);
            }
            buildControls();
        });
        note(Lang.t(918));
        note(Lang.t(29) + (ControlService.instance != null ? Lang.t(914) : Lang.t(915))
            + Lang.t(30) + (MediaListener.instance != null ? Lang.t(914) : Lang.t(916))
            + Lang.t(31) + (threeBtn ? Lang.t(837) : Lang.t(32)));
        sideSlider(Lang.t(33), "bubbleSize", 32, 72, " dp");
        sideSlider(Lang.t(34), "bubbleAlpha", 30, 100, "%");
        sideSlider(Lang.t(544), "holdSecs", 3, 15, " s");
        toolBtn(Lang.t(35), () -> {
            SideHub h = sideHub();
            if (h != null && SideConfig.getMode(this) == SideConfig.MODE_BUBBLE) { h.enterCanvas(); moveTaskToBack(true); }
            else Toast.makeText(this, Lang.t(36), Toast.LENGTH_SHORT).show();
        });
        toolBtn(Lang.t(37), () -> { SideConfig.resetPos(this); });
        toolBtn(Lang.t(38), () -> {
            SideHub h = sideHub();
            if (h != null && SideConfig.getMode(this) != SideConfig.MODE_OFF) { h.open(); moveTaskToBack(true); }
            else Toast.makeText(this, Lang.t(39), Toast.LENGTH_SHORT).show();
        });
        note(Lang.t(40));

        // ---- notificaciones ----
        section(Lang.t(545));
        sideToggle(Lang.t(41), "noteOn");
        sideSlider(Lang.t(42), "noteSecs", 2, 15, " s");
        toolBtn(Lang.t(546), () -> {
            SideHub h = sideHub();
            if (h == null || !h.test()) Toast.makeText(this, Lang.t(39), Toast.LENGTH_SHORT).show();
            else moveTaskToBack(true);
        });
        note(Lang.t(919));

        // ---- accesos ----
        section(Lang.t(547));
        final List<String> apps = SideConfig.shortcuts(this);
        if (apps.isEmpty()) note(Lang.t(43));
        for (int i = 0; i < apps.size(); i++) sideShortcutRow(i, apps.get(i), apps.size());
        toolBtn(Lang.t(44), this::pickSideApps);
        note(Lang.t(45));

        // ---- ventanas ----
        section(Lang.t(548));
        sideToggle(Lang.t(46), "floatWin");
        sideSlider(Lang.t(47), "winMode", 1, 6, "");
        sideSlider(Lang.t(48), "winSize", 20, 100, "%");
        sideSlider(Lang.t(49), "winH", 20, 100, "%");
        sideSlider(Lang.t(50), "winX", 0, 100, "%");
        sideSlider(Lang.t(51), "winY", 0, 100, "%");
        sideToggle(Lang.t(52), "winBounds");
        sideToggle(Lang.t(53), "winHack");
        sideSlider(Lang.t(54), "winHackMs", 0, 600, " ms");
        sideToggle(Lang.t(55), "winExempt");
        sideToggle(Lang.t(56), "winSettings");
        toolBtn(Lang.t(57), () -> {
            if (Privileged.available()) {
                SideLauncher.prepareFreeform();
                Toast.makeText(this, Lang.t(58), Toast.LENGTH_LONG).show();
            } else Toast.makeText(this, Lang.t(59), Toast.LENGTH_LONG).show();
        });
        note(Lang.t(60));

        // ---- gestos ----
        section(Lang.t(549));
        note(threeBtn ? Lang.t(61)
            : Lang.t(62));
        sideSlider(Lang.t(550), "edgeW", 12, 40, " dp");
        sideSlider(Lang.t(551), "edgeH", 30, 100, "%");
    }

    // ======================= la lista de opciones =======================

    private void buildAll() {
        Skin sk = new Skin(this);
        final String[] zoneKeys = {"left", "center", "right", "full"};
        List<String> zoneLabels = Arrays.asList(Lang.t(63), Lang.t(64), Lang.t(65), Lang.t(66));

        if (item(0)) controls.addView(label(Lang.t(67), 12f, sub, 6));

        section(Lang.t(552));
        List<String> bgLabels = new ArrayList<>();
        for (BgMode m : BgMode.values()) bgLabels.add(m.label);
        choice(Lang.t(838), "bgMode", bgLabels, true, i -> {
            BgMode m = BgMode.values()[i];
            int dim = Skin.get(this, "dim");
            if (m == BgMode.SOLID && dim < 50) Skin.set(this, "dim", 92);
            if (m != BgMode.SOLID && dim > 70) Skin.set(this, "dim", 35);
        });
        switch (sk.bgMode) {
            case SOLID:
                colorRow(Lang.t(68), "bgColor", false);
                skSlider(Lang.t(553), "dim", 0, 100, "%");
                break;
            case TRANSPARENT:
                note(Lang.t(69));
                break;
            case BLUR:
                skSlider(Lang.t(554), "blurR", 1, 12);
                skSlider(Lang.t(555), "dim", 0, 90, "%");
                colorRow(Lang.t(839), "bgColor", false);
                note(Lang.t(70));
                break;
            case IMAGE:
                imageRow();
                skSlider(Lang.t(71), "imgBlur", 0, 12);
                skSlider(Lang.t(555), "dim", 0, 90, "%");
                colorRow(Lang.t(839), "bgColor", false);
                break;
        }

        section(Lang.t(556));
        colorRow(Lang.t(72), "sheetColor", true);
        skSlider(Lang.t(73), "sheetAlpha", 0, 100, "%");
        skSlider(Lang.t(74), "sheetRadius", 0, 48, " dp");
        skSlider(Lang.t(75), "sheetTopRadius", 0, 48, " dp");
        colorRow(Lang.t(76), "sheetColor2", true);
        choice(Lang.t(77), "sheetAngle", Arrays.asList(Lang.t(78), Lang.t(79), Lang.t(80), Lang.t(81)));
        skSlider(Lang.t(82), "sheetStroke", 0, 8, " dp");
        colorRow(Lang.t(83), "sheetStrokeColor", true);
        skSlider(Lang.t(84), "sheetWidth", 50, 100, "%");
        skSlider(Lang.t(85), "sidePad", 0, 40, " dp");
        skSlider(Lang.t(86), "topPad", 0, 120, " dp");

        section(Lang.t(557));
        List<String> shapeLabels = new ArrayList<>();
        for (TileShape s : TileShape.values()) shapeLabels.add(s.sym + " " + s.label);
        choice(Lang.t(87), "shape", shapeLabels, true);
        if (sk.shape == TileShape.ROUNDED) skSlider(Lang.t(88), "round", 0, 50, "%");
        List<String> layoutLabels = new ArrayList<>();
        for (TileLayout l : TileLayout.values()) layoutLabels.add(l.label);
        choice(Lang.t(89), "layout", layoutLabels, true);
        skSlider(Lang.t(558), "size", 36, 110, " dp");
        skSlider(Lang.t(90), "gap", 0, 24, " dp");
        skSlider(Lang.t(91), "cols", 2, 6);
        if (sk.layout == TileLayout.PILL) note(Lang.t(92));
        skSlider(Lang.t(93), "iconScale", 30, 80, "%");
        if (sk.layout != TileLayout.ICON) skSlider(Lang.t(94), "labelSize", 9, 16, " sp");
        if (sk.layout == TileLayout.PILL) toggle(Lang.t(95), "showSub");
        if (sk.layout == TileLayout.PILL) skSlider(Lang.t(96), "pillRound", 0, 100, "%");
        skSlider(Lang.t(97), "tileStroke", 0, 8, " dp");
        colorRow(Lang.t(98), "strokeColor", true);
        skSlider(Lang.t(99), "tileRot", 0, 359, "°");
        skSlider(Lang.t(100), "onScale", 60, 140, "%");
        skSlider(Lang.t(101), "offScale", 40, 140, "%");
        toggle(Lang.t(102), "labelCaps");
        colorRow(Lang.t(103), "labelColor", true);
        colorRow(Lang.t(104), "subColor", true);
        colorRow(Lang.t(105), "onFg", true);

        section(Lang.t(559));
        choice(Lang.t(840), "theme", Arrays.asList(Lang.t(841), Lang.t(106), Lang.t(107)));
        colorRow(Lang.t(108), "accent", true);
        skSlider(Lang.t(109), "onAlpha", 20, 100, "%");
        colorRow(Lang.t(110), "tileOff", true);
        skSlider(Lang.t(111), "offAlpha", 0, 100, "%");
        colorRow(Lang.t(112), "textColor", true);

        section(Lang.t(560));
        skSlider(Lang.t(558), "clockSize", 16, 96, " sp");
        choice(Lang.t(113), "clockAlign", Arrays.asList(Lang.t(63), Lang.t(64), Lang.t(65)));
        choice(Lang.t(114), "clockWeight", Arrays.asList(Lang.t(115), Lang.t(561), Lang.t(116)));
        toggle(Lang.t(117), "showDate");
        toggle(Lang.t(118), "showGear");
        skSlider(Lang.t(119), "gearSize", 24, 80, " dp");
        toggle(Lang.t(120), "showEdit");
        choice(Lang.t(121), "clockFormat", Arrays.asList("24 h", "12 h"));
        toggle(Lang.t(122), "showSeconds");
        choice(Lang.t(123), "dateFormat", Arrays.asList(Lang.t(124), Lang.t(842), "15/11/2026", "2026-11-15"));
        skSlider(Lang.t(125), "dateSize", 8, 32, " sp");
        choice(Lang.t(126), "clockFont", Arrays.asList(Lang.t(127), Lang.t(128), Lang.t(129), Lang.t(130), Lang.t(131), Lang.t(132), Lang.t(133), Lang.t(134), Lang.t(135)));
        toggle(Lang.t(136), "clockItalic");
        colorRow(Lang.t(137), "clockColor", true);
        colorRow(Lang.t(138), "dateColor", true);
        skSlider(Lang.t(562), "clockPadV", 0, 48, " dp");

        section(Lang.t(563));
        skSlider(Lang.t(139), "compact", 0, 8);
        note(Lang.t(140));

        section(Lang.t(564));
        choice(Lang.t(141), "sliderStyle", Arrays.asList(Lang.t(142), Lang.t(143)));
        skSlider(Lang.t(144), "sliderThick", 40, 90, " dp");
        toggle(Lang.t(843), "showMedia");

        section(Lang.t(565));
        skSlider(Lang.t(145), "cardR", 0, 40, " dp");
        skSlider(Lang.t(553), "cardAlpha", 0, 100, "%");
        toggle(Lang.t(146), "notifApp");

        section(Lang.t(566));
        choice(Lang.t(147), "fontFamily", Arrays.asList(Lang.t(128), Lang.t(129), Lang.t(130), Lang.t(131), Lang.t(132), Lang.t(133), Lang.t(134), Lang.t(135)));
        skSlider(Lang.t(148), "textScale", 50, 200, "%");
        toggle(Lang.t(149), "textBold");
        toggle(Lang.t(150), "textItalic");
        skSlider(Lang.t(567), "letterSp", 0, 40);

        section(Lang.t(568));
        colorRow(Lang.t(151), "cardColor", true);
        skSlider(Lang.t(152), "cardStroke", 0, 6, " dp");
        colorRow(Lang.t(153), "cardStrokeColor", true);
        skSlider(Lang.t(154), "notifLines", 1, 6);
        skSlider(Lang.t(155), "notifOpenLines", 2, 16);
        skSlider(Lang.t(156), "notifTitleSize", 8, 24, " sp");
        skSlider(Lang.t(94), "notifTextSize", 8, 24, " sp");
        toggle(Lang.t(157), "notifShowTime");
        skSlider(Lang.t(158), "notifGap", 0, 24, " dp");
        skSlider(Lang.t(159), "notifMaxH", 10, 80, "%");
        skSlider(Lang.t(93), "notifIconSize", 20, 64, " dp");
        skSlider(Lang.t(569), "mediaH", 80, 260, " dp");
        skSlider(Lang.t(160), "vCorner", 10, 40);
        toggle(Lang.t(161), "showClear");

        section(Lang.t(570));
        choice(Lang.t(141), "animStyle", Arrays.asList(Lang.t(162), Lang.t(163), Lang.t(164), Lang.t(165), Lang.t(166), Lang.t(167)));
        skSlider(Lang.t(168), "animDur", 0, 1200, " ms");
        skSlider(Lang.t(169), "tabAnimDur", 0, 1200, " ms");

        section(Lang.t(571));
        toggle(Lang.t(844), "closeTap");
        toggle(Lang.t(845), "closeOnAct");
        toggle(Lang.t(846), "haptic");
        choice(Lang.t(170), "startStage", Arrays.asList(Lang.t(171), Lang.t(172)));
        choice(Lang.t(173), "defaultTab", Arrays.asList(Lang.t(874), Lang.t(8)));
        toggle(Lang.t(174), "showTabs");
        toggle(Lang.t(175), "showGrab");
        skSlider(Lang.t(176), "grabW", 16, 200, " dp");
        skSlider(Lang.t(177), "grabH", 2, 16, " dp");

        section(Lang.t(572));
        String cz = Skin.prefs(this).getString("zone", "full");
        int zi = Math.max(0, Arrays.asList(zoneKeys).indexOf(cz));
        chipsRow(Lang.t(847), zoneLabels, zi, i -> {
            Skin.prefs(this).edit().putString("zone", zoneKeys[i]).apply();
            ControlService s = ControlService.instance;
            if (s != null) s.rebuildHandle();
        });
        slider(Lang.t(178), 0, 100, "%", true,
            () -> Skin.prefs(this).getInt("pill_alpha", 60),
            v -> Skin.prefs(this).edit().putInt("pill_alpha", v).apply());
        skSlider(Lang.t(179), "pillW", 24, 240, " dp", true);
        skSlider(Lang.t(180), "pillH", 2, 14, " dp", true);
        colorRow(Lang.t(181), "pillColor", true);
        skSlider(Lang.t(182), "pillRadius", 0, 20, " dp", true);
        skSlider(Lang.t(183), "pillTop", 0, 40, " dp", true);
        skSlider(Lang.t(184), "handleH", 12, 160, " dp", true);
        skSlider(Lang.t(185), "openThr", 2, 120, " dp");

        section(Lang.t(573));
        choice(Lang.t(186), "layoutMode", Arrays.asList(Lang.t(187), Lang.t(188)));
        skSlider(Lang.t(574), "canvasCols", 4, 16);
        toolBtn(Lang.t(575), () -> startActivity(new Intent(this, LayoutEditorActivity.class)));
        note(Lang.t(189));

        section(Lang.t(576));
        chips(Arrays.asList(Skin.PRESETS), -1, i -> { Skin.applyPreset(this, i); buildControls(); changed(true); });
        for (int sl = 1; sl <= 3; sl++) {
            final int slot = sl;
            toolBtn(Lang.t(190) + slot, () -> {
                Skin.saveSlot(this, slot);
                Toast.makeText(this, Lang.t(191) + slot, Toast.LENGTH_SHORT).show();
            });
            toolBtn(Lang.t(192) + slot, () -> {
                int n = Skin.loadSlot(this, slot);
                if (n < 0) Toast.makeText(this, Lang.t(193), Toast.LENGTH_SHORT).show();
                else { buildControls(); changed(true); }
            });
        }

        section(Lang.t(577));
        List<String> gl = Arrays.asList(Lang.t(578), Lang.t(579), Lang.t(580), Lang.t(581), Lang.t(582),
            Lang.t(583), Lang.t(584), Lang.t(585), Lang.t(586), Lang.t(587), Lang.t(588),
            Lang.t(589), Lang.t(590));
        choice(Lang.t(848), "gestTap", gl);
        textInput(Lang.t(194), "gestTapP", "");
        choice(Lang.t(849), "gestDouble", gl);
        textInput(Lang.t(195), "gestDoubleP", "");
        choice(Lang.t(850), "gestLong", gl);
        textInput(Lang.t(196), "gestLongP", "");
        note(Lang.t(197));

        section(Lang.t(591));
        choice(Lang.t(198), "fabMode", Arrays.asList(Lang.t(199), Lang.t(200), Lang.t(201)));
        skSlider(Lang.t(558), "fabSize", 32, 96, " dp");
        textInput(Lang.t(851), "txtFab", "✎");
        note(Lang.t(202));

        section(Lang.t(592));
        choice(Lang.t(852), "tapAnim", Arrays.asList(Lang.t(203), Lang.t(204), Lang.t(205), Lang.t(206), Lang.t(207), Lang.t(208)));
        choice(Lang.t(853), "entryAnim", Arrays.asList(Lang.t(203), Lang.t(209), Lang.t(210), Lang.t(211)));

        section(Lang.t(593));
        toggle(Lang.t(212), "autoTheme");
        skSlider(Lang.t(213), "autoFrom", 0, 23, " h");
        skSlider(Lang.t(214), "autoTo", 0, 23, " h");
        toggle(Lang.t(215), "lowBatRed");

        section(Lang.t(594));
        final List<String[]> cts = Skin.customTiles(this);
        for (final String[] f : cts) {
            if (item(1)) controls.addView(label("★ " + (f[2].isEmpty() ? Lang.t(595) : f[2]), 14f, acc, 14));
            chips(Arrays.asList(Lang.t(216), Lang.t(217), Lang.t(218), Lang.t(219)), parseInt0(f[1]), i -> {
                f[1] = String.valueOf(i);
                Skin.saveCustomTiles(this, cts);
                changed();
            });
            textEdit(Lang.t(854), f[2], v -> { f[2] = v; Skin.saveCustomTiles(this, cts); changed(); });
            textEdit(Lang.t(920), f[3], v -> {
                f[3] = v;
                Skin.saveCustomTiles(this, cts);
                changed();
            });
            toolBtn(Lang.t(596), () -> { cts.remove(f); Skin.saveCustomTiles(this, cts); buildControls(); changed(); });
        }
        toolBtn(Lang.t(220), () -> {
            String id = Long.toString(System.currentTimeMillis(), 36);
            cts.add(new String[]{id, "0", Lang.t(221), ""});
            Skin.saveCustomTiles(this, cts);
            enableTile("ct:" + id);
            buildControls();
            changed();
        });

        section(Lang.t(597));
        final List<String> tkeys = new ArrayList<>(Arrays.asList(PanelView.TILE_KEYS));
        final List<String> tnames = new ArrayList<>(Lang.sAll(PanelView.TILE_KEYS));
        for (String[] f : cts) {
            tkeys.add("ct:" + f[0]);
            tnames.add("★ " + (f[2].isEmpty() ? Lang.t(595) : f[2]));
        }
        if (selTile >= tkeys.size()) selTile = 0;
        chips(tnames, selTile, i -> { selTile = i; buildControls(); });
        String selKey = tkeys.get(selTile);
        textInput(Lang.t(854), "lbl_" + selKey, tnames.get(selTile));
        textInput(Lang.t(222), "glyph_" + selKey, "");
        colorRow(Lang.t(223), "tOn_" + selKey, true);
        colorRow(Lang.t(224), "tOff_" + selKey, true);
        skSlider(Lang.t(598), "tSpan_" + selKey, 1, 6);

        section(Lang.t(599));
        textInput(Lang.t(225), "txtTab0", Lang.t(874));
        textInput(Lang.t(226), "txtTab1", Lang.t(8));
        textInput(Lang.t(227), "txtClear", Lang.t(600));
        textInput(Lang.t(228), "txtEmpty", Lang.t(601));
        textInput(Lang.t(229), "txtGear", "⚙");
        textInput(Lang.t(230), "txtEditBtn", "✎");
        textInput(Lang.t(231), "txtEditTiles", Lang.t(232));

        section(Lang.t(602));
        for (String tk : PanelView.TILE_KEYS) textInput(Lang.s(tk), "lbl_" + tk, Lang.s(tk));

        buildSide();

        section(Lang.t(603));
        toolBtn(Lang.t(233), () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("zc-control", Skin.export(this)));
            Toast.makeText(this, Lang.t(234), Toast.LENGTH_SHORT).show();
        });
        toolBtn(Lang.t(235), () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData cd = cm.getPrimaryClip();
            CharSequence tx = cd != null && cd.getItemCount() > 0 ? cd.getItemAt(0).coerceToText(this) : null;
            int n = tx == null ? -1 : Skin.importAll(this, tx.toString());
            if (n < 0) Toast.makeText(this, Lang.t(236), Toast.LENGTH_LONG).show();
            else { Toast.makeText(this, Lang.t(237) + n + Lang.t(855), Toast.LENGTH_SHORT).show(); buildControls(); changed(true); }
        });
        toolBtn(Lang.t(238), () -> { Skin.randomize(this); buildControls(); changed(true); });

        Button reset = new Button(this);
        reset.setText(Lang.t(239));
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> { Skin.reset(BuilderActivity.this); buildControls(); changed(true); });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MPB, WCB);
        rlp.topMargin = dp(28);
        if (item(1)) controls.addView(reset, rlp);

    }

    private void buildControls() {
        final int keep = keepZero ? 0 : (scroll != null ? scroll.getScrollY() : 0);
        keepZero = false;
        controls.removeAllViews();
        pageTitles.clear();
        if (only != null) {
            pageTitles.add(onlyTitle != null ? onlyTitle : Lang.t(240));
            curPage = 0;
            dry = false;
            secOn = false;
            gCur = 0;
            gCountPage = 0;
            buildAll();
            scroll.post(() -> scroll.scrollTo(0, keep));
            return;
        }
        dry = true;
        gCur = 0;
        gCountPage = 0;
        buildAll();
        if (pageTitles.isEmpty()) pageTitles.add(Lang.t(240));
        if (curPage >= pageTitles.size()) curPage = pageTitles.size() - 1;
        if (curPage < 0) curPage = 0;
        dry = false;
        gCur = 0;
        gCountPage = 0;
        buildAll();
        updateNav();
        scroll.post(() -> scroll.scrollTo(0, keep));
    }
}
