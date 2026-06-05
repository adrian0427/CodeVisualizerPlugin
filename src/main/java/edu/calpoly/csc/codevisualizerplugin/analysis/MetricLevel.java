package edu.calpoly.csc.codevisualizerplugin.analysis;

import java.awt.Color;

public enum MetricLevel {
    LOW(new Color(101, 163, 13)),
    MEDIUM(new Color(217, 119, 6)),
    HIGH(new Color(220, 38, 38));

    private final Color color;

    MetricLevel(Color color) {
        this.color = color;
    }

    public Color color() {
        return color;
    }

    public static MetricLevel fromScore(int score) {
        if (score >= 24) {
            return HIGH;
        }
        if (score >= 10) {
            return MEDIUM;
        }
        return LOW;
    }
}
