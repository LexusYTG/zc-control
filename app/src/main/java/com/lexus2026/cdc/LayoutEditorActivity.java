package com.lexus2026.cdc;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class LayoutEditorActivity extends Activity {
    private static final int MPB = -1;
    private static final int WCB = -2;
    private static final int REQ_IMG = 51;
    private static final int REQ_VID = 52;
    private static final int REQ_BIND = 53;
    private static final int REQ_CFG = 54;
    private static final int[] COLORS = {0, 0xFFFFFFFF, 0xFF000000, 0xFFFF5252, 0xFF69F0AE, 0xFF40C4FF, 0xFFFFD740};

    private final int txt = 0xFFEDE7F6;
    private final int sub = 0xFFB0A9B5;
    private final int acc = 0xFFB69DF8;

    private List<Elem> elems;
    private final Map<Elem, View> views = new HashMap<>();
    private int page = 0;
    private int cols;
    private int cell;
    private int gap;
    private Elem sel = null;
    private boolean pendingReplace = false;
    private int pendingWidget = -1;
    private FrameLayout canvas;
    private View selView;
    private ScrollView scroll;
    private TextView info;
    private LinearLayout btnRow;
    private Button pageBtn;
    private AppWidgetHost host;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private static class GridBg extends View {
        private final int cell;
        private final int cols;
        private final Paint p = new Paint();

        GridBg(android.content.Context c, int cell, int cols) {
            super(c);
            this.cell = cell;
            this.cols = cols;
            p.setColor(0x22FFFFFF);
            p.setStrokeWidth(1f);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = cols * cell;
            int h = getHeight();
            for (int i = 0; i <= cols; i++) c.drawLine(i * cell, 0, i * cell, h, p);
            for (int y = 0; y <= h; y += cell) c.drawLine(0, y, w, y, p);
        }
    }

    private class SelView extends View {
        private final Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);

        SelView(android.content.Context c) {
            super(c);
            sp.setStyle(Paint.Style.STROKE);
            sp.setStrokeWidth((float) dp(2));
            sp.setColor(acc);
            hp.setColor(acc);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawRoundRect(1f, 1f, getWidth() - 1f, getHeight() - 1f, (float) dp(10), (float) dp(10), sp);
            float hs = (float) dp(20);
            c.drawRect(getWidth() - hs, getHeight() - hs, (float) getWidth(), (float) getHeight(), hp);
        }
    }

    private Button small(String text, final Runnable action) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setTextSize(12f);
        bt.setOnClickListener(v -> action.run());
        return bt;
    }

    private void confirm(String msg, final Runnable action) {
        new AlertDialog.Builder(this).setMessage(msg).setPositiveButton(Lang.t(366), (d, w) -> action.run()).setNegativeButton(Lang.t(24), null).show();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        host = new AppWidgetHost(getApplicationContext(), 1337);
        cols = Math.max(3, Skin.get(this, "canvasCols"));
        cell = (getResources().getDisplayMetrics().widthPixels - dp(24)) / cols;
        gap = dp(2);
        elems = Elem.load(this);
        if (elems.isEmpty()) {
            elems = Elem.example(cols);
            Elem.save(this, elems);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121216);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(12), dp(10), dp(8), dp(4));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(28f);
        back.setTextColor(txt);
        back.setPadding(0, 0, dp(12), dp(2));
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = new TextView(this);
        title.setText(Lang.t(414));
        title.setTextSize(18f);
        title.setTextColor(txt);
        title.setTypeface(null, Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, WCB, 1f));
        pageBtn = small(Lang.t(415), () -> { page = 1 - page; sel = null; rebuild(); });
        head.addView(pageBtn);
        head.addView(small(Lang.t(416), () -> confirm(Lang.t(417), () -> {
            clearWidgets();
            elems.clear();
            elems.addAll(Elem.example(cols));
            sel = null;
            Elem.save(this, elems);
            rebuild();
        })));
        head.addView(small(Lang.t(418), () -> confirm(Lang.t(419), () -> {
            clearWidgets();
            elems.clear();
            sel = null;
            Elem.save(this, elems);
            rebuild();
        })));
        root.addView(head);

        canvas = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.addView(canvas, new FrameLayout.LayoutParams(cols * cell, 12 * cell, Gravity.CENTER_HORIZONTAL));
        root.addView(scroll, new LinearLayout.LayoutParams(MPB, 0, 1f));

        info = new TextView(this);
        info.setTextColor(sub);
        info.setTextSize(12f);
        info.setPadding(dp(14), dp(6), dp(14), dp(2));
        root.addView(info);
        btnRow = new LinearLayout(this);
        btnRow.setPadding(dp(8), 0, dp(8), 0);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(btnRow);
        root.addView(hs);

        LinearLayout pal = new LinearLayout(this);
        pal.setPadding(dp(8), 0, dp(8), dp(8));
        pal.addView(small(Lang.t(420), () -> pickTile(key -> addElem("tile", 1, 1, 0, key))));
        pal.addView(small(Lang.t(421), () -> addElem("clock", Math.min(cols, 6), 2, 0, "")));
        pal.addView(small(Lang.t(422), () -> addElem("media", Math.min(cols, 5), 3, 0, "")));
        pal.addView(small(Lang.t(423), () -> addElem("notifs", cols, 5, 0, "")));
        pal.addView(small(Lang.t(424), () -> addElem("bright", Math.min(cols, 4), 1, 0, "")));
        pal.addView(small(Lang.t(425), () -> addElem("volume", Math.min(cols, 4), 1, 0, "")));
        pal.addView(small(Lang.t(426), () -> pickMedia(false, false)));
        pal.addView(small(Lang.t(427), () -> pickMedia(true, false)));
        pal.addView(small(Lang.t(428), () -> pickApp(pkg -> addElem("app", 1, 1, 0, pkg))));
        pal.addView(small(Lang.t(429), () -> pickWidget()));
        pal.addView(small(Lang.t(430), () -> promptText("", t -> addElem("text", Math.min(cols, 3), 1, 0, t))));
        HorizontalScrollView ps = new HorizontalScrollView(this);
        ps.setHorizontalScrollBarEnabled(false);
        ps.addView(pal);
        root.addView(ps);

        Insets.apply(root);
        setContentView(root);

        canvas.setOnTouchListener(new View.OnTouchListener() {
            Elem target = null;
            boolean resizing = false;
            float sx = 0f;
            float sy = 0f;
            int ox = 0;
            int oy = 0;
            int ow = 0;
            int oh = 0;

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: {
                        float x = ev.getX();
                        float y = ev.getY();
                        resizing = false;
                        if (sel != null && sel.page == page && inHandle(sel, x, y)) { target = sel; resizing = true; }
                        else target = hit(x, y);
                        if (target == null) {
                            if (sel != null) { sel = null; updateSel(); }
                            return false;
                        }
                        if (sel != target) { sel = target; updateSel(); }
                        sx = ev.getRawX();
                        sy = ev.getRawY();
                        ox = target.x;
                        oy = target.y;
                        ow = target.w;
                        oh = target.h;
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        if (target == null) return false;
                        int dx = Math.round((ev.getRawX() - sx) / cell);
                        int dy = Math.round((ev.getRawY() - sy) / cell);
                        if (resizing) {
                            target.w = Math.max(1, Math.min(cols - target.x, ow + dx));
                            target.h = Math.max(1, oh + dy);
                        } else {
                            target.x = Math.max(0, Math.min(cols - target.w, ox + dx));
                            target.y = Math.max(0, oy + dy);
                        }
                        relayout(target);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (target != null) {
                            Elem.save(LayoutEditorActivity.this, elems);
                            resizeCanvas();
                        }
                        target = null;
                        return true;
                    default:
                        return false;
                }
            }
        });

        rebuild();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private Elem hit(float x, float y) {
        for (int i = elems.size() - 1; i >= 0; i--) {
            Elem e = elems.get(i);
            if (e.page != page) continue;
            if (x >= e.x * cell && x < (e.x + e.w) * cell && y >= e.y * cell && y < (e.y + e.h) * cell) return e;
        }
        return null;
    }

    private boolean inHandle(Elem e, float x, float y) {
        float r = (e.x + e.w) * cell;
        float bt = (e.y + e.h) * cell;
        return x >= r - dp(28) && x <= r + dp(6) && y >= bt - dp(28) && y <= bt + dp(6);
    }

    private void place(View v, Elem e) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.max(1, e.w * cell - 2 * gap), Math.max(1, e.h * cell - 2 * gap));
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = e.x * cell + gap;
        lp.topMargin = e.y * cell + gap;
        v.setLayoutParams(lp);
    }

    private void resizeCanvas() {
        int rows = 12;
        for (Elem e : elems) {
            if (e.page == page) rows = Math.max(rows, e.y + e.h + 4);
        }
        canvas.setLayoutParams(new FrameLayout.LayoutParams(cols * cell, rows * cell, Gravity.CENTER_HORIZONTAL));
    }

    private void relayout(Elem e) {
        View v = views.get(e);
        if (v != null) place(v, e);
        if (selView != null && sel == e) place(selView, e);
        info.setText(describe(e) + "   (" + e.x + ", " + e.y + ")  " + e.w + "×" + e.h);
    }

    private void rebuild() {
        pageBtn.setText(Lang.t(431) + (page + 1));
        canvas.removeAllViews();
        views.clear();
        resizeCanvas();
        canvas.addView(new GridBg(this, cell, cols), new FrameLayout.LayoutParams(MPB, MPB));
        for (Elem e : elems) {
            if (e.page != page) continue;
            View v = preview(e);
            place(v, e);
            canvas.addView(v);
            views.put(e, v);
        }
        selView = new SelView(this);
        canvas.addView(selView, new FrameLayout.LayoutParams(1, 1));
        updateSel();
    }

    private void updateSel() {
        if (sel == null || sel.page != page) {
            selView.setVisibility(View.GONE);
        } else {
            selView.setVisibility(View.VISIBLE);
            place(selView, sel);
            selView.bringToFront();
        }
        updateProps();
    }

    private String widgetLabel(Elem e) {
        try {
            AppWidgetProviderInfo pi = AppWidgetManager.getInstance(this).getAppWidgetInfo(Integer.parseInt(e.a));
            return pi != null ? pi.loadLabel(getPackageManager()) : Lang.t(432);
        } catch (Exception x) { return Lang.t(432); }
    }

    private String tileName(String key) {
        String l = Skin.getStr(this, "lbl_" + key, null);
        if (l != null && !l.isEmpty()) return l;
        if (key.startsWith("ct:")) {
            for (String[] f : Skin.customTiles(this)) {
                if (("ct:" + f[0]).equals(key)) return f[2];
            }
        }
        if (key.startsWith("tp:")) {
            for (String[] f : PanelView.thirdPartyInfo(this)) {
                if (("tp:" + f[0]).equals(key)) return f[1];
            }
        }
        return Lang.s(key);
    }

    private String appName(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception x) { return pkg; }
    }

    private String describe(Elem e) {
        switch (e.type) {
            case "tile": return Lang.t(880) + tileName(e.a);
            case "clock": return Lang.t(881);
            case "media": return Lang.t(882);
            case "notifs": return Lang.t(874);
            case "bright": return Lang.t(873);
            case "volume": return Lang.t(883);
            case "image": return Lang.t(884);
            case "video": return Lang.t(885);
            case "widget": return Lang.t(886) + widgetLabel(e);
            case "text": return Lang.t(887) + e.a;
            case "app": return Lang.t(888) + appName(e.a);
            default: return e.type;
        }
    }

    private TextView centered(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12f);
        t.setTextColor(txt);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private View preview(Elem e) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2A2833);
        bg.setCornerRadius((float) dp(10));
        f.setBackground(bg);
        f.setClipToOutline(true);
        switch (e.type) {
            case "tile": {
                String glyph = Skin.getStr(this, "glyph_" + e.a, null);
                int idx = -1;
                for (int i = 0; i < PanelView.TILE_KEYS.length; i++) {
                    if (PanelView.TILE_KEYS[i].equals(e.a)) idx = i;
                }
                if (idx < 0 && e.a.startsWith("tp:") && (glyph == null || glyph.isEmpty())) {
                    ImageView tv2 = new ImageView(this);
                    android.graphics.drawable.Drawable td = PanelView.thirdPartyIcon(this, e.a.substring(3));
                    if (td != null) {
                        tv2.setImageDrawable(td);
                        tv2.setImageTintList(android.content.res.ColorStateList.valueOf(txt));
                    }
                    tv2.setPadding(dp(8), dp(8), dp(8), dp(8));
                    f.addView(tv2, new FrameLayout.LayoutParams(MPB, MPB));
                } else if ((glyph != null && !glyph.isEmpty()) || idx < 0) {
                    TextView g = centered(glyph != null && !glyph.isEmpty() ? glyph : "★");
                    g.setTextSize(22f);
                    f.addView(g, new FrameLayout.LayoutParams(MPB, MPB));
                } else {
                    ImageView iv = new ImageView(this);
                    iv.setImageResource(PanelView.TILE_ICONS[idx]);
                    iv.setImageTintList(android.content.res.ColorStateList.valueOf(txt));
                    iv.setPadding(dp(8), dp(8), dp(8), dp(8));
                    f.addView(iv, new FrameLayout.LayoutParams(MPB, MPB));
                }
                if (e.w >= 2) {
                    TextView n = centered(tileName(e.a));
                    n.setTextSize(10f);
                    f.addView(n, new FrameLayout.LayoutParams(MPB, WCB, Gravity.BOTTOM));
                }
                break;
            }
            case "app": {
                ImageView av = new ImageView(this);
                try { av.setImageDrawable(getPackageManager().getApplicationIcon(e.a)); } catch (Exception x) {}
                av.setPadding(dp(4), dp(4), dp(4), dp(4));
                f.addView(av, new FrameLayout.LayoutParams(MPB, MPB));
                if (e.w >= 2) {
                    TextView an = centered(appName(e.a));
                    an.setTextSize(10f);
                    f.addView(an, new FrameLayout.LayoutParams(MPB, WCB, Gravity.BOTTOM));
                }
                break;
            }
            case "image":
            case "video": {
                Uri u = Uri.parse(e.a);
                if (e.a.isEmpty()) {
                    f.addView(centered(Lang.t(433)), new FrameLayout.LayoutParams(MPB, MPB));
                } else if (Media.isVideo(this, u)) {
                    f.addView(centered(Lang.t(434)), new FrameLayout.LayoutParams(MPB, MPB));
                } else {
                    ImageView iv = new ImageView(this);
                    iv.setScaleType(e.b == 1 ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
                    Media.loadImage(iv, this, u);
                    f.addView(iv, new FrameLayout.LayoutParams(MPB, MPB));
                }
                break;
            }
            case "text": {
                TextView t = centered(e.a);
                t.setTextColor(e.b != 0 ? e.b : txt);
                t.setTextSize(16f);
                f.addView(t, new FrameLayout.LayoutParams(MPB, MPB));
                break;
            }
            default:
                f.addView(centered(describe(e)), new FrameLayout.LayoutParams(MPB, MPB));
                break;
        }
        return f;
    }

    private void updateProps() {
        btnRow.removeAllViews();
        if (sel == null) {
            info.setText("Tocá un elemento para editarlo. Arrastralo para moverlo y usá el cuadrito de la esquina para cambiar su tamaño.");
            return;
        }
        final Elem e = sel;
        info.setText(describe(e) + "   (" + e.x + ", " + e.y + ")  " + e.w + "×" + e.h);
        btnRow.addView(small(Lang.t(636), () -> {
            if (e.type.equals("widget")) {
                try { host.deleteAppWidgetId(Integer.parseInt(e.a)); } catch (Exception x) {}
            }
            elems.remove(e);
            sel = null;
            Elem.save(this, elems);
            rebuild();
        }));
        btnRow.addView(small(Lang.t(435), () -> {
            if (e.type.equals("clock") || e.type.equals("media") || e.type.equals("notifs") || e.type.equals("widget")) {
                Toast.makeText(this, Lang.t(436), Toast.LENGTH_SHORT).show();
                return;
            }
            Elem c = e.copy();
            placeFree(c);
            elems.add(c);
            sel = c;
            Elem.save(this, elems);
            rebuild();
        }));
        btnRow.addView(small(Lang.t(437) + (e.page == 0 ? 2 : 1), () -> {
            e.page = 1 - e.page;
            placeFree(e);
            page = e.page;
            Elem.save(this, elems);
            rebuild();
        }));
        btnRow.addView(small(Lang.t(438), () -> {
            elems.remove(e);
            elems.add(e);
            Elem.save(this, elems);
            rebuild();
        }));
        if (e.type.equals("tile")) {
            btnRow.addView(small(Lang.t(439), () -> pickTile(key -> { e.a = key; Elem.save(this, elems); rebuild(); })));
        }
        if (e.type.equals("app")) {
            btnRow.addView(small(Lang.t(440), () -> pickApp(pkg -> { e.a = pkg; Elem.save(this, elems); rebuild(); })));
        }
        if (e.type.equals("text")) {
            btnRow.addView(small(Lang.t(441), () -> promptText(e.a, t -> { e.a = t; Elem.save(this, elems); rebuild(); })));
            btnRow.addView(small(Lang.t(412), () -> {
                int idx = 0;
                for (int i = 0; i < COLORS.length; i++) {
                    if (COLORS[i] == e.b) idx = i;
                }
                e.b = COLORS[(idx + 1) % COLORS.length];
                Elem.save(this, elems);
                rebuild();
            }));
        }
        if (e.type.equals("image") || e.type.equals("video")) {
            btnRow.addView(small(Lang.t(637), () -> pickMedia(e.type.equals("video"), true)));
            btnRow.addView(small(e.b == 1 ? Lang.t(442) : Lang.t(443), () -> {
                e.b = e.b == 1 ? 0 : 1;
                Elem.save(this, elems);
                rebuild();
            }));
        }
    }

    private boolean overlaps(Elem a) {
        for (Elem o : elems) {
            if (o == a || o.page != a.page) continue;
            if (a.x < o.x + o.w && a.x + a.w > o.x && a.y < o.y + o.h && a.y + a.h > o.y) return true;
        }
        return false;
    }

    private void placeFree(Elem e) {
        e.w = Math.min(e.w, cols);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x + e.w <= cols; x++) {
                e.x = x;
                e.y = y;
                if (!overlaps(e)) return;
            }
        }
        e.x = 0;
        e.y = 0;
    }

    private void addElem(String type, int w, int h, int b, String a) {
        if (type.equals("clock") || type.equals("media") || type.equals("notifs")) {
            for (Elem o : elems) {
                if (o.type.equals(type)) {
                    Toast.makeText(this, "Ya hay uno; solo puede haber uno", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        Elem e = new Elem(Elem.newId(), type, page, 0, 0, w, h, b, a);
        placeFree(e);
        elems.add(e);
        sel = e;
        Elem.save(this, elems);
        rebuild();
    }

    private void pickTile(final Consumer<String> cb) {
        final List<String> keys = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (String k : PanelView.TILE_KEYS) {
            keys.add(k);
            names.add(tileName(k));
        }
        for (String[] f : Skin.customTiles(this)) {
            keys.add("ct:" + f[0]);
            names.add("★ " + (f[2].isEmpty() ? Lang.t(595) : f[2]));
        }
        for (String[] f : PanelView.thirdPartyInfo(this)) {
            keys.add("tp:" + f[0]);
            names.add(f[1] + Lang.t(863));
        }
        new AlertDialog.Builder(this).setTitle(Lang.t(444))
            .setItems(names.toArray(new CharSequence[0]), (d, which) -> cb.accept(keys.get(which))).show();
    }

    private void promptText(String initial, final Consumer<String> cb) {
        final EditText et = new EditText(this);
        et.setText(initial);
        new AlertDialog.Builder(this).setTitle(Lang.t(445)).setView(et)
            .setPositiveButton(Lang.t(366), (d, w) -> cb.accept(et.getText().toString()))
            .setNegativeButton(Lang.t(24), null).show();
    }

    private void pickMedia(boolean video, boolean replace) {
        pendingReplace = replace;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(video ? "video/*" : "image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, video ? REQ_VID : REQ_IMG);
    }

    private void pickApp(final Consumer<String> cb) {
        final android.content.pm.PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> list = pm.queryIntentActivities(main, 0);
        final List<String[]> apps = new ArrayList<>();
        for (android.content.pm.ResolveInfo ri : list) apps.add(new String[]{ri.loadLabel(pm).toString(), ri.activityInfo.packageName});
        java.util.Collections.sort(apps, (x, y) -> x[0].toLowerCase().compareTo(y[0].toLowerCase()));
        CharSequence[] names = new CharSequence[apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = apps.get(i)[0];
        new AlertDialog.Builder(this).setTitle(Lang.t(864)).setItems(names, (d, which) -> cb.accept(apps.get(which)[1])).show();
    }

    private void pickWidget() {
        final AppWidgetManager awm = AppWidgetManager.getInstance(this);
        final List<AppWidgetProviderInfo> list = awm.getInstalledProviders();
        List<CharSequence> names = new ArrayList<>();
        for (AppWidgetProviderInfo p : list) names.add(p.loadLabel(getPackageManager()));
        new AlertDialog.Builder(this).setTitle(Lang.t(446))
            .setItems(names.toArray(new CharSequence[0]), (d, which) -> {
                AppWidgetProviderInfo p = list.get(which);
                int id = host.allocateAppWidgetId();
                pendingWidget = id;
                if (awm.bindAppWidgetIdIfAllowed(id, p.provider)) {
                    configureOrAdd(id);
                } else {
                    Intent bi = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                    bi.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                    bi.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider);
                    startActivityForResult(bi, REQ_BIND);
                }
            }).show();
    }

    private void configureOrAdd(int id) {
        AppWidgetProviderInfo wi = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (wi != null && wi.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CFG, null);
                return;
            } catch (Exception e) {}
        }
        addElem("widget", Math.min(cols, 4), 2, 0, String.valueOf(id));
        pendingWidget = -1;
    }

    private void clearWidgets() {
        for (Elem e : elems) {
            if (e.type.equals("widget")) {
                try { host.deleteAppWidgetId(Integer.parseInt(e.a)); } catch (Exception x) {}
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_IMG || req == REQ_VID) {
            Uri u = res == RESULT_OK && data != null ? data.getData() : null;
            if (u == null) { pendingReplace = false; return; }
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception e) {}
            if (pendingReplace && sel != null) {
                sel.a = u.toString();
                Elem.save(this, elems);
                rebuild();
            } else {
                addElem(req == REQ_VID ? "video" : "image", Math.min(cols, 4), 3, 0, u.toString());
            }
            pendingReplace = false;
        } else if (req == REQ_BIND) {
            if (res == RESULT_OK && pendingWidget >= 0) configureOrAdd(pendingWidget);
            else if (pendingWidget >= 0) { host.deleteAppWidgetId(pendingWidget); pendingWidget = -1; }
        } else if (req == REQ_CFG) {
            if (res == RESULT_OK && pendingWidget >= 0) {
                addElem("widget", Math.min(cols, 4), 2, 0, String.valueOf(pendingWidget));
                pendingWidget = -1;
            } else if (pendingWidget >= 0) {
                host.deleteAppWidgetId(pendingWidget);
                pendingWidget = -1;
            }
        }
    }
}
