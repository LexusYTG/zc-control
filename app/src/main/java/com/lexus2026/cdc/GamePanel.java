package com.lexus2026.cdc;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public final class GamePanel {
    private final ControlService svc;
    private final WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private GameBubbleView bubble = null;
    private WindowManager.LayoutParams bubbleLp = null;
    private View panel = null;
    private int bubbleY = -1;
    private String label = "";
    private boolean shown = false;
    private long lastClose = 0L;
    private MeterView meter = null;
    private WindowManager.LayoutParams meterLp = null;
    private GameMeter stats = null;
    private ValueAnimator meterAnim = null;
    private boolean meterBusy = false;
    private float mp = 0f;
    private int meterX = Integer.MIN_VALUE;
    private int meterY = 0;
    private String pkgName = "";
    private boolean gpuShown = false;

    public GamePanel(ControlService s) {
        svc = s;
        wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
    }

    private int dp(float v) {
        return GcUi.dp(svc, v);
    }

    private int screenH() {
        return svc.getResources().getDisplayMetrics().heightPixels;
    }

    public void show(String pkg) {
        if (shown) return;
        shown = true;
        pkgName = pkg;
        try {
            PackageManager pm = svc.getPackageManager();
            label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            label = pkg;
        }
        addBubble();
    }

    public void hide() {
        shown = false;
        removeMeter();
        closePanel();
        removeBubble();
    }

    public void refreshLayout() {
        ui.postDelayed(() -> {
            if (bubble != null && bubbleLp != null) {
                bubbleY = Math.max(0, Math.min(bubbleY, screenH() - dp(44)));
                bubbleLp.y = bubbleY;
                try {
                    wm.updateViewLayout(bubble, bubbleLp);
                } catch (Exception e) {
                }
            }
            if (meter != null && meterLp != null && !meterBusy) {
                int sw = svc.getResources().getDisplayMetrics().widthPixels;
                meterLp.x = Math.max(0, Math.min(sw - meterLp.width, meterLp.x));
                meterLp.y = Math.max(0, Math.min(screenH() - meterLp.height, meterLp.y));
                try {
                    wm.updateViewLayout(meter, meterLp);
                } catch (Exception e) {
                }
            }
        }, 300L);
    }

    public void destroy() {
        hide();
    }

    private void removeBubble() {
        if (bubble != null) {
            try {
                wm.removeViewImmediate(bubble);
            } catch (Exception e) {
            }
        }
        bubble = null;
        bubbleLp = null;
    }

    private void addBubble() {
        if (bubble != null) return;
        final GameBubbleView b = new GameBubbleView(svc);
        b.setAlpha(0.75f);
        int s = dp(44);
        if (bubbleY < 0) bubbleY = screenH() / 3;
        bubbleY = Math.max(0, Math.min(bubbleY, screenH() - s));
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            s, s, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(2);
        lp.y = bubbleY;
        noInsets(lp);
        b.setOnTouchListener(new View.OnTouchListener() {
            private float downY;
            private int startY;
            private boolean moved;
            private boolean wasOpen;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        startY = lp.y;
                        moved = false;
                        wasOpen = panel != null;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dy) > dp(8)) moved = true;
                        if (moved && bubble != null) {
                            lp.y = Math.max(0, Math.min(screenH() - dp(44), startY + (int) dy));
                            bubbleY = lp.y;
                            try {
                                wm.updateViewLayout(bubble, lp);
                            } catch (Exception x) {
                            }
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            if (wasOpen || SystemClock.uptimeMillis() - lastClose < 400) closePanel();
                            else openPanel();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
        try {
            wm.addView(b, lp);
            bubble = b;
            bubbleLp = lp;
        } catch (Exception e) {
            bubble = null;
            bubbleLp = null;
        }
    }

    private void closePanel() {
        View p = panel;
        panel = null;
        if (p != null) {
            lastClose = SystemClock.uptimeMillis();
            try {
                wm.removeViewImmediate(p);
            } catch (Exception e) {
            }
        }
    }

    private void add(LinearLayout box, View v, float topDp) {
        box.addView(v, GcUi.lp(svc, GcUi.MATCH, GcUi.WRAP, 0f, topDp));
    }

    private void openPanel() {
        if (panel != null || bubbleLp == null) return;
        LinearLayout box = GcUi.col(svc);
        box.setPadding(dp(16), dp(14), dp(16), dp(16));
        box.setBackground(GcUi.round(svc, 0xF2111827, 18));

        LinearLayout head = GcUi.row(svc);
        head.addView(GcUi.tv(svc, "Game Control", 16, GcUi.TEXT, true), new LinearLayout.LayoutParams(0, GcUi.WRAP, 1f));
        TextView x = GcUi.tv(svc, "✕", 18, GcUi.SUB, false);
        x.setPadding(dp(12), dp(4), dp(4), dp(4));
        x.setOnClickListener(v -> closePanel());
        head.addView(x);
        box.addView(head);
        box.addView(GcUi.tv(svc, label, 12, GcUi.SUB, false));

        add(box, GcUi.tv(svc, "Brillo", 13, GcUi.SUB, true), 14);
        SeekBar sb = new SeekBar(svc);
        sb.setMax(GameOps.maxBrightness());
        sb.setProgress(GameOps.brightness(svc));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean first = true;

            @Override
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (!fromUser) return;
                if (first) {
                    GameOps.brightnessMark(svc);
                    first = false;
                }
                GameOps.setBrightness(svc, v, false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                if (Settings.System.canWrite(svc)) return;
                final int v = s.getProgress();
                new Thread(() -> GameOps.setBrightness(svc, v, true)).start();
            }
        });
        add(box, sb, 4);

        final Switch sw = new Switch(svc);
        sw.setText("Ahorro de batería");
        sw.setTextColor(GcUi.TEXT);
        sw.setEnabled(false);
        add(box, sw, 12);
        new Thread(() -> {
            final boolean on = GameOps.saverOn();
            ui.post(() -> {
                if (panel == null) return;
                sw.setChecked(on);
                sw.setEnabled(true);
                sw.setOnCheckedChangeListener((b, c) -> new Thread(() -> GameOps.saver(svc, c)).start());
            });
        }).start();

        TextView mb = GcUi.tv(svc, "Medidor de rendimiento", 14, GcUi.TEXT, true);
        mb.setGravity(Gravity.CENTER);
        mb.setPadding(0, dp(12), 0, dp(12));
        mb.setBackground(GcUi.round(svc, GcUi.BOX, 12));
        mb.setOnClickListener(v -> openMeter());
        add(box, mb, 14);

        box.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                closePanel();
                return true;
            }
            return false;
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            dp(290), GcUi.WRAP, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(44);
        lp.y = Math.max(dp(16), Math.min(bubbleY - dp(60), screenH() - dp(400)));
        try {
            wm.addView(box, lp);
            panel = box;
        } catch (Exception e) {
            panel = null;
        }
    }

    private void noInsets(WindowManager.LayoutParams lp) {
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.setFitInsetsTypes(0);
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private int meterW() {
        return gpuShown ? dp(268) : dp(201);
    }

    private int statusBarH() {
        int id = svc.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? svc.getResources().getDimensionPixelSize(id) : dp(24);
    }

    private void removeMeter() {
        if (meterAnim != null) meterAnim.cancel();
        meterAnim = null;
        if (stats != null) {
            stats.stop();
            stats = null;
        }
        if (meter != null) {
            try {
                wm.removeViewImmediate(meter);
            } catch (Exception e) {
            }
        }
        meter = null;
        meterLp = null;
        mp = 0f;
        meterBusy = false;
        if (bubble != null) bubble.setAlpha(0.75f);
    }

    private void openMeter() {
        if (meter != null || bubbleLp == null) return;
        closePanel();
        final MeterView mv = new MeterView(svc);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            dp(44), dp(44), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = bubbleLp.x;
        lp.y = bubbleLp.y;
        noInsets(lp);
        mv.setOnTouchListener(new View.OnTouchListener() {
            private float x0;
            private float y0;
            private int sx;
            private int sy;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (meterBusy || meterLp == null || meter == null) return true;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = e.getRawX();
                        y0 = e.getRawY();
                        sx = meterLp.x;
                        sy = meterLp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - x0;
                        float dy = e.getRawY() - y0;
                        if (!moved && Math.hypot(dx, dy) > dp(8)) moved = true;
                        if (moved) {
                            int sw = svc.getResources().getDisplayMetrics().widthPixels;
                            meterLp.x = Math.max(0, Math.min(sw - meterLp.width, sx + (int) dx));
                            meterLp.y = Math.max(0, Math.min(screenH() - meterLp.height, sy + (int) dy));
                            try {
                                wm.updateViewLayout(meter, meterLp);
                            } catch (Exception x) {
                            }
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) collapseMeter();
                        else {
                            meterX = meterLp.x;
                            meterY = meterLp.y;
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
        try {
            wm.addView(mv, lp);
        } catch (Exception e) {
            return;
        }
        meter = mv;
        meterLp = lp;
        mp = 0f;
        stats = new GameMeter();
        gpuShown = false;
        try {
            GameMeter.logFile = new java.io.File(svc.getExternalFilesDir(null), "fps-debug.txt");
        } catch (Throwable ignored) {
        }
        stats.start(pkgName, st -> {
            if (meter == null) return;
            meter.set(st);
            boolean g = st.gpu >= 0;
            if (g != gpuShown && !meterBusy && meterLp != null && mp >= 1f) {
                gpuShown = g;
                int sw2 = svc.getResources().getDisplayMetrics().widthPixels;
                int nx = Math.max(0, Math.min(sw2 - meterW(), meterLp.x));
                animateMeter(nx, meterLp.y, meterW(), dp(88), 1f, null);
            }
        });
        boolean saved = meterX != Integer.MIN_VALUE;
        int tx = saved ? meterX : dp(8);
        int ty = saved ? meterY : statusBarH() + dp(6);
        int sw = svc.getResources().getDisplayMetrics().widthPixels;
        tx = Math.max(0, Math.min(sw - meterW(), tx));
        ty = Math.max(0, Math.min(screenH() - dp(88), ty));
        animateMeter(tx, ty, meterW(), dp(88), 1f, null);
    }

    private void collapseMeter() {
        if (meter == null || meterLp == null || meterBusy || bubbleLp == null) return;
        meterX = meterLp.x;
        meterY = meterLp.y;
        animateMeter(bubbleLp.x, bubbleLp.y, dp(44), dp(44), 0f, this::removeMeter);
    }

    private void animateMeter(final int tx, final int ty, final int tw, final int th, final float tp, final Runnable end) {
        if (meter == null || meterLp == null) return;
        if (meterAnim != null) meterAnim.cancel();
        final int fx = meterLp.x;
        final int fy = meterLp.y;
        final int fw = meterLp.width;
        final int fh = meterLp.height;
        final float fp = mp;
        meterBusy = true;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(240);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(an -> {
            if (meter == null || meterLp == null) return;
            float t = (Float) an.getAnimatedValue();
            meterLp.x = (int) (fx + (tx - fx) * t);
            meterLp.y = (int) (fy + (ty - fy) * t);
            meterLp.width = (int) (fw + (tw - fw) * t);
            meterLp.height = (int) (fh + (th - fh) * t);
            mp = fp + (tp - fp) * t;
            meter.setProgress(mp);
            if (bubble != null) bubble.setAlpha(0.75f * (1f - mp));
            try {
                wm.updateViewLayout(meter, meterLp);
            } catch (Exception e) {
            }
        });
        final boolean[] cancelled = {false};
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator an) {
                cancelled[0] = true;
            }

            @Override
            public void onAnimationEnd(Animator an) {
                if (cancelled[0]) return;
                meterBusy = false;
                if (end != null) end.run();
            }
        });
        meterAnim = a;
        a.start();
    }
}
