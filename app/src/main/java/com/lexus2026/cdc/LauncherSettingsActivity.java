package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import android.content.Intent;

import java.util.List;

/** Ajustes del launcher y editor de categorías. Todo se guarda al instante. */
public class LauncherSettingsActivity extends Activity {
    private static final String[] SHAPES = {Lang.t(619), Lang.t(620), Lang.t(621), Lang.t(622)};
    private static final int[] PALETTE = {0xFF7C4DFF, 0xFF2979FF, 0xFFE91E63, 0xFFFF9800, 0xFF00BCD4,
        0xFF4CAF50, 0xFFFFC107, 0xFFF44336, 0xFF607D8B, 0xFF8D6E63};
    private LauncherSettings st;
    private CategoryManager cm;
    private LinearLayout box, catBox;
    private float d;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new LauncherSettings(this);
        cm = new CategoryManager(this);
        d = getResources().getDisplayMetrics().density;
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0E0E1C);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        sv.addView(box);
        sv.setOnApplyWindowInsetsListener((v, in) -> {
            box.setPadding(dp(18), in.getSystemWindowInsetTop() + dp(16), dp(18), in.getSystemWindowInsetBottom() + dp(28));
            return in;
        });
        box.setPadding(dp(18), dp(40), dp(18), dp(28));
        add(text(Lang.t(623), 24, Color.WHITE));

        head(Lang.t(372));
        seek(Lang.t(373), LauncherSettings.RING1, 1, 7, "");
        seek(Lang.t(374), LauncherSettings.RING2, 0, 9, "");
        seek(Lang.t(375), LauncherSettings.ARC, 20, 300, "°");
        seek(Lang.t(376), LauncherSettings.BUBBLE, 24, 96, " dp");
        seek(Lang.t(377), LauncherSettings.GAP, 0, 60, " dp");
        seek(Lang.t(378), LauncherSettings.STAGE2, 10, 90, " dp");
        seek(Lang.t(379), LauncherSettings.SENS, 50, 200, " %");
        seek(Lang.t(380), LauncherSettings.HOLD, 0, 500, " ms");
        seek(Lang.t(381), LauncherSettings.ANIM, 80, 600, " ms");
        seek(Lang.t(382), LauncherSettings.R1, 0, 260, " dp");
        seek(Lang.t(383), LauncherSettings.R2GAP, 0, 140, " dp");
        seek(Lang.t(384), LauncherSettings.TRACK, 0, 80, " dp");
        seek(Lang.t(385), LauncherSettings.ROT, -90, 90, "°");
        final TextView fit = button(fitLabel());
        fit.setOnClickListener(v -> { st.set(LauncherSettings.FIT, 1 - st.get(LauncherSettings.FIT)); fit.setText(fitLabel()); });
        final TextView shape = button("");
        shape.setText(Lang.t(624) + SHAPES[st.get(LauncherSettings.SHAPE)]);
        shape.setOnClickListener(v -> {
            int n = (st.get(LauncherSettings.SHAPE) + 1) % SHAPES.length;
            st.set(LauncherSettings.SHAPE, n);
            shape.setText(Lang.t(624) + SHAPES[n]);
        });
        seek(Lang.t(386), LauncherSettings.RAD_X, -200, 200, " dp");
        seek(Lang.t(387), LauncherSettings.RAD_Y, -150, 300, " dp");
        add(text(Lang.t(388), 12, 0xFF8A8FA8));

        head(Lang.t(861));
        seek(Lang.t(389), LauncherSettings.DOCK_H, 56, 110, " dp");
        seek(Lang.t(390), LauncherSettings.DOCK_W, 30, 100, " %");
        seek(Lang.t(391), LauncherSettings.DOCK_Y, 0, 400, " dp");
        final TextView pos = button(posLabel());
        pos.setOnClickListener(v -> { st.set(LauncherSettings.DOCK_POS, (st.get(LauncherSettings.DOCK_POS) + 1) % 3); pos.setText(posLabel()); });
        final TextView dsk = button(deskLabel());
        dsk.setOnClickListener(v -> { st.set(LauncherSettings.DESKTOP, 1 - st.get(LauncherSettings.DESKTOP)); dsk.setText(deskLabel()); });
        seek(Lang.t(392), LauncherSettings.DOCK_ICON, 36, 64, " dp");
        seek(Lang.t(393), LauncherSettings.APP_ICON, 36, 72, " dp");
        seek(Lang.t(394), LauncherSettings.DIM, 0, 80, " %");
        final TextView bg = button("");
        bg.setText(bgLabel());
        bg.setOnClickListener(v -> { st.set(LauncherSettings.BG, 1 - st.get(LauncherSettings.BG)); bg.setText(bgLabel()); });
        final TextView nm = button("");
        nm.setText(newLabel());
        nm.setOnClickListener(v -> { st.set(LauncherSettings.NEW_MODE, 1 - st.get(LauncherSettings.NEW_MODE)); nm.setText(newLabel()); });
        button(Lang.t(395)).setOnClickListener(v ->
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), Lang.t(625))));
        button(Lang.t(396)).setOnClickListener(v -> colorDialog(LauncherSettings.BG_COLOR));
        button(Lang.t(397)).setOnClickListener(v -> colorDialog(LauncherSettings.ACCENT));

        head(Lang.t(398));
        catBox = new LinearLayout(this);
        catBox.setOrientation(LinearLayout.VERTICAL);
        add(catBox);
        button(Lang.t(399)).setOnClickListener(v -> editCategory(null));
        refreshCats();
        setContentView(sv);
    }

    private String posLabel() {
        int p = st.get(LauncherSettings.DOCK_POS);
        return Lang.t(400) + (p == 1 ? "izquierda" : p == 2 ? "derecha" : "centro");
    }

    private String deskLabel() {
        return Lang.t(626) + (st.get(LauncherSettings.DESKTOP) == 1 ? Lang.t(627) : Lang.t(917));
    }

    private String newLabel() {
        return Lang.t(628) + (st.get(LauncherSettings.NEW_MODE) == 1 ? Lang.t(629) : Lang.t(630));
    }

    private String fitLabel() { return Lang.t(631) + (st.get(LauncherSettings.FIT) == 1 ? Lang.t(627) : Lang.t(917)); }

    private String bgLabel() { return Lang.t(632) + (st.get(LauncherSettings.BG) == 1 ? Lang.t(633) : Lang.t(634)); }

    private void refreshCats() {
        catBox.removeAllViews();
        final List<CategoryManager.Category> l = cm.list();
        int r1 = st.get(LauncherSettings.RING1), r2 = st.get(LauncherSettings.RING2);
        for (int i = 0; i < l.size(); i++) {
            final CategoryManager.Category c = l.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView info = text(c.icon + "  " + c.name + "\n" + (i < r1 ? Lang.t(862) : i < r1 + r2 ? Lang.t(635)
                : Lang.t(401)), 15, Color.WHITE);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(small("▲", () -> { cm.move(c.id, -1); refreshCats(); }));
            row.addView(small("▼", () -> { cm.move(c.id, 1); refreshCats(); }));
            row.addView(small("✎", () -> editCategory(c)));
            row.addView(small("✕", () -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Lang.t(402) + c.name + "»?")
                .setMessage(Lang.t(403))
                .setPositiveButton(Lang.t(636), (x, w) -> { cm.remove(c.id); refreshCats(); })
                .setNegativeButton(Lang.t(24), null).show()));
            catBox.addView(row);
        }
    }

    private void editCategory(final CategoryManager.Category c) {
        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText name = new EditText(this), icon = new EditText(this), hex = new EditText(this);
        name.setHint(Lang.t(854)); name.setText(c == null ? "" : c.name);
        icon.setHint(Lang.t(404)); icon.setText(c == null ? "⭐" : c.icon);
        hex.setHint(Lang.t(405));
        hex.setText(String.format("%06X", (c == null ? PALETTE[0] : c.color) & 0xFFFFFF));
        for (EditText e : new EditText[]{name, icon, hex}) { e.setTextColor(Color.WHITE); e.setHintTextColor(0xFF888888); lay.addView(e); }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout sw = new LinearLayout(this);
        for (final int col : PALETTE) {
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            v.setBackground(g);
            v.setOnClickListener(x -> hex.setText(String.format("%06X", col & 0xFFFFFF)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMargins(0, dp(10), dp(8), dp(6));
            sw.addView(v, lp);
        }
        hs.addView(sw);
        lay.addView(hs);
        final int[] shp = {c == null ? -1 : c.shape};
        final TextView sh = button("");
        ((LinearLayout) sh.getParent()).removeView(sh); // el botón se añadió a la pantalla; aquí va en el diálogo
        sh.setText(shapeLabel(shp[0]));
        sh.setOnClickListener(v -> { shp[0] = shp[0] >= SHAPES.length - 1 ? -1 : shp[0] + 1; sh.setText(shapeLabel(shp[0])); });
        lay.addView(sh);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(c == null ? Lang.t(406) : Lang.t(407)).setView(lay)
            .setPositiveButton(Lang.t(23), (x, w) -> {
                int color;
                try { color = Color.parseColor("#" + hex.getText().toString().trim()); } catch (Exception ex) { color = PALETTE[0]; }
                String n = name.getText().toString().trim(), ic = icon.getText().toString().trim();
                if (n.isEmpty()) n = Lang.t(408);
                if (ic.isEmpty()) ic = "•";
                CategoryManager.Category t = c == null ? cm.add(n, ic, color) : c;
                t.name = n; t.icon = ic; t.color = color; t.shape = shp[0];
                cm.save();
                refreshCats();
            }).setNegativeButton(Lang.t(24), null).show();
    }

    private String shapeLabel(int s) { return Lang.t(409) + (s < 0 ? Lang.t(410) : SHAPES[s]); }

    private void colorDialog(final String key) {
        final EditText hex = new EditText(this);
        hex.setHint(Lang.t(411));
        hex.setText(String.format("%06X", st.get(key) & 0xFFFFFF));
        hex.setTextColor(Color.WHITE);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle(Lang.t(412)).setView(hex)
            .setPositiveButton(Lang.t(413), (x, w) -> {
                try { st.set(key, Color.parseColor("#" + hex.getText().toString().trim())); } catch (Exception ignored) { }
            }).setNegativeButton(Lang.t(24), null).show();
    }

    // ---------- helpers de UI ----------
    private int dp(float v) { return (int) (v * d + 0.5f); }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    private void add(View v) { box.addView(v); }

    private void head(String s) {
        TextView t = text(s.toUpperCase(), 12, 0xFF8FA3FF);
        t.setPadding(0, dp(24), 0, dp(6));
        add(t);
    }

    private TextView button(String s) {
        TextView t = text(s, 15, Color.WHITE);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF22223C);
        g.setCornerRadius(dp(12));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        box.addView(t, lp);
        return t;
    }

    private TextView small(String s, final Runnable r) {
        TextView t = text(s, 18, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(44)); t.setMinHeight(dp(44));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void seek(final String label, final String key, final int min, int max, final String unit) {
        final TextView t = text(label + ": " + st.get(key) + unit, 15, Color.WHITE);
        t.setPadding(0, dp(10), 0, 0);
        add(t);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(st.get(key) - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                st.set(key, p + min);
                t.setText(label + ": " + (p + min) + unit);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) {
                if (key.equals(LauncherSettings.RING1) || key.equals(LauncherSettings.RING2)) refreshCats();
            }
        });
        add(sb);
    }
}
