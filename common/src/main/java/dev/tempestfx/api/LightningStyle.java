package dev.tempestfx.api;

import dev.tempestfx.math.FxMath;

/**
 * How one strike should look, independent of the player's own lightning settings.
 *
 * <p>Values are relative to the stock look: {@code 1} everywhere is a normal bolt. Nothing here can
 * raise brightness or reach the reduced-flashing mode, which stay global.
 *
 * @param thickness  channel width, {@code 0.25..4}
 * @param branchiness how much the channel forks, {@code 0..3}
 * @param scale      how far up the channel reaches, {@code 0.25..3}
 * @param coldTint   {@code 0} warms the glow toward white, {@code 2} doubles the cold cast
 * @param coreColor  {@code 0xRRGGBB} for the conducting core, or {@link #AUTOMATIC} for near-white
 * @param glowColor  {@code 0xRRGGBB} for the halo and sheath, or {@link #AUTOMATIC} to derive them
 *                   from {@code coldTint} the way an ordinary bolt does
 */
public record LightningStyle(float thickness, float branchiness, float scale, float coldTint,
                             int coreColor, int glowColor) {
    /** A colour left to the mod. Outside the 24-bit range, so it cannot collide with a real one. */
    public static final int AUTOMATIC = -1;
    /** The stock look, with no adjustment. */
    public static final LightningStyle DEFAULT = new LightningStyle(1, 1, 1, 1, AUTOMATIC, AUTOMATIC);

    public LightningStyle {
        thickness = clamp(thickness, 0.25f, 4f);
        branchiness = clamp(branchiness, 0f, 3f);
        scale = clamp(scale, 0.25f, 3f);
        coldTint = clamp(coldTint, 0f, 2f);
        coreColor = colour(coreColor);
        glowColor = colour(glowColor);
    }

    public boolean hasCoreColor() { return coreColor != AUTOMATIC; }

    public boolean hasGlowColor() { return glowColor != AUTOMATIC; }

    public boolean isDefault() { return DEFAULT.equals(this); }

    public static Builder builder() { return new Builder(); }

    /** Anything outside 24-bit RGB is treated as "not specified" rather than as an error. */
    private static int colour(int value) {
        return value < 0 || value > 0xFFFFFF ? AUTOMATIC : value;
    }

    /** Clamps rather than throws: a bad number from an integration should not kill the storm. */
    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? (float) FxMath.clamp(value, min, max) : 1f;
    }

    public static final class Builder {
        private float thickness = 1;
        private float branchiness = 1;
        private float scale = 1;
        private float coldTint = 1;
        private int coreColor = AUTOMATIC;
        private int glowColor = AUTOMATIC;

        public Builder thickness(float value) { thickness = value; return this; }
        public Builder branchiness(float value) { branchiness = value; return this; }
        public Builder scale(float value) { scale = value; return this; }
        public Builder coldTint(float value) { coldTint = value; return this; }

        /** {@code 0xRRGGBB} for the conducting core. */
        public Builder coreColor(int value) { coreColor = value; return this; }

        /** {@code 0xRRGGBB} for the halo and the sheath around the core. */
        public Builder glowColor(int value) { glowColor = value; return this; }

        /** Both layers at once, for a bolt that is simply one colour. */
        public Builder color(int value) { coreColor = value; glowColor = value; return this; }

        public LightningStyle build() {
            return new LightningStyle(thickness, branchiness, scale, coldTint, coreColor, glowColor);
        }
    }
}
