package com.lexus2026.cdc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

public class MeterView extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint val = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lab = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sep = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private float p = 0f;
    private int cpu = 0;
    private int gpu = -1;
    private boolean gpuEst = false;
    private int fps = 0;
    private long memKb = 0L;
    private float temp = -1f;

    public MeterView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bg.setColor(0xE6111827);
        val.setColor(0xFFFFFFFF);
        val.setTextSize(14f * d);
        val.setTextAlign(Paint.Align.CENTER);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        lab.setColor(0xFF94A3B8);
        lab.setTextSize(9f * d);
        lab.setTextAlign(Paint.Align.CENTER);
        sep.setColor(0xFFFFFFFF);
        sep.setStrokeWidth(d);
    }

    public void setProgress(float v) {
        p = v;
        invalidate();
    }

    public boolean hasGpu() {
        return gpu >= 0;
    }

    public void set(GameMeter.Stats s) {
        cpu = s.cpu;
        memKb = s.memKb;
        gpu = s.gpu;
        gpuEst = s.gpuEst;
        fps = s.fps;
        temp = s.temp;
        invalidate();
    }

    private String mem() {
        if (memKb <= 0L) return "--";
        long mb = memKb / 1024L;
        if (mb >= 1024L) return String.format(Locale.US, "%.1f GB", mb / 1024f);
        return mb + Lang.t(472);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float r = Math.min(h / 2f, 24f * d);
        c.drawRoundRect(0f, 0f, w, h, r, r, bg);
        float a = Math.max(0f, Math.min(1f, (p - 0.55f) / 0.45f));
        if (a <= 0f) return;
        boolean hasGpu = gpu >= 0;
        String[] v = hasGpu
            ? new String[]{cpu + "%", mem(), (gpuEst ? "~" : "") + gpu + "%", String.valueOf(fps)}
            : new String[]{cpu + "%", mem(), String.valueOf(fps)};
        String[] labels = hasGpu ? new String[]{Lang.t(473), Lang.t(474), Lang.t(475), Lang.t(476)} : new String[]{Lang.t(473), Lang.t(474), Lang.t(476)};
        int cols = v.length;
        float rowH = h / 2f;
        float colW = w / cols;
        val.setAlpha((int) (255 * a));
        lab.setAlpha((int) (255 * a));
        sep.setAlpha((int) (51 * a));
        for (int i = 0; i < cols; i++) {
            float cx = colW * i + colW / 2f;
            c.drawText(v[i], cx, rowH * 0.54f, val);
            c.drawText(labels[i], cx, rowH * 0.88f, lab);
            if (i < cols - 1) c.drawLine(colW * (i + 1), rowH * 0.2f, colW * (i + 1), rowH * 0.88f, sep);
        }
        sep.setAlpha((int) (31 * a));
        c.drawLine(w * 0.08f, rowH, w * 0.92f, rowH, sep);
        sep.setAlpha((int) (51 * a));
        String t = temp < 0f ? "--" : String.format(Locale.US, "%.0f°C", temp);
        c.drawText(t, w / 2f, rowH + rowH * 0.54f, val);
        c.drawText(Lang.t(477), w / 2f, rowH + rowH * 0.88f, lab);
    }
}
