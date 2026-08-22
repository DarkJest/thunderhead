package dev.tempestfx.lightning;

public enum LightningLod {
    FULL, MEDIUM, DISTANT, ATMOSPHERIC;

    public static LightningLod forDistance(double distance) {
        if (distance < 32) return FULL;
        if (distance < 96) return MEDIUM;
        if (distance < 256) return DISTANT;
        return ATMOSPHERIC;
    }
}
