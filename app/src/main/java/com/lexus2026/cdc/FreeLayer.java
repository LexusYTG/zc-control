package com.lexus2026.cdc;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

/**
 * Capa de elementos libres del escritorio (apps, textos, relojes, imágenes, widgets, atajos, botones y formas) colocados a mano.
 * Con {@code live} los elementos responden a toque, doble toque, mantener y deslizamientos; sin {@code live} solo se dibujan
 * (lo usa el editor visual, que se encarga de los toques).
 */
final class FreeLayer extends FrameLayout {
    static final int WIDGET_HOST_ID = 1338;

    private final Activity act;
    private final boolean live;
    private final Consumer<String> runner;
    private final AppWidgetHost host;
    private final float d;

    FreeLayer(Activity a, boolean live, AppWidgetHost host, Consumer<String> runner) {
        super(a);
        this.act = a;
        this.live = live;
        this.host = host;
        this.runner = runner;
        this.d = a.getResources().getDisplayMetrics().density;
        setClipChildren(false);
    }

    private int dp(float v) { return (int) (v * d + 0.5f); }

    /** Rectángulo en píxeles de pantalla de un elemento libre. */
    static int[] rect(Activity a, VisualModel.Free f) {
        int W = a.getResources().getDisplayMetrics().widthPixels, H = a.getResources().getDisplayMetrics().heightPixels;
        return new int[]{f.x * W / 1000, f.y * H / 1000, Math.max(1, f.w * W / 1000), Math.max(1, f.h * H / 1000)};
    }

    void load(List<VisualModel.Free> list) {
        removeAllViews();
        for (VisualModel.Free f : list) {
            if (!f.visible && live) continue;
            View v = make(f);
            if (v == null) continue;
            int[] r = rect(act, f);
            LayoutParams lp = new LayoutParams(r[2], r[3], Gravity.TOP | Gravity.START);
            lp.leftMargin = r[0];
            lp.topMargin = r[1];
            v.setLayoutParams(lp);
            v.setAlpha(Math.max(0, Math.min(100, f.opacity)) / 100f * (f.visible ? 1f : 0.35f));
            if (live && !f.type.equals("widget")) attachGestures(v, f);
            addView(v);
        }
    }

    /** Solo para el editor (sin {@code live}): el hijo i corresponde al elemento i de la lista. */
    void place(int i, VisualModel.Free f) {
        View v = getChildAt(i);
        if (v == null) return;
        int[] r = rect(act, f);
        LayoutParams lp = (LayoutParams) v.getLayoutParams();
        lp.width = r[2];
        lp.height = r[3];
        lp.leftMargin = r[0];
        lp.topMargin = r[1];
        v.setLayoutParams(lp);
    }

