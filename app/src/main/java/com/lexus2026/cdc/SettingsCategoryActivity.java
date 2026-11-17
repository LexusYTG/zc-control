package com.lexus2026.cdc;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Lista de ajustes de una categoría (se genera desde {@link SettingsRegistry}). */
public class SettingsCategoryActivity extends Activity {
    public static final String EXTRA_CAT = "cat";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SettingsRegistry.Category cat = SettingsRegistry.category(getIntent().getStringExtra(EXTRA_CAT));
        if (cat == null) { finish(); return; }

        LinearLayout root = ZUi.box(this, LinearLayout.VERTICAL);
        root.setPadding(ZUi.dp(this, 20), ZUi.dp(this, 20), ZUi.dp(this, 20), ZUi.dp(this, 32));

        TextView back = ZUi.text(this, "‹  Ajustes", 14f, ZUi.ACC, true);
        back.setPadding(0, ZUi.dp(this, 8), 0, ZUi.dp(this, 12));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(ZUi.text(this, cat.title, 28f, ZUi.TXT, true));
        TextView sub = ZUi.text(this, cat.sub, 14f, ZUi.SUB, false);
        sub.setPadding(0, ZUi.dp(this, 4), 0, ZUi.dp(this, 16));
        root.addView(sub);

        LinearLayout card = ZUi.box(this, LinearLayout.VERTICAL);
        card.setBackground(ZUi.shape(this, ZUi.CARD, 20));
        List<SettingsRegistry.Leaf> leaves = cat.leaves;
        for (int i = 0; i < leaves.size(); i++) {
            final SettingsRegistry.Leaf l = leaves.get(i);
            if (i > 0) card.addView(ZUi.divider(this), new LinearLayout.LayoutParams(-1, 1));
            final boolean dim = l.target == null;
            card.addView(ZUi.row(this, null, l.title, l.sub, dim, (View v) -> {
                if (dim) Toast.makeText(this, "Todavía no está disponible", Toast.LENGTH_SHORT).show();
                else l.target.open(this);
            }));
        }
        root.addView(card);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(ZUi.BG);
        sv.addView(root);
        sv.setClipToPadding(false);
        Insets.apply(sv);
        setContentView(sv);
    }
}
