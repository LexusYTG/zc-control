package com.lexus2026.cdc;

public enum BgMode {
    SOLID("Sólido"), TRANSPARENT("Transparente"), BLUR("Difuminado"), IMAGE("Imagen");

    public final String label;

    BgMode(String label) { this.label = label; }
}
