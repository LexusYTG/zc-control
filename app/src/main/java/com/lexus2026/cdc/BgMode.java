package com.lexus2026.cdc;

public enum BgMode {
    SOLID(Lang.t(1)), TRANSPARENT(Lang.t(2)), BLUR(Lang.t(3)), IMAGE(Lang.t(4));

    public final String label;

    BgMode(String label) { this.label = label; }
}
