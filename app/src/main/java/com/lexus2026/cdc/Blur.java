package com.lexus2026.cdc;

import android.graphics.Bitmap;

/** Desenfoque de caja (3 pasadas ≈ gaussiano) sobre un bitmap chico. Barato y sin depender del compositor. */
public final class Blur {
    private Blur() {}

    public static Bitmap apply(Bitmap src, int r, int passes) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] a = new int[w * h];
        int[] b = new int[w * h];
        src.getPixels(a, 0, w, 0, 0, w, h);
        for (int n = 0; n < passes; n++) {
            box(a, b, w, h, r, true);
            box(b, a, w, h, r, false);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(a, 0, w, 0, 0, w, h);
        return out;
    }

    private static void box(int[] src, int[] dst, int w, int h, int r, boolean horiz) {
        int len = horiz ? w : h;
        int lines = horiz ? h : w;
        int d = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int sr = 0;
            int sg = 0;
            int sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[ix(Math.max(0, Math.min(len - 1, i)), line, w, horiz)];
                sr += (c >> 16) & 255;
                sg += (c >> 8) & 255;
                sb += c & 255;
            }
            for (int i = 0; i < len; i++) {
                dst[ix(i, line, w, horiz)] = (0xFF << 24) | ((sr / d) << 16) | ((sg / d) << 8) | (sb / d);
                int add = src[ix(Math.min(i + r + 1, len - 1), line, w, horiz)];
                int sub = src[ix(Math.max(i - r, 0), line, w, horiz)];
                sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
                sb += (add & 255) - (sub & 255);
            }
        }
    }

    private static int ix(int i, int line, int w, boolean horiz) {
        return horiz ? line * w + i : i * w + line;
    }
}
