package com.lexus2026.cdc;

public enum TileShape {
    CIRCLE(Lang.t(619), "●"), ROUNDED(Lang.t(537), "▢"), SQUARE(Lang.t(621), "■"),
    TRIANGLE(Lang.t(538), "▲"), STAR(Lang.t(539), "★"), HEXAGON(Lang.t(540), "⬢"), DIAMOND(Lang.t(622), "◆");

    public final String label;
    public final String sym;

    TileShape(String label, String sym) {
        this.label = label;
        this.sym = sym;
    }
}
