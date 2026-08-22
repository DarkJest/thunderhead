package dev.tempestfx.api;

/**
 * Surface properties sampled once at the impact point.
 *
 * @param type        broad material family, derived from tags and fluid state rather than a block switch
 * @param groundColor packed RGB taken from the block's map colour, so modded blocks work unchanged.
 *                    Never zero: see {@link #NEUTRAL_GROUND}
 * @param raining     whether weather is falling in the strike column
 * @param moisture    {@code 0..1} wetness used to bias steam against dust
 * @param surfaceY    world Y of the surface the bolt terminated on, or {@code NaN} when the caller
 *                    did not sample one; use {@link #surfaceY(double)} to read it safely
 * @param foliage     whether leaves are close enough to be disturbed by the pressure wave
 * @param brightness  {@code 0..1} light level at the impact, so unlit particles are not painted as
 *                    dark blobs in daylight or glowing ones at midnight
 */
public record LightningEnvironment(Type type, int groundColor, boolean raining, float moisture,
                                   double surfaceY, boolean foliage, float brightness) {
    public enum Type { LAND, WATER, SNOW, SAND, STONE, FOREST }

    /**
     * Stand-in for a surface with no map colour of its own.
     *
     * <p>{@code MapColor.NONE} packs to zero and air and glass both report it. Debris is drawn
     * untextured and takes this colour directly, so zero would render as black squares.
     */
    public static final int NEUTRAL_GROUND = 0x8A8A8A;

    public LightningEnvironment {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        moisture = Math.max(0f, Math.min(1f, moisture));
        brightness = Math.max(0f, Math.min(1f, brightness));
        if (groundColor == 0) groundColor = NEUTRAL_GROUND;
    }

    public static LightningEnvironment land(int color, boolean raining) {
        return new LightningEnvironment(Type.LAND, color, raining, raining ? 1f : 0f, Double.NaN, false, 1f);
    }

    /** Multiplier for unlit particle colours; the floor keeps debris visible at night. */
    public float litScale() { return 0.42f + 0.58f * brightness; }

    /** Sampled surface height, falling back to the given value when none was sampled. */
    public double surfaceY(double fallback) { return Double.isNaN(surfaceY) ? fallback : surfaceY; }

    public boolean water() { return type == Type.WATER; }

    /** Dry particulate families are suppressed on water. */
    public boolean dusty() { return type != Type.WATER; }
}
