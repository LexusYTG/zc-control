package com.lexus2026.cdc;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

public final class Insets {
    private Insets() {}

    public static void apply(View v) {
        v.setOnApplyWindowInsetsListener((view, in) -> {
            int l;
            int t;
            int r;
            int b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                l = i.left;
                t = i.top;
                r = i.right;
                b = i.bottom;
            } else {
                l = in.getSystemWindowInsetLeft();
                t = in.getSystemWindowInsetTop();
                r = in.getSystemWindowInsetRight();
                b = in.getSystemWindowInsetBottom();
            }
            view.setPadding(l, t, r, b);
            return in;
        });
        v.requestApplyInsets();
    }
}
