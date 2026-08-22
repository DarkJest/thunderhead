package dev.tempestfx.render.composite;

/**
 * Where the light shafts come from, in four floats.
 *
 * <p>Measured during the world pass, where the pose stack still maps world coordinates into view
 * space, and consumed by the bloom chain afterwards — the same arrangement {@link DistortionField}
 * uses, and for the same reason: the measurement needs the frame's matrices and the application does
 * not.
 *
 * @param centerX screen-space position of the channel, {@code 0..1}
 * @param centerY screen-space position of the channel, {@code 0..1}
 * @param strength how hard to smear toward it; 0 means no shaft pass at all
 * @param aspect  window aspect, so the falloff around the channel is round rather than stretched
 */
public record LightShaftField(float centerX, float centerY, float strength, float aspect) {
    /** Nothing bright enough on screen to throw a shaft. */
    public static final LightShaftField NONE = new LightShaftField(0.5f, 0.5f, 0f, 1f);

    public boolean active() { return strength > 0; }
}
