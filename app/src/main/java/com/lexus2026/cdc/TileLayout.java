package com.lexus2026.cdc;

public enum TileLayout {
    ICON(Lang.t(534)), LABEL(Lang.t(535)), PILL(Lang.t(536));

    public final String label;

    TileLayout(String label) { this.label = label; }
}
