package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pantalla principal: dock de 5 posiciones, botón Home central, cajón por categoría y menú contextual.
 * Reglas del botón central: un toque abre "Apps"; mantener solo muestra el radial (a los 160 ms por defecto);
 * soltar sobre una burbuja abre su categoría y soltar fuera cancela. Mantener más tiempo NO dispara nada más.
 */
public class LauncherEngine {
    private final Activity act;
    private final LauncherSettings st;
    private final CategoryManager cm;
    private final ApplicationManager am;
    private final float d;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final FrameLayout root, drawer;
    private final View scrim;
    private final LinearLayout chips;
    private final FrameLayout dock;
    private final View dockBg;
    private final FrameLayout[] cells = new FrameLayout[5];
    private int cellW, bgL, bgR, dragFromDock = -1;
    private boolean dragging, dockBuilt;
    private ValueAnimator bgAnim;
    private final HomeButton home;
    private final RadialView radial;
    private final TextView title, gear, chipNew, chipAll, empty;
    private final GridView grid;
    private final AppAdapter adapter = new AppAdapter();
    private final GridView desk;
    private final DeskAdapter deskAdapter = new DeskAdapter();
    private int insetTop, insetBottom;
    private String openId;          // null = cajón cerrado, "*" = apps generales, otro = id de categoría
    private boolean showPending;
    private PopupWindow menu;
    private float downX, downY;
    private boolean radialOn, moved;
    private final Runnable showRadial = this::startRadial;
    private final BroadcastReceiver pkgRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Uri u = i.getData();
            String pkg = u == null ? null : u.getSchemeSpecificPart();
            if (Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && pkg != null
                && !i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                cm.forget(pkg);
                st.unpin(pkg);
                for (int k = 0; k < 4; k++) if (pkg.equals(st.getDock(k))) st.setDock(k, null);
            }
            am.refreshAsync(LauncherEngine.this::onAppsChanged);
        }
    };

    public LauncherEngine(Activity a) {
        act = a;
        d = a.getResources().getDisplayMetrics().density;
        st = new LauncherSettings(a);
        cm = new CategoryManager(a);
        am = new ApplicationManager(a);
        root = new FrameLayout(a);

        // ---- escritorio: iconos fijados + apps sin categoría (sueltas o en la carpeta «Nuevas») ----
        desk = new GridView(a);
        desk.setNumColumns(4);
        desk.setVerticalSpacing(dp(10));
        desk.setSelector(new ColorDrawable(0));
        desk.setAdapter(deskAdapter);
        root.addView(desk, new FrameLayout.LayoutParams(-1, -1));

        scrim = new View(a);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> closeDrawer(true));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        // ---- cajón ----
        drawer = new FrameLayout(a);
        drawer.setVisibility(View.GONE);
        drawer.setBackground(round(Color.argb(238, 18, 18, 40), 26, Color.argb(40, 255, 255, 255)));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(12), dp(14), dp(8));
        drawer.addView(col, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        title = tv("", 18, Color.WHITE);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        gear = tv("⚙", 22, Color.WHITE);
        gear.setPadding(dp(12), dp(4), dp(12), dp(4));
        gear.setOnClickListener(v -> act.startActivity(new Intent(act, LauncherSettingsActivity.class)));
        head.addView(gear);
        TextView close = tv("✕", 20, Color.WHITE);
        close.setPadding(dp(12), dp(4), dp(4), dp(4));
        close.setOnClickListener(v -> closeDrawer(true));
        head.addView(close);
        col.addView(head);
        chips = new LinearLayout(a);
        chipNew = chip("Sin categoría");
        chipAll = chip("Todas");
        chipNew.setOnClickListener(v -> { showPending = true; populate(); });
        chipAll.setOnClickListener(v -> { showPending = false; populate(); });
        chips.addView(chipNew);
        chips.addView(chipAll);
        col.addView(chips);
        FrameLayout body = new FrameLayout(a);
        grid = new GridView(a);
        grid.setNumColumns(4);
        grid.setAdapter(adapter);
        grid.setVerticalSpacing(dp(6));
        grid.setSelector(new ColorDrawable(0));
        body.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        empty = tv("", 14, Color.argb(190, 255, 255, 255));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(20), dp(20), dp(20), dp(20));
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, 0, 1);
        bl.topMargin = dp(8);
        col.addView(body, bl);
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -2));

        // ---- dock ----
        // Dock: 5 celdas fijas. El fondo solo cubre desde la primera hasta la última ocupada (el Home siempre cuenta),
        // así que un lado vacío se "achica" y nada se mueve.
        cellW = (int) ((a.getResources().getDisplayMetrics().widthPixels - dp(24)) * st.get(LauncherSettings.DOCK_W) / 100f) / 5;
        dock = new FrameLayout(a);
        dockBg = new View(a);
        dockBg.setBackground(round(Color.argb(120, 12, 12, 30), 30, Color.argb(36, 255, 255, 255)));
        dock.addView(dockBg, new FrameLayout.LayoutParams(cellW, -1, Gravity.START));
        for (int i = 0; i < 5; i++) {
            final int ci = i;
            cells[i] = new FrameLayout(a);
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(cellW, -1, Gravity.START);
            cl.leftMargin = i * cellW;
            dock.addView(cells[i], cl);
            if (i != 2) cells[i].setOnDragListener((v, ev) -> onDockDrag(ci, v, ev));
        }
        root.addView(dock, new FrameLayout.LayoutParams(cellW * 5, dp(st.get(LauncherSettings.DOCK_H)), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        home = new HomeButton(a);
        home.setOnTouchListener((v, e) -> onHomeTouch(e));

        radial = new RadialView(a, st);
        root.addView(radial, new FrameLayout.LayoutParams(-1, -1));

        root.setOnApplyWindowInsetsListener((v, in) -> {
            insetTop = in.getSystemWindowInsetTop();
            insetBottom = in.getSystemWindowInsetBottom();
            layoutChrome();
            return in;
        });

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) a.registerReceiver(pkgRx, f, Context.RECEIVER_EXPORTED);
        else a.registerReceiver(pkgRx, f);
        buildDock();
        applyBackground();
    }

    public View view() { return root; }

    // ---------- ciclo de vida ----------
    public void onResume() {
        applyDockSize();
        applyBackground();
        buildDock();
        layoutChrome();
        root.requestApplyInsets();
        if (am.all().isEmpty()) am.refreshAsync(this::onAppsChanged);
        refreshDesk();
        if (openId != null) populate();
    }

    public void onHome() {
        closeMenu();
        if (radialOn) { radial.hide(); radialOn = false; home.setActive(false); }
        if (openId != null) closeDrawer(true);
    }

    public boolean onBack() {
        if (menu != null && menu.isShowing()) { closeMenu(); return true; }
        if (openId != null) { closeDrawer(true); return true; }
        return false;
    }

    public void onDestroy() { try { act.unregisterReceiver(pkgRx); } catch (Throwable ignored) { } }

    private void onAppsChanged() {
        if (!am.all().isEmpty())
            for (int k = 0; k < 4; k++) { String p = st.getDock(k); if (p != null && am.find(p) == null) st.setDock(k, null); }
        if (!am.all().isEmpty()) for (String p : st.pinned()) if (am.find(p) == null) st.unpin(p);
        buildDock();
        refreshDesk();
        if (openId != null) populate();
    }

    private void applyBackground() {
        int dim = st.get(LauncherSettings.DIM);
        root.setBackgroundColor(st.get(LauncherSettings.BG) == 1 ? st.get(LauncherSettings.BG_COLOR)
            : Color.argb(dim * 255 / 100, 0, 0, 0));
        scrim.setBackgroundColor(Color.argb(90, 0, 0, 10));
    }

    private void layoutChrome() {
        FrameLayout.LayoutParams dp1 = (FrameLayout.LayoutParams) dock.getLayoutParams();
        dp1.width = cellW * 5;
        dp1.height = dp(st.get(LauncherSettings.DOCK_H));
        dp1.setMargins(0, 0, 0, insetBottom + dp(8));
        dock.setLayoutParams(dp1);
        FrameLayout.LayoutParams dk = (FrameLayout.LayoutParams) desk.getLayoutParams();
        dk.setMargins(dp(8), insetTop + dp(8), dp(8), insetBottom + dp(8 + st.get(LauncherSettings.DOCK_H) + 10));
        desk.setLayoutParams(dk);
        int rh = root.getHeight() > 0 ? root.getHeight() : act.getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) drawer.getLayoutParams();
        lp.height = (int) (rh * 0.58f);
        lp.gravity = Gravity.BOTTOM;
        lp.setMargins(dp(12), insetTop + dp(8), dp(12), insetBottom + dp(8 + st.get(LauncherSettings.DOCK_H) + 10));
        drawer.setLayoutParams(lp);
    }

    // ---------- dock ----------
    /** Recalcula ancho y alto de la barra según los ajustes; las celdas mantienen su posición relativa al centro. */
    private void applyDockSize() {
        cellW = (int) ((act.getResources().getDisplayMetrics().widthPixels - dp(24)) * st.get(LauncherSettings.DOCK_W) / 100f) / 5;
        for (int i = 0; i < 5; i++) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cells[i].getLayoutParams();
            lp.width = cellW;
            lp.leftMargin = i * cellW;
            cells[i].setLayoutParams(lp);
        }
        updateDockBg(false);
        layoutChrome();
    }

    private void buildDock() {
        if (home.getParent() instanceof ViewGroup) ((ViewGroup) home.getParent()).removeView(home);
        for (int c = 0; c < 5; c++) {
            cells[c].removeAllViews();
            if (c == 2) {
                int hs = Math.min(dp(56), Math.min(dp(st.get(LauncherSettings.DOCK_H)) - dp(6), cellW));
                cells[c].addView(home, new FrameLayout.LayoutParams(hs, hs, Gravity.CENTER));
            }
            else slot(cells[c], c < 2 ? c : c - 1);
        }
        updateDockBg(dockBuilt);
        dockBuilt = true;
    }

    private void slot(FrameLayout cell, final int idx) {
        String pkg = st.getDock(idx);
        int sz = Math.min(dp(st.get(LauncherSettings.DOCK_ICON)), Math.min(dp(st.get(LauncherSettings.DOCK_H)) - dp(12), cellW - dp(4)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sz, sz, Gravity.CENTER);
        if (pkg == null) { // vacío: sin icono; el globito "+" solo aparece mientras se arrastra una app
            TextView plus = tv("+", 22, Color.argb(190, 255, 255, 255));
            plus.setTag("plus");
            plus.setGravity(Gravity.CENTER);
            plus.setBackground(round(Color.argb(40, 255, 255, 255), 40, Color.argb(90, 255, 255, 255)));
            plus.setAlpha(dragging ? 1f : 0f);
            cell.addView(plus, lp);
            return;
        }
        ApplicationManager.App a = am.find(pkg);
        if (a == null) return; // la lista de apps aún no cargó: el hueco sigue ocupado
        ImageView iv = new ImageView(act);
        iv.setImageDrawable(a.icon);
        gestures(iv, a, null, idx);
        cell.addView(iv, lp);
    }

    private void updateDockBg(boolean animate) {
        int lo = 2, hi = 2;
        for (int c = 0; c < 5; c++) {
            if (c == 2) continue;
            if (dragging || st.getDock(c < 2 ? c : c - 1) != null) { lo = Math.min(lo, c); hi = Math.max(hi, c); }
        }
        final int tl = lo * cellW, tr = (hi + 1) * cellW, fl = bgL, fr = bgR;
        if (bgAnim != null) bgAnim.cancel();
        if (!animate) { setBg(tl, tr); return; }
        bgAnim = ValueAnimator.ofFloat(0f, 1f);
        bgAnim.setDuration(180);
        bgAnim.setInterpolator(new DecelerateInterpolator());
        bgAnim.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            setBg((int) (fl + (tl - fl) * t), (int) (fr + (tr - fr) * t));
        });
        bgAnim.start();
    }

    private void setBg(int l, int r) {
        bgL = l; bgR = r;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dockBg.getLayoutParams();
        lp.leftMargin = l;
        lp.width = r - l;
        dockBg.setLayoutParams(lp);
    }

    private void setDragging(boolean on) {
        dragging = on;
        if (!on) dragFromDock = -1;
        for (int c = 0; c < 5; c++) {
            if (c == 2) continue;
            View ch = cells[c].getChildAt(0);
            if (ch != null && "plus".equals(ch.getTag())) ch.animate().alpha(on ? 1f : 0f).setDuration(150).start();
        }
        updateDockBg(true);
    }

    // ---------- arrastrar apps al dock ----------
    /** Toque = abrir; mantener = menú; si sigues sosteniendo y mueves el dedo, el menú se cierra y empieza el arrastre. */
    private void gestures(final View v, final ApplicationManager.App a, final String catId, final int dockIdx) {
        final int slop = ViewConfiguration.get(act).getScaledTouchSlop();
        final float[] down = new float[2];
        final boolean[] held = {false};
        v.setOnClickListener(x -> launch(a.pkg));
        v.setOnLongClickListener(x -> {
            held[0] = true;
            if (x.getParent() != null) x.getParent().requestDisallowInterceptTouchEvent(true); // que la lista no robe el gesto
            appMenu(a, x, catId, dockIdx);
            return true;
        });
        v.setOnTouchListener((x, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX(); down[1] = e.getRawY(); held[0] = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (held[0] && Math.hypot(e.getRawX() - down[0], e.getRawY() - down[1]) > slop * 2) {
                        held[0] = false;
                        startDrag(x, a, dockIdx);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    held[0] = false;
                    break;
            }
            return false;
        });
    }

    private void startDrag(View v, ApplicationManager.App a, int dockIdx) {
        closeMenu();
        dragFromDock = dockIdx;
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        v.startDragAndDrop(ClipData.newPlainText("zc-app", a.pkg), new View.DragShadowBuilder(v), null, 0);
    }

    private boolean onDockDrag(int ci, View c, DragEvent ev) {
        switch (ev.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                if (ev.getClipDescription() == null
                    || !ev.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return false;
                setDragging(true);
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                c.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start();
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                c.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                return true;
            case DragEvent.ACTION_DROP:
                c.setScaleX(1f); c.setScaleY(1f);
                if (ev.getClipData() != null && ev.getClipData().getItemCount() > 0)
                    dropOnDock(ci < 2 ? ci : ci - 1, String.valueOf(ev.getClipData().getItemAt(0).getText()));
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                c.setScaleX(1f); c.setScaleY(1f);
                setDragging(false);
                return true;
        }
        return false;
    }

    /** Reemplaza lo que hubiera en esa posición. Si la app ya estaba en otra, se mueve (desde el dock, se intercambian). */
    private void dropOnDock(int idx, String pkg) {
        String old = st.getDock(idx);
        for (int k = 0; k < 4; k++)
            if (k != idx && pkg.equals(st.getDock(k))) st.setDock(k, k == dragFromDock ? old : null);
        st.setDock(idx, pkg);
        buildDock();
    }

    // ---------- gesto del botón central ----------
    private boolean onHomeTouch(MotionEvent e) {
        int slop = ViewConfiguration.get(act).getScaledTouchSlop();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX(); downY = e.getRawY(); moved = false; radialOn = false;
                home.setActive(true);
                h.postDelayed(showRadial, st.get(LauncherSettings.HOLD));
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!radialOn && Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > slop * 2) {
                    moved = true;
                    h.removeCallbacks(showRadial);
                    startRadial();
                }
                if (radialOn) feed(e);
                return true;
            case MotionEvent.ACTION_UP:
                h.removeCallbacks(showRadial);
                home.setActive(false);
                if (radialOn) {
                    int idx = radial.hide();
                    radialOn = false;
                    if (idx >= 0 && idx < cm.list().size()) openDrawer(cm.list().get(idx).id);
                } else if (!moved) {
                    if (openId != null) closeDrawer(true); else openDrawer("*");
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(showRadial);
                home.setActive(false);
                if (radialOn) { radial.hide(); radialOn = false; }
                return true;
        }
        return false;
    }

    private void feed(MotionEvent e) {
        int[] r = new int[2];
        root.getLocationOnScreen(r);
        radial.move(e.getRawX() - r[0], e.getRawY() - r[1]);
    }

    private void startRadial() {
        if (radialOn || cm.list().isEmpty()) return;
        radialOn = true;
        closeMenu();
        if (home.getParent() != null) home.getParent().requestDisallowInterceptTouchEvent(true);
        int[] r = new int[2], hh = new int[2];
        root.getLocationOnScreen(r);
        home.getLocationOnScreen(hh);
        radial.show(hh[0] - r[0] + home.getWidth() / 2f, hh[1] - r[1] + home.getHeight() / 2f - dp(7), cm.list(), insetTop); // 7 dp más arriba
        home.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    // ---------- cajón ----------
    private void openDrawer(String id) {
        boolean was = openId != null;
        openId = id;
        populate();
        layoutChrome();
        long dur = st.get(LauncherSettings.ANIM);
        if (!was) {
            scrim.setVisibility(View.VISIBLE);
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(dur).start();
            drawer.setAlpha(0f); drawer.setScaleX(0.6f); drawer.setScaleY(0.6f); drawer.setTranslationY(dp(30));
            drawer.setVisibility(View.VISIBLE);
            drawer.post(() -> {
                drawer.setPivotX(drawer.getWidth() / 2f);
                drawer.setPivotY(drawer.getHeight());
                drawer.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(dur)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).withEndAction(null).start();
            });
        } else { // cambio de categoría con el cajón abierto
            grid.setAlpha(0f);
            grid.setTranslationY(dp(16));
            grid.animate().alpha(1f).translationY(0f).setDuration(dur).start();
        }
    }

    private void closeDrawer(boolean animate) {
        openId = null;
        closeMenu();
        scrim.animate().cancel();
        if (!animate) {
            drawer.animate().cancel();
            drawer.setVisibility(View.GONE);
            scrim.setVisibility(View.GONE);
            return;
        }
        scrim.animate().alpha(0f).setDuration(st.get(LauncherSettings.ANIM)).start();
        drawer.setPivotX(drawer.getWidth() / 2f);
        drawer.setPivotY(drawer.getHeight());
        drawer.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).translationY(dp(30))
            .setDuration(st.get(LauncherSettings.ANIM) * 3 / 4).withEndAction(() -> {
                if (openId == null) { drawer.setVisibility(View.GONE); scrim.setVisibility(View.GONE); }
            }).start();
    }

    private void populate() {
        refreshDesk();
        if (openId == null) return;
        List<ApplicationManager.App> items;
        gear.setVisibility(View.GONE);
        if ("*".equals(openId)) {
            title.setText("Todas las apps");
            items = am.all();
            empty.setText("Cargando apps…");
            gear.setVisibility(View.VISIBLE);
        } else if ("new".equals(openId)) {
            title.setText("Nuevas");
            items = am.pending(cm);
            empty.setText("No quedan apps sin categoría.");
        } else {
            CategoryManager.Category c = cm.byId(openId);
            title.setText(c == null ? "" : c.icon + "  " + c.name);
            items = am.inCategory(cm, openId);
            empty.setText("Esta categoría está vacía.\nMantén pulsada una app y elige «Enviar a…».");
        }
        chips.setVisibility(View.GONE);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.items = items;
        adapter.notifyDataSetChanged();
        grid.smoothScrollToPosition(0);
    }

    /** Escritorio: primero lo fijado; después las apps sin categoría, sueltas o dentro de una carpeta «Nuevas». */
    private void refreshDesk() {
        List<Object> items = new ArrayList<>();
        for (String pkg : st.pinned()) { ApplicationManager.App a = am.find(pkg); if (a != null) items.add(a); }
        List<ApplicationManager.App> pend = am.pending(cm);
        if (st.get(LauncherSettings.NEW_MODE) == 1) {
            if (!pend.isEmpty()) items.add("folder");
        } else {
            for (ApplicationManager.App a : pend) if (!st.isPinned(a.pkg)) items.add(a);
        }
        deskAdapter.items = items;
        deskAdapter.notifyDataSetChanged();
    }

    private void launch(String pkg) {
        if (am.launch(pkg)) closeDrawer(false);
        else Toast.makeText(act, "No se pudo abrir la app", Toast.LENGTH_SHORT).show();
    }

    private class AppAdapter extends BaseAdapter {
        List<ApplicationManager.App> items = new ArrayList<>();
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout cell;
            if (cv == null) {
                cell = new LinearLayout(act);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(2), dp(6), dp(2), dp(6));
                int sz = dp(st.get(LauncherSettings.APP_ICON));
                cell.addView(new ImageView(act), new LinearLayout.LayoutParams(sz, sz));
                TextView t = tv("", 11, Color.WHITE);
                t.setSingleLine();
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER);
                cell.addView(t, new LinearLayout.LayoutParams(-1, -2));
            } else cell = (LinearLayout) cv;
            final ApplicationManager.App a = items.get(pos);
            ((ImageView) cell.getChildAt(0)).setImageDrawable(a.icon);
            ((TextView) cell.getChildAt(1)).setText((cm.hasAny(a.pkg) ? "" : "• ") + a.label);
            gestures(cell, a, openId, -1);
            return cell;
        }
    }

    private class DeskAdapter extends BaseAdapter {
        List<Object> items = new ArrayList<>();
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout cell;
            if (cv == null) {
                cell = new LinearLayout(act);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(2), dp(6), dp(2), dp(6));
                int sz = dp(st.get(LauncherSettings.APP_ICON));
                cell.addView(new ImageView(act), new LinearLayout.LayoutParams(sz, sz));
                TextView t = tv("", 11, Color.WHITE);
                t.setSingleLine();
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER);
                t.setShadowLayer(4f, 0f, 1f, 0xAA000000); // legible sobre cualquier fondo
                cell.addView(t, new LinearLayout.LayoutParams(-1, -2));
            } else cell = (LinearLayout) cv;
            Object o = items.get(pos);
            ImageView iv = (ImageView) cell.getChildAt(0);
            TextView t = (TextView) cell.getChildAt(1);
            if (o instanceof ApplicationManager.App) {
                ApplicationManager.App a = (ApplicationManager.App) o;
                iv.setImageDrawable(a.icon);
                t.setText(a.label);
                gestures(cell, a, null, -1);
            } else {
                iv.setImageDrawable(new FolderDrawable(am.pending(cm)));
                t.setText("Nuevas");
                cell.setOnTouchListener(null);
                cell.setOnLongClickListener(null);
                cell.setOnClickListener(v -> openDrawer("new"));
            }
            return cell;
        }
    }

    /** Icono de la carpeta «Nuevas»: fondo translúcido con hasta 4 iconos de las apps que contiene. */
    private static class FolderDrawable extends Drawable {
        private final List<ApplicationManager.App> apps;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        FolderDrawable(List<ApplicationManager.App> a) { apps = a; p.setColor(Color.argb(110, 255, 255, 255)); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float r = b.width() * 0.22f;
            c.drawRoundRect(b.left, b.top, b.right, b.bottom, r, r, p);
            int gap = (int) (b.width() * 0.07f), cs = (b.width() - gap * 3) / 2;
            for (int i = 0; i < 4 && i < apps.size(); i++) {
                Drawable ic = apps.get(i).icon;
                Rect old = ic.copyBounds(); // el icono se comparte con otras vistas: se restaura al terminar
                int l = b.left + gap + (i % 2) * (cs + gap), t = b.top + gap + (i / 2) * (cs + gap);
                ic.setBounds(l, t, l + cs, t + cs);
                ic.draw(c);
                ic.setBounds(old);
            }
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---------- menú contextual ----------
    private void appMenu(final ApplicationManager.App a, View anchor, final String catId, final int dockIdx) {
        closeMenu();
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(Color.argb(250, 28, 28, 52), 16, Color.argb(50, 255, 255, 255)));
        box.setPadding(0, dp(6), 0, dp(6));
        TextView hd = tv(a.label, 16, Color.WHITE);
        hd.setPadding(dp(16), dp(8), dp(16), dp(8));
        box.addView(hd);
        item(box, "Abrir", () -> launch(a.pkg));
        item(box, "Enviar a…", () -> sendTo(a));
        if (st.isPinned(a.pkg)) item(box, "Quitar del inicio", () -> { st.unpin(a.pkg); refreshDesk(); });
        else item(box, "Fijar al inicio", () -> { st.pin(a.pkg); refreshDesk(); });
        item(box, "Añadir a la pantalla principal", () -> addToDock(a));
        if (catId != null && !"*".equals(catId)) item(box, "Quitar de esta categoría", () -> { cm.removeApp(a.pkg, catId); populate(); });
        if (dockIdx >= 0) item(box, "Quitar de la pantalla principal", () -> { st.setDock(dockIdx, null); buildDock(); });
        item(box, "Información de la app", () -> am.info(a.pkg));
        item(box, "Desinstalar", () -> am.uninstall(a.pkg));
        menu = new PopupWindow(box, dp(250), -2, true);
        menu.setBackgroundDrawable(new ColorDrawable(0));
        menu.setOutsideTouchable(true);
        menu.setElevation(dp(10));
        menu.showAsDropDown(anchor);
    }

    private void item(LinearLayout box, String label, final Runnable r) {
        TextView t = tv(label, 15, Color.WHITE);
        t.setPadding(dp(16), dp(11), dp(16), dp(11));
        t.setOnClickListener(v -> { closeMenu(); r.run(); });
        box.addView(t);
    }

    private void closeMenu() { if (menu != null) { menu.dismiss(); menu = null; } }

    private void sendTo(final ApplicationManager.App a) {
        final List<CategoryManager.Category> l = cm.list();
        if (l.isEmpty()) { Toast.makeText(act, "Crea categorías en los ajustes", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[l.size()];
        final boolean[] chk = new boolean[l.size()];
        Set<String> cur = cm.of(a.pkg);
        for (int i = 0; i < names.length; i++) { names[i] = l.get(i).icon + "  " + l.get(i).name; chk[i] = cur.contains(l.get(i).id); }
        dlg().setTitle("Enviar a…").setMultiChoiceItems(names, chk, (x, i, on) -> chk[i] = on)
            .setPositiveButton("Aceptar", (x, w) -> {
                Set<String> ids = new LinkedHashSet<>();
                for (int i = 0; i < chk.length; i++) if (chk[i]) ids.add(l.get(i).id);
                cm.setApp(a.pkg, ids);
                populate();
            }).setNegativeButton("Cancelar", null).show();
    }

    private void addToDock(final ApplicationManager.App a) {
        String[] n = {"Izquierda 1", "Izquierda 2", "Derecha 1", "Derecha 2"};
        for (int i = 0; i < 4; i++) {
            ApplicationManager.App o = am.find(st.getDock(i));
            if (o != null) n[i] += "  (" + o.label + ")";
        }
        dlg().setTitle("Posición en el dock").setItems(n, (x, w) -> { st.setDock(w, a.pkg); buildDock(); }).show();
    }

    // ---------- utilidades ----------
    private AlertDialog.Builder dlg() { return new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert); }
    private int dp(float v) { return (int) (v * d + 0.5f); }

    private TextView tv(String s, float sp, int color) {
        TextView t = new TextView(act);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    private TextView chip(String s) {
        TextView t = tv(s, 13, Color.WHITE);
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(8); lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void setChip(TextView t, boolean on) {
        t.setBackground(round(on ? st.get(LauncherSettings.ACCENT) : Color.argb(40, 255, 255, 255), 20, 0));
    }

    private GradientDrawable round(int color, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * d);
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    /** Botón Home propio: anillo con punto central que se ilumina mientras está pulsado. */
    private class HomeButton extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;
        HomeButton(Context c) { super(c); }
        void setActive(boolean a) {
            active = a;
            animate().scaleX(a ? 1.12f : 1f).scaleY(a ? 1.12f : 1f).setDuration(120).start();
            invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = getWidth() * 0.4f;
            int acc = st.get(LauncherSettings.ACCENT);
            if (active) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(40, Color.red(acc), Color.green(acc), Color.blue(acc)));
                c.drawCircle(cx, cy, r * 1.35f, p);
                p.setColor(Color.argb(70, Color.red(acc), Color.green(acc), Color.blue(acc)));
                c.drawCircle(cx, cy, r * 1.15f, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.6f * d);
            p.setColor(active ? Color.argb(255, 170, 200, 255) : Color.WHITE);
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, r * 0.28f, p);
        }
    }
}
