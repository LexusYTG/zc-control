package com.lexus2026.cdc;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Contenido de la notificación emergente: ícono de la app, nombre, "ahora", título y texto. */
public class SideNoticeCard extends LinearLayout {
    private final ImageView icon;
    private final TextView app;
    private final TextView when;
    private final TextView title;
    private final TextView body;
    private final float d;

    public SideNoticeCard(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(14), dp(12), dp(16), dp(12));

        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(icon, new LayoutParams(dp(38), dp(38)));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        LayoutParams clp = new LayoutParams(0, -2, 1f);
        clp.leftMargin = dp(12);
        addView(col, clp);

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        app = new TextView(c);
        app.setTextSize(11f);
        app.setTextColor(0xFFB69DF8);
        app.setTypeface(null, Typeface.BOLD);
        app.setSingleLine(true);
        app.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(app, new LayoutParams(0, -2, 1f));
        when = new TextView(c);
        when.setTextSize(11f);
        when.setTextColor(0xFFB0A9B5);
        when.setText("ahora");
        top.addView(when, new LayoutParams(-2, -2));
        col.addView(top, new LayoutParams(-1, -2));

        title = new TextView(c);
        title.setTextSize(14f);
        title.setTextColor(0xFFEDE7F6);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title, new LayoutParams(-1, -2));

        body = new TextView(c);
        body.setTextSize(13f);
        body.setTextColor(0xDDEDE7F6);
        body.setMaxLines(2);
        body.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(body, new LayoutParams(-1, -2));
    }

    private int dp(int v) { return (int) (v * d); }

    public void bind(MediaListener.Note n) {
        Drawable ic = n.appIcon != null ? n.appIcon : n.icon;
        icon.setImageDrawable(ic);
        app.setText(n.app);
        title.setText(n.title);
        title.setVisibility(n.title.isEmpty() ? GONE : VISIBLE);
        body.setText(n.text);
        body.setVisibility(n.text.isEmpty() ? GONE : VISIBLE);
    }
}
