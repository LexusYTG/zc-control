package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class GamesActivity extends Activity {
    private static final class Item {
        String pkg;
        String label;
        Drawable icon;
        boolean extra;
    }

    private LinearLayout list;
    private TextView badge;
    private Switch sw;
    private boolean binding = false;

    private int dp(float v) {
        return GcUi.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = GcUi.col(this);
        root.setBackgroundColor(GcUi.BG);
        root.setPadding(dp(16), dp(20), dp(16), dp(24));

        LinearLayout head = GcUi.row(this);
        head.addView(GcUi.tv(this, "GAMES", 18, GcUi.TEXT, true), new LinearLayout.LayoutParams(0, GcUi.WRAP, 1f));
        head.addView(GcUi.badge(this, "ZCtrl", GcUi.GREEN));
        root.addView(head);

        LinearLayout card = GcUi.col(this);
        card.setBackground(GcUi.round(this, GcUi.CARD, 18));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout box = GcUi.col(this);
        box.setBackground(GcUi.round(this, GcUi.BOX, 14));
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout t1 = GcUi.row(this);
        t1.addView(GcUi.tv(this, "ZC Game Control", 18, GcUi.TEXT, true), new LinearLayout.LayoutParams(0, GcUi.WRAP, 1f));
        badge = GcUi.badge(this, "Desactivado", GcUi.GRAY);
        t1.addView(badge);
        box.addView(t1);
        box.addView(GcUi.tv(this, "Optimización automática antes de iniciar tus juegos", 13, GcUi.SUB, false), GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 6));
        LinearLayout t2 = GcUi.row(this);
        t2.addView(GcUi.tv(this, "Activar Game Control", 14, GcUi.SUB, false), new LinearLayout.LayoutParams(0, GcUi.WRAP, 1f));
        sw = new Switch(this);
        sw.setOnCheckedChangeListener((v, on) -> {
            if (binding) return;
            GameSettings.setEnabled(this, on);
            refreshBadge();
            if (on && !Privileged.available()) ShizukuNotice.show(this);
        });
        t2.addView(sw);
        box.addView(t2, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 10));
        card.addView(box);

        card.addView(GcUi.tv(this, "Tus juegos", 18, GcUi.TEXT, true), GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 18));
        list = GcUi.col(this);
        card.addView(list);

        TextView add = GcUi.tv(this, "+  Agregar juego", 15, GcUi.GREEN, true);
        add.setPadding(0, dp(14), 0, dp(14));
        add.setOnClickListener(v -> pickExtra());
        card.addView(add);
        card.addView(GcUi.line(this));

        LinearLayout cfg = GcUi.row(this);
        cfg.setPadding(0, dp(14), 0, dp(4));
        cfg.addView(GcUi.tv(this, "Configuración general", 15, GcUi.SUB, false), new LinearLayout.LayoutParams(0, GcUi.WRAP, 1f));
        cfg.addView(GcUi.tv(this, "⚙", 18, GcUi.SUB, false));
        cfg.setOnClickListener(v -> startActivity(new Intent(this, GameProfileActivity.class)));
        card.addView(cfg);

        root.addView(card, GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 16));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(GcUi.BG);
        sv.addView(root);
        Insets.apply(sv);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding = true;
        sw.setChecked(GameSettings.enabled(this));
        binding = false;
        refreshBadge();
        reload();
        GameWatchdog.recoverAsync(this);
    }

    private void refreshBadge() {
        boolean on = GameSettings.enabled(this);
        GcUi.paint(badge, on ? "Activado" : "Desactivado", on ? GcUi.GREEN : GcUi.GRAY);
    }

    private void play(String pkg) {
        if (GameSettings.enabled(this)) {
            startActivity(new Intent(this, GamePrepActivity.class).putExtra("pkg", pkg));
            return;
        }
        Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
        if (li != null) startActivity(li);
    }

    private void reload() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Set<String> extra = GameSettings.extras(this);
            final List<Item> items = new ArrayList<Item>();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai.packageName.equals(getPackageName())) continue;
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                boolean game = ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
                boolean ex = extra.contains(ai.packageName);
                if (!game && !ex) continue;
                Item it = new Item();
                it.pkg = ai.packageName;
                it.label = pm.getApplicationLabel(ai).toString();
                it.icon = pm.getApplicationIcon(ai);
                it.extra = ex;
                items.add(it);
            }
            Collections.sort(items, (x, y) -> x.label.compareToIgnoreCase(y.label));
            runOnUiThread(() -> render(items));
        }).start();
    }

    private void render(List<Item> items) {
        list.removeAllViews();
        if (items.isEmpty()) {
            list.addView(GcUi.tv(this, "No se detectaron juegos. Tocá «Agregar juego».", 13, GcUi.SUB, false), GcUi.lp(this, GcUi.MATCH, GcUi.WRAP, 0f, 10));
        }
        for (final Item it : items) {
            LinearLayout r = GcUi.row(this);
            r.setPadding(0, dp(12), 0, dp(12));
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(it.icon);
            r.addView(iv, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout tx = GcUi.col(this);
            tx.setPadding(dp(14), 0, dp(8), 0);
            tx.addView(GcUi.tv(this, it.label, 17, GcUi.TEXT, true));
            String sub = GameSettings.hasProfile(this, it.pkg)
                ? "Perfil personalizado · " + GameSettings.profile(this, it.pkg).pct + " %"
                : "Configuración predeterminada";
            tx.addView(GcUi.tv(this, sub, 12, GcUi.SUB, false));
            r.addView(tx, new LinearLayout.LayoutParams(0, GcUi.WRAP, 1f));
            TextView ch = GcUi.tv(this, "›", 28, GcUi.SUB, false);
            ch.setPadding(dp(14), dp(4), dp(6), dp(4));
            ch.setOnClickListener(v -> startActivity(new Intent(this, GameProfileActivity.class).putExtra("pkg", it.pkg)));
            r.addView(ch);
            r.setOnClickListener(v -> play(it.pkg));
            if (it.extra) {
                r.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                        .setTitle("Quitar de la lista")
                        .setMessage(it.label)
                        .setPositiveButton("Quitar", (d, w) -> {
                            Set<String> s = GameSettings.extras(this);
                            s.remove(it.pkg);
                            GameSettings.setExtras(this, s);
                            reload();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                    return true;
                });
            }
            list.addView(r);
            list.addView(GcUi.line(this));
        }
    }

    private void pickExtra() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Set<String> extra = GameSettings.extras(this);
            final List<Item> all = new ArrayList<Item>();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai.packageName.equals(getPackageName())) continue;
                if (extra.contains(ai.packageName)) continue;
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                Item it = new Item();
                it.pkg = ai.packageName;
                it.label = pm.getApplicationLabel(ai).toString();
                all.add(it);
            }
            Collections.sort(all, (x, y) -> x.label.compareToIgnoreCase(y.label));
            final String[] names = new String[all.size()];
            for (int i = 0; i < names.length; i++) names[i] = all.get(i).label;
            runOnUiThread(() -> new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Agregar juego")
                .setItems(names, (d, which) -> {
                    Set<String> s = GameSettings.extras(this);
                    s.add(all.get(which).pkg);
                    GameSettings.setExtras(this, s);
                    reload();
                })
                .setNegativeButton("Cancelar", null)
                .show());
        }).start();
    }
}
