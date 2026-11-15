package com.lexus2026.cdc;

public enum TileShape {
    CIRCLE("Círculo", "●"), ROUNDED("Redondeado", "▢"), SQUARE("Cuadrado", "■"),
    TRIANGLE("Triángulo", "▲"), STAR("Estrella", "★"), HEXAGON("Hexágono", "⬢"), DIAMOND("Rombo", "◆");

    public final String label;
    public final String sym;

    TileShape(String label, String sym) {
        this.label = label;
        this.sym = sym;
    }
}
