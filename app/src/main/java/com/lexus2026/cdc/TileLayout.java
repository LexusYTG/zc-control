package com.lexus2026.cdc;

public enum TileLayout {
    ICON("Solo ícono"), LABEL("Ícono y texto"), PILL("Píldora");

    public final String label;

    TileLayout(String label) { this.label = label; }
}
