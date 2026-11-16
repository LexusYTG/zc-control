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

    private final int txt = 0xFFEDE7F6;
    private final int sub = 0xFFB0A9B5;
    private final int acc = 0xFFB69DF8;
    private final int chipOff = 0xFF2A2833;
    private final int chipOn = 0xFF5B4B9A;

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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121216);

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
        title.setText("Make your action bar");
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
        stateBtn(bar, "Acotada", 1, 0); stateBtn(bar, "Completa", 2, 0); stateBtn(bar, "Controles", 2, 1);
        Button real = new Button(this);
        real.setText("▶ Real");
        real.setAllCaps(false);
        real.setTextSize(12f);
        real.setPadding(0, 0, 0, 0);
        real.setOnClickListener(v -> {
            ControlService svc = ControlService.instance;
            if (svc != null) svc.open();
            else Toast.makeText(BuilderActivity.this, "El servicio todavía no está activo", Toast.LENGTH_LONG).show();
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
        new AlertDialog.Builder(this).setTitle("Ir a la pantalla").setItems(names, (d, w) -> goPage(w)).show();
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
        if (gCountPage >= 5) newPage();
        if (dry && pageTitles.size() <= gCur) pageTitles.add(title);
        if (dry || gCur != curPage) return;
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

    private void buildSide() {
        // ---- modo y burbuja ----
        section("INTERFAZ LATERAL");
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
        sideSlider("Tamaño de la burbuja", "bubbleSize", 32, 72, " dp");
        sideSlider("Opacidad de la burbuja", "bubbleAlpha", 30, 100, "%");
        sideSlider("Mantener apretada para moverla", "holdSecs", 3, 15, " s");
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

        // ---- notificaciones ----
        section("INTERFAZ LATERAL · NOTIFICACIONES");
        sideToggle("Mostrar notificaciones nuevas con animación", "noteOn");
        sideSlider("Tiempo visible", "noteSecs", 2, 15, " s");
        toolBtn("Probar una notificación", () -> {
            SideHub h = sideHub();
            if (h == null || !h.test()) Toast.makeText(this, "Activá la burbuja o los gestos primero", Toast.LENGTH_SHORT).show();
            else moveTaskToBack(true);
        });
        note("Solo avisa de notificaciones normales: no muestra las silenciosas, las fijas, las de progreso ni las de la propia app. Tocá la tarjeta para abrir la app; deslizala hacia arriba para descartarla.");

        // ---- accesos ----
        section("INTERFAZ LATERAL · ACCESOS DEL PANEL");
        final List<String> apps = SideConfig.shortcuts(this);
        if (apps.isEmpty()) note("Todavía no hay apps en el panel.");
        for (int i = 0; i < apps.size(); i++) sideShortcutRow(i, apps.get(i), apps.size());
        toolBtn("＋ Agregar o quitar apps", this::pickSideApps);
        note("También podés editar los accesos desde el propio panel: tocá ✎ o mantené apretada una app.");

        // ---- ventanas ----
        section("INTERFAZ LATERAL · VENTANAS");
        sideToggle("Abrir las apps en ventana flotante", "floatWin");
        sideSlider("Tamaño de la ventana (ancho de pantalla)", "winSize", 50, 95, "%");
        toolBtn("Preparar ventanas flotantes (Shizuku)", () -> {
            if (Privileged.available()) {
                SideLauncher.prepareFreeform();
                Toast.makeText(this, "Listo. En algunos equipos hay que reiniciar para que tome efecto", Toast.LENGTH_LONG).show();
            } else Toast.makeText(this, "Necesita Shizuku activo", Toast.LENGTH_LONG).show();
        });
        note("Funciona si el sistema y la app lo permiten. Si no, la app se abre normalmente en pantalla completa.");

        // ---- gestos ----
        section("INTERFAZ LATERAL · GESTOS");
        note(threeBtn ? "Tu equipo usa navegación de tres botones: el modo gestos está disponible."
            : "Tu equipo usa navegación por gestos (o dos botones): el modo gestos queda desactivado para no chocar con los gestos de Android.");
        sideSlider("Ancho de la zona del borde", "edgeW", 12, 40, " dp");
        sideSlider("Alto de la zona del borde", "edgeH", 30, 100, "%");
    }

    // ======================= la lista de opciones =======================

    private void buildAll() {
        Skin sk = new Skin(this);
        final String[] zoneKeys = {"left", "center", "right", "full"};
        List<String> zoneLabels = Arrays.asList("Izquierda", "Centro", "Derecha", "Todo arriba");

        if (item(0)) controls.addView(label("Los cambios se ven al instante en la vista previa de arriba (es la barra de verdad). Probá todo.", 12f, sub, 6));

        section("FONDO DETRÁS DEL PANEL");
        List<String> bgLabels = new ArrayList<>();
        for (BgMode m : BgMode.values()) bgLabels.add(m.label);
        choice("Tipo de fondo", "bgMode", bgLabels, true, i -> {
            BgMode m = BgMode.values()[i];
            int dim = Skin.get(this, "dim");
            if (m == BgMode.SOLID && dim < 50) Skin.set(this, "dim", 92);
            if (m != BgMode.SOLID && dim > 70) Skin.set(this, "dim", 35);
        });
        switch (sk.bgMode) {
            case SOLID:
                colorRow("Color del fondo", "bgColor", false);
                skSlider("Opacidad", "dim", 0, 100, "%");
                break;
            case TRANSPARENT:
                note("El panel flota sobre lo que estés usando, sin oscurecer ni difuminar.");
                break;
            case BLUR:
                skSlider("Intensidad del difuminado", "blurR", 1, 12);
                skSlider("Oscurecer", "dim", 0, 90, "%");
                colorRow("Tinte", "bgColor", false);
                note("Usa el difuminado del sistema (Android 12 o superior). Si tu equipo no lo permite, el fondo se oscurece. No toma capturas de pantalla.");
                break;
            case IMAGE:
                imageRow();
                skSlider("Desenfoque de la imagen", "imgBlur", 0, 12);
                skSlider("Oscurecer", "dim", 0, 90, "%");
                colorRow("Tinte", "bgColor", false);
                break;
        }

        section("HOJA DEL PANEL");
        colorRow("Color de la hoja", "sheetColor", true);
        skSlider("Opacidad de la hoja (0 = sin hoja)", "sheetAlpha", 0, 100, "%");
        skSlider("Redondeo de abajo", "sheetRadius", 0, 48, " dp");
        skSlider("Redondeo de arriba", "sheetTopRadius", 0, 48, " dp");
        colorRow("Segundo color (degradado)", "sheetColor2", true);
        choice("Dirección del degradado", "sheetAngle", Arrays.asList("Arriba a abajo", "Izquierda a derecha", "Abajo a arriba", "Derecha a izquierda"));
        skSlider("Borde de la hoja", "sheetStroke", 0, 8, " dp");
        colorRow("Color del borde de la hoja", "sheetStrokeColor", true);
        skSlider("Ancho de la hoja", "sheetWidth", 50, 100, "%");
        skSlider("Margen lateral", "sidePad", 0, 40, " dp");
        skSlider("Margen superior", "topPad", 0, 120, " dp");

        section("BOTONES");
        List<String> shapeLabels = new ArrayList<>();
        for (TileShape s : TileShape.values()) shapeLabels.add(s.sym + " " + s.label);
        choice("Forma", "shape", shapeLabels, true);
        if (sk.shape == TileShape.ROUNDED) skSlider("Redondeo de la forma", "round", 0, 50, "%");
        List<String> layoutLabels = new ArrayList<>();
        for (TileLayout l : TileLayout.values()) layoutLabels.add(l.label);
        choice("Disposición", "layout", layoutLabels, true);
        skSlider("Tamaño", "size", 36, 110, " dp");
        skSlider("Separación", "gap", 0, 24, " dp");
        skSlider("Columnas", "cols", 2, 6);
        if (sk.layout == TileLayout.PILL) note("En píldora se usan hasta 3 columnas.");
        skSlider("Tamaño del ícono", "iconScale", 30, 80, "%");
        if (sk.layout != TileLayout.ICON) skSlider("Tamaño del texto", "labelSize", 9, 16, " sp");
        if (sk.layout == TileLayout.PILL) toggle("Mostrar el estado (texto chico)", "showSub");
        if (sk.layout == TileLayout.PILL) skSlider("Redondeo de la píldora", "pillRound", 0, 100, "%");
        skSlider("Grosor del borde", "tileStroke", 0, 8, " dp");
        colorRow("Color del borde", "strokeColor", true);
        skSlider("Rotación de cada botón", "tileRot", 0, 359, "°");
        skSlider("Escala encendido", "onScale", 60, 140, "%");
        skSlider("Escala apagado", "offScale", 40, 140, "%");
        toggle("Etiquetas en MAYÚSCULAS", "labelCaps");
        colorRow("Color de las etiquetas", "labelColor", true);
        colorRow("Color del subtexto", "subColor", true);
        colorRow("Color del texto encendido", "onFg", true);

        section("COLORES");
        choice("Tema del texto", "theme", Arrays.asList("Sistema", "Claro", "Oscuro"));
        colorRow("Acento (botón encendido)", "accent", true);
        skSlider("Opacidad encendido", "onAlpha", 20, 100, "%");
        colorRow("Botón apagado", "tileOff", true);
        skSlider("Opacidad apagado", "offAlpha", 0, 100, "%");
        colorRow("Texto e íconos", "textColor", true);

        section("RELOJ");
        skSlider("Tamaño", "clockSize", 16, 96, " sp");
        choice("Alineación", "clockAlign", Arrays.asList("Izquierda", "Centro", "Derecha"));
        choice("Grosor", "clockWeight", Arrays.asList("Fino", "Normal", "Negrita"));
        toggle("Mostrar la fecha", "showDate");
        toggle("Botón de ajustes ⚙", "showGear");
        skSlider("Tamaño de los botones ⚙ y ✎", "gearSize", 24, 80, " dp");
        toggle("Botón ✎ para editar accesos", "showEdit");
        choice("Formato de hora", "clockFormat", Arrays.asList("24 h", "12 h"));
        toggle("Mostrar segundos", "showSeconds");
        choice("Formato de fecha", "dateFormat", Arrays.asList("domingo 15 de noviembre", "dom 15 nov", "15/11/2026", "2026-11-15"));
        skSlider("Tamaño de la fecha", "dateSize", 8, 32, " sp");
        choice("Fuente del reloj", "clockFont", Arrays.asList("Según grosor", "Sans", "Serif", "Mono", "Casual", "Condensada", "Cursiva", "Fina", "Media"));
        toggle("Reloj en cursiva", "clockItalic");
        colorRow("Color del reloj", "clockColor", true);
        colorRow("Color de la fecha", "dateColor", true);
        skSlider("Margen vertical del encabezado", "clockPadV", 0, 48, " dp");

        section("PRIMERA BAJADA");
        skSlider("Accesos visibles", "compact", 0, 8);
        note("Son los primeros de tu lista. Para ordenarlos, usá el botón ✎ del panel real.");

        section("BRILLO Y VOLUMEN");
        choice("Estilo", "sliderStyle", Arrays.asList("Horizontal", "Vertical"));
        skSlider("Tamaño de los sliders", "sliderThick", 40, 90, " dp");
        toggle("Mostrar el reproductor", "showMedia");

        section("TARJETAS Y NOTIFICACIONES");
        skSlider("Redondeo", "cardR", 0, 40, " dp");
        skSlider("Opacidad", "cardAlpha", 0, 100, "%");
        toggle("Ícono de la app en las notificaciones", "notifApp");

        section("TIPOGRAFÍA");
        choice("Fuente", "fontFamily", Arrays.asList("Sans", "Serif", "Mono", "Casual", "Condensada", "Cursiva", "Fina", "Media"));
        skSlider("Escala del texto", "textScale", 50, 200, "%");
        toggle("Negrita en todo", "textBold");
        toggle("Cursiva en todo", "textItalic");
        skSlider("Espaciado entre letras", "letterSp", 0, 40);

        section("DETALLE DE NOTIFICACIONES Y TARJETAS");
        colorRow("Color de las tarjetas", "cardColor", true);
        skSlider("Borde de las tarjetas", "cardStroke", 0, 6, " dp");
        colorRow("Color del borde de las tarjetas", "cardStrokeColor", true);
        skSlider("Líneas de texto (colapsada)", "notifLines", 1, 6);
        skSlider("Líneas de texto (expandida)", "notifOpenLines", 2, 16);
        skSlider("Tamaño del título", "notifTitleSize", 8, 24, " sp");
        skSlider("Tamaño del texto", "notifTextSize", 8, 24, " sp");
        toggle("Mostrar la hora", "notifShowTime");
        skSlider("Separación entre notificaciones", "notifGap", 0, 24, " dp");
        skSlider("Alto máximo de la lista", "notifMaxH", 10, 80, "%");
        skSlider("Tamaño del ícono", "notifIconSize", 20, 64, " dp");
        skSlider("Alto del reproductor", "mediaH", 80, 260, " dp");
        skSlider("Redondeo de los sliders verticales", "vCorner", 10, 40);
        toggle("Mostrar el botón de borrar todo", "showClear");

        section("ANIMACIÓN");
        choice("Estilo", "animStyle", Arrays.asList("Desacelera", "Suave", "Rebote hacia afuera", "Rebota", "Lineal", "Se echa hacia atrás"));
        skSlider("Duración", "animDur", 0, 1200, " ms");
        skSlider("Duración de las pestañas", "tabAnimDur", 0, 1200, " ms");

        section("COMPORTAMIENTO");
        toggle("Tocar fuera cierra el panel", "closeTap");
        toggle("Cerrar al abrir un ajuste", "closeOnAct");
        toggle("Vibración al tocar", "haptic");
        choice("Con el botón Probar abre en", "startStage", Arrays.asList("Vista acotada", "Vista completa"));
        choice("Pestaña inicial", "defaultTab", Arrays.asList("Notificaciones", "Controles"));
        toggle("Mostrar las pestañas", "showTabs");
        toggle("Mostrar la barrita de abajo", "showGrab");
        skSlider("Ancho de la barrita de abajo", "grabW", 16, 200, " dp");
        skSlider("Grosor de la barrita de abajo", "grabH", 2, 16, " dp");

        section("BARRITA DE ACCESO");
        String cz = Skin.prefs(this).getString("zone", "full");
        int zi = Math.max(0, Arrays.asList(zoneKeys).indexOf(cz));
        chipsRow("Zona de apertura", zoneLabels, zi, i -> {
            Skin.prefs(this).edit().putString("zone", zoneKeys[i]).apply();
            ControlService s = ControlService.instance;
            if (s != null) s.rebuildHandle();
        });
        slider("Opacidad de la barrita (0 = invisible)", 0, 100, "%", true,
            () -> Skin.prefs(this).getInt("pill_alpha", 60),
            v -> Skin.prefs(this).edit().putInt("pill_alpha", v).apply());
        skSlider("Largo de la barrita", "pillW", 24, 240, " dp", true);
        skSlider("Grosor de la barrita", "pillH", 2, 14, " dp", true);
        colorRow("Color de la barrita", "pillColor", true);
        skSlider("Redondeo de la barrita", "pillRadius", 0, 20, " dp", true);
        skSlider("Margen superior de la barrita", "pillTop", 0, 40, " dp", true);
        skSlider("Alto de la zona táctil", "handleH", 12, 160, " dp", true);
        skSlider("Distancia para empezar a bajar", "openThr", 2, 120, " dp");

        section("DISEÑO MANUAL (LIENZO)");
        choice("Modo de diseño", "layoutMode", Arrays.asList("Clásico (cuadrícula automática)", "Lienzo (lo armo a mano)"));
        skSlider("Columnas del lienzo", "canvasCols", 4, 16);
        toolBtn("Abrir el editor del lienzo", () -> startActivity(new Intent(this, LayoutEditorActivity.class)));
        note("En el lienzo ubicás cada elemento donde quieras y del tamaño que quieras: accesos, reloj, reproductor, notificaciones, sliders, imágenes, GIF, videos, widgets y textos.");

        section("PRESETS");
        chips(Arrays.asList(Skin.PRESETS), -1, i -> { Skin.applyPreset(this, i); buildControls(); changed(true); });
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

        section("GESTOS DE LA BARRITA");
        List<String> gl = Arrays.asList("Nada", "Abrir el panel", "Abrir el editor de accesos", "Captura de pantalla", "Bloquear pantalla",
            "Menú de apagado", "Inicio", "Atrás", "Recientes", "Notificaciones del sistema", "Ajustes rápidos del sistema",
            "Abrir una app (paquete)", "Ejecutar comando (Shizuku)");
        choice("Un toque", "gestTap", gl);
        textInput("Paquete o comando del toque", "gestTapP", "");
        choice("Doble toque", "gestDouble", gl);
        textInput("Paquete o comando del doble toque", "gestDoubleP", "");
        choice("Mantener apretado", "gestLong", gl);
        textInput("Paquete o comando de mantener apretado", "gestLongP", "");
        note("Bajar desde la barrita sigue abriendo el panel aunque tengas gestos asignados.");

        section("BOTÓN FLOTANTE DE EDICIÓN");
        choice("Mostrar", "fabMode", Arrays.asList("Oculto", "Solo en Controles", "Siempre"));
        skSlider("Tamaño", "fabSize", 32, 96, " dp");
        textInput("Símbolo", "txtFab", "✎");
        note("Se arrastra a cualquier lado y recuerda dónde lo dejaste.");

        section("EFECTOS");
        choice("Animación al tocar un acceso", "tapAnim", Arrays.asList("Ninguna", "Rebote", "Giro", "Vuelta", "Temblor", "Pulso"));
        choice("Animación de entrada de los accesos", "entryAnim", Arrays.asList("Ninguna", "Sube y aparece", "Crece", "Entra desde la izquierda"));

        section("REGLAS AUTOMÁTICAS");
        toggle("Tema oscuro por horario", "autoTheme");
        skSlider("Desde las", "autoFrom", 0, 23, " h");
        skSlider("Hasta las", "autoTo", 0, 23, " h");
        toggle("Acento rojo con batería baja (menos de 20%)", "lowBatRed");

        section("ACCESOS PROPIOS");
        final List<String[]> cts = Skin.customTiles(this);
        for (final String[] f : cts) {
            if (item(1)) controls.addView(label("★ " + (f[2].isEmpty() ? "Acceso" : f[2]), 14f, acc, 14));
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

        section("AJUSTE INDIVIDUAL DE CADA ACCESO");
        final List<String> tkeys = new ArrayList<>(Arrays.asList(PanelView.TILE_KEYS));
        final List<String> tnames = new ArrayList<>(Arrays.asList(PanelView.TILE_KEYS));
        for (String[] f : cts) {
            tkeys.add("ct:" + f[0]);
            tnames.add("★ " + (f[2].isEmpty() ? "Acceso" : f[2]));
        }
        if (selTile >= tkeys.size()) selTile = 0;
        chips(tnames, selTile, i -> { selTile = i; buildControls(); });
        String selKey = tkeys.get(selTile);
        textInput("Nombre", "lbl_" + selKey, tnames.get(selTile));
        textInput("Glifo (emoji o texto en lugar del ícono)", "glyph_" + selKey, "");
        colorRow("Color encendido", "tOn_" + selKey, true);
        colorRow("Color apagado", "tOff_" + selKey, true);
        skSlider("Columnas que ocupa", "tSpan_" + selKey, 1, 6);

        section("TEXTOS");
        textInput("Título de la pestaña 1", "txtTab0", "Notificaciones");
        textInput("Título de la pestaña 2", "txtTab1", "Controles");
        textInput("Texto de borrar todo", "txtClear", "Borrar todo");
        textInput("Texto sin notificaciones", "txtEmpty", "Sin notificaciones");
        textInput("Símbolo de ajustes", "txtGear", "⚙");
        textInput("Símbolo de editar", "txtEditBtn", "✎");
        textInput("Texto del botón de editar accesos", "txtEditTiles", "Editar accesos");

        section("NOMBRES DE LOS ACCESOS");
        for (String tk : PanelView.TILE_KEYS) textInput(tk, "lbl_" + tk, tk);

        buildSide();

        section("HERRAMIENTAS");
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
            else { Toast.makeText(this, "Se aplicaron " + n + " valores", Toast.LENGTH_SHORT).show(); buildControls(); changed(true); }
        });
        toolBtn("🎲 Aleatorizar todo", () -> { Skin.randomize(this); buildControls(); changed(true); });

        Button reset = new Button(this);
        reset.setText("Restablecer todo");
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
        dry = true;
        gCur = 0;
        gCountPage = 0;
        buildAll();
        if (pageTitles.isEmpty()) pageTitles.add("Ajustes");
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