    private GradientDrawable bgOf(VisualModel.Free f) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(f.bg);
        g.setCornerRadius(dp(f.radius));
        return g;
    }

    private TextView label(String s, VisualModel.Free f, float size) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextColor(f.tcolor);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine();
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (f.bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private View make(VisualModel.Free f) {
        FrameLayout box = new FrameLayout(act);
        box.setBackground(bgOf(f));
        box.setClipToOutline(true);
        switch (f.type) {
            case "app": {
                LinearLayout col = new LinearLayout(act);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER);
                ImageView iv = new ImageView(act);
                String name = f.a;
                try {
                    iv.setImageDrawable(act.getPackageManager().getApplicationIcon(f.a));
                    name = act.getPackageManager().getApplicationLabel(act.getPackageManager().getApplicationInfo(f.a, 0)).toString();
                } catch (Throwable ignored) { }
                int side = Math.min(rect(act, f)[2], rect(act, f)[3]);
                int sz = f.label ? Math.max(dp(16), (int) (side * 0.62f)) : Math.max(dp(16), (int) (side * 0.9f));
                col.addView(iv, new LinearLayout.LayoutParams(sz, sz));
                if (f.label) col.addView(label(name, f, Math.max(9, f.tsize * 0.6f)), new LinearLayout.LayoutParams(-1, -2));
                box.addView(col, new LayoutParams(-1, -1));
                break;
            }
            case "clock": {
                TextClock tc = new TextClock(act);
                String[] fm = {"HH:mm", "HH:mm:ss", "hh:mm a", "EEE d MMM", "EEEE d 'de' MMMM"};
                String fmt = fm[Math.max(0, Math.min(fm.length - 1, f.fmt))];
                tc.setFormat12Hour(fmt);
                tc.setFormat24Hour(fmt);
                tc.setTextColor(f.tcolor);
                tc.setTextSize(f.tsize);
                tc.setGravity(Gravity.CENTER);
                if (f.bold) tc.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                box.addView(tc, new LayoutParams(-1, -1));
                break;
            }
            case "image": {
                if (f.a.isEmpty()) { box.addView(label("🖼", f, 24), new LayoutParams(-1, -1)); break; }
                ImageView iv = new ImageView(act);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Media.loadImage(iv, act, Uri.parse(f.a));
                box.addView(iv, new LayoutParams(-1, -1));
                break;
            }
            case "widget": {
                try {
                    int id = Integer.parseInt(f.a);
                    AppWidgetProviderInfo pi = AppWidgetManager.getInstance(act).getAppWidgetInfo(id);
                    if (pi != null && host != null && live) {
                        View wv = host.createView(act, id, pi);
                        box.addView(wv, new LayoutParams(-1, -1));
                    } else {
                        box.setBackgroundColor(0xFF2A2833);
                        box.addView(label(pi != null ? pi.loadLabel(act.getPackageManager()) : "Widget", f, 12), new LayoutParams(-1, -1));
                    }
                } catch (Throwable t) { box.addView(label("Widget", f, 12), new LayoutParams(-1, -1)); }
                break;
            }
            case "category":
            case "action": {
                LinearLayout col = new LinearLayout(act);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER);
                String glyph = f.icon.isEmpty() ? (f.type.equals("category") ? "📁" : "●") : f.icon;
                col.addView(label(glyph, f, Math.max(14, f.tsize * 1.5f)), new LinearLayout.LayoutParams(-1, -2));
                String txt = f.type.equals("category") ? categoryName(f.a) : f.a;
                if (f.label && !txt.isEmpty()) col.addView(label(txt, f, Math.max(9, f.tsize * 0.6f)), new LinearLayout.LayoutParams(-1, -2));
                box.addView(col, new LayoutParams(-1, -1));
                break;
            }
            case "shape":
                break;
            default: { // text
                TextView t = label(f.a, f, f.tsize);
                t.setSingleLine(false);
                t.setEllipsize(null);
                box.addView(t, new LayoutParams(-1, -1));
            }
        }
        return box;
    }

    private String categoryName(String id) {
        if ("*".equals(id)) return SL.t(act, "Todas las apps", "All apps");
        try {
            CategoryManager.Category c = new CategoryManager(act).byId(id);
            return c == null ? "" : c.name;
        } catch (Throwable t) { return ""; }
    }

    /** Si el elemento no tiene acción de toque propia, hace lo lógico según su tipo. */
    private String defaultTap(VisualModel.Free f) {
        if (f.type.equals("app")) return "app:" + f.a;
        if (f.type.equals("category")) return "drawer:" + f.a;
        return "";
    }

    private void attachGestures(final View v, final VisualModel.Free f) {
        final boolean hasDbl = !f.dbl.isEmpty();
        final GestureDetector gd = new GestureDetector(act, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (!hasDbl) fire(f.tap.isEmpty() ? defaultTap(f) : f.tap);
                return true;
            }

            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (hasDbl) fire(f.tap.isEmpty() ? defaultTap(f) : f.tap);
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) { fire(f.dbl); return true; }

            @Override public void onLongPress(MotionEvent e) {
                if (f.hold.isEmpty()) return;
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                fire(f.hold);
            }

            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null) return false;
                float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
                String go;
                if (Math.abs(dx) > Math.abs(dy)) go = dx > 0 ? f.srt : f.slf;
                else go = dy > 0 ? f.sdn : f.sup;
                if (go.isEmpty()) return false;
                fire(go);
                return true;
            }
        });
        v.setOnTouchListener((x, e) -> gd.onTouchEvent(e));
    }

    private void fire(String action) {
        if (action == null || action.isEmpty() || runner == null) return;
        runner.accept(action);
    }
}
