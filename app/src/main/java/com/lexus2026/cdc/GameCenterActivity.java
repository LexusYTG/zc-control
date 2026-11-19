package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** ZC Game Center: interruptor del modo juego, perfil por defecto con sus opciones y lista de juegos. */
public class GameCenterActivity extends Activity {
    private static final class Item {
        String pkg, label;
        Drawable icon;
        boolean extra;
    }

    private ZKit.Page page;
    private List<Item> games = new ArrayList<>();
    private boolean loaded;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Consent.granted(this)) { Consent.require(this, false); return; }
        page = new ZKit.Page(this, Lang.t(S.MOD_GAME), Lang.t(S.MOD_GAME_D), true);
        page.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page == null) return;
        render();
        loadGames();
        GameWatchdog.recoverAsync(this);
    }

    private void loadGames() {
        final Activity a = this;
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Set<String> extra = GameSettings.extras(a);
            List<Item> out = new ArrayList<>();
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
                out.add(it);
            }
            Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
            runOnUiThread(() -> { games = out; loaded = true; render(); });
        }, "zc-games-scan").start();
    }

    private void render() {
        final Activity a = this;
        page.clear();
        final boolean on = Features.game(a);

        page.section(Lang.t(S.F_STATUS));
        LinearLayout st = page.card();
        page.toggle(st, Lang.t(S.GAME_EN), Lang.t(S.GAME_EN_D), on, v -> {
            Features.setGame(a, v);
            if (v && !Privileged.available()) ShizukuNotice.show(a);
            render();
        });
        page.divider(st);
        page.status(st, Lang.t(S.PM_SHZ) + ": " + (Privileged.available() ? Lang.t(S.ST_ON) : Lang.t(S.ST_OFF)), Privileged.available());
        if (on && !Privileged.available()) page.note(st, Lang.t(S.F_NEEDS_SHZ), ZKit.WARN);

        if (!on) {
            page.guide(Lang.t(S.F_GUIDE), Lang.t(S.GAME_GUIDE));
            return;
        }

        // ---- perfil por defecto (se guarda al instante)
        page.section(Lang.t(S.GAME_DEF_T));
        final GameSettings.Profile p = GameSettings.defaults(a);
        LinearLayout pf = page.card();
        page.note(pf, Lang.t(S.GAME_DEF_D), ZKit.SUB);
        page.toggle(pf, Lang.t(S.GAME_PROCS), null, p.procs, v -> { p.procs = v; GameSettings.saveDefaults(a, p); });
        if (p.procs) {
            page.slider(pf, Lang.t(S.GAME_PCT), 50, 100, " %",
                () -> p.pct, v -> { p.pct = (v / 5) * 5; GameSettings.saveDefaults(a, p); });
        }
        page.divider(pf);
        page.toggle(pf, Lang.t(S.GAME_COMPILE), null, p.compile, v -> { p.compile = v; GameSettings.saveDefaults(a, p); });
        page.divider(pf);
        page.toggle(pf, Lang.t(S.GAME_PANEL), null, p.panel, v -> { p.panel = v; GameSettings.saveDefaults(a, p); });
        page.divider(pf);
        page.toggle(pf, Lang.t(S.GAME_SAVER), null, p.saver, v -> { p.saver = v; GameSettings.saveDefaults(a, p); });
        page.divider(pf);
        page.toggle(pf, Lang.t(S.GAME_NOKEEP), null, p.noKeep, v -> { p.noKeep = v; GameSettings.saveDefaults(a, p); });
        page.divider(pf);
        page.toggle(pf, Lang.t(S.GAME_BGLIM), null, p.bgLimit, v -> { p.bgLimit = v; GameSettings.saveDefaults(a, p); });
        page.divider(pf);
        page.nav(pf, Lang.t(S.GAME_ADV_ALL), null, () -> startActivity(new Intent(a, GameProfileActivity.class)));

        // ---- lista de juegos
        page.section(Lang.t(S.GAME_LIST_T));
        LinearLayout list = page.card();
        if (!loaded) {
            page.note(list, "…", ZKit.SUB);
        } else if (games.isEmpty()) {
            page.note(list, Lang.t(S.GAME_EMPTY), ZKit.SUB);
        } else {
            for (int i = 0; i < games.size(); i++) {
                if (i > 0) page.divider(list);
                list.addView(gameRow(games.get(i)));
            }
        }
        page.divider(list);
        page.nav(list, "＋  " + Lang.t(S.GAME_ADD), null, this::pickExtra);

        page.guide(Lang.t(S.F_GUIDE), Lang.t(S.GAME_GUIDE));
    }

    private LinearLayout gameRow(final Item it) {
        final Activity a = this;
        LinearLayout r = ZUi.box(a, LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, ZKit.dp(a, 10), 0, ZKit.dp(a, 10));
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(it.icon);
        r.addView(iv, new LinearLayout.LayoutParams(ZKit.dp(a, 44), ZKit.dp(a, 44)));
        LinearLayout col = ZUi.box(a, LinearLayout.VERTICAL);
        col.setPadding(ZKit.dp(a, 14), 0, ZKit.dp(a, 8), 0);
        col.addView(ZUi.text(a, it.label, 15f, ZKit.TXT, true));
        boolean own = GameSettings.hasProfile(a, it.pkg);
        col.addView(ZUi.text(a, own ? Lang.t(S.GAME_OWN) + " · " + GameSettings.profile(a, it.pkg).pct + " %" : Lang.t(S.GAME_DEFAULT), 12f, ZKit.SUB, false));
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = ZUi.text(a, "›", 26f, ZKit.SUB, false);
        arrow.setPadding(ZKit.dp(a, 14), ZKit.dp(a, 4), ZKit.dp(a, 4), ZKit.dp(a, 4));
        arrow.setOnClickListener(v -> startActivity(new Intent(a, GameProfileActivity.class).putExtra("pkg", it.pkg)));
        r.addView(arrow);
        r.setOnClickListener(v -> play(it.pkg));
        if (it.extra) {
            r.setOnLongClickListener(v -> {
                new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle(Lang.t(S.GAME_REMOVE_Q))
                    .setMessage(it.label)
                    .setPositiveButton(Lang.t(S.REMOVE), (d, w) -> {
                        Set<String> s = GameSettings.extras(a);
                        s.remove(it.pkg);
                        GameSettings.setExtras(a, s);
                        loadGames();
                    })
                    .setNegativeButton(Lang.t(S.CANCEL), null)
                    .show();
                return true;
            });
        }
        return r;
    }

    private void play(String pkg) {
        if (GameSettings.enabled(this)) {
            startActivity(new Intent(this, GamePrepActivity.class).putExtra("pkg", pkg));
            return;
        }
        Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
        if (li != null) startActivity(li);
    }

    private void pickExtra() {
        final Activity a = this;
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Set<String> extra = GameSettings.extras(a);
            final List<Item> all = new ArrayList<>();
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
            runOnUiThread(() -> new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(Lang.t(S.GAME_PICK))
                .setItems(names, (d, which) -> {
                    Set<String> s = GameSettings.extras(a);
                    s.add(all.get(which).pkg);
                    GameSettings.setExtras(a, s);
                    loadGames();
                })
                .setNegativeButton(Lang.t(S.CANCEL), null)
                .show());
        }, "zc-games-pick").start();
    }
}
