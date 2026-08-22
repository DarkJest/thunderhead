package dev.tempestfx.render.composite;

/**
 * Screen-space refraction, expressed analytically rather than as a texture.
 *
 * <p>A wavefront is a circle on screen, so six floats describe it exactly: where its centre is, how
 * wide it has grown, how hard it bends, the window aspect that makes it round, and its phase. The
 * composite shader evaluates the field per pixel from those, which is why the mod needs no
 * screen-sized vector buffer to displace the scene.
 *
 * <p>Values are in normalised screen space, {@code [0,1]} with {@code y} up.
 */
public record DistortionField(float centerX, float centerY, float radius, float strength,
                              float aspect, float phase) {
    /** No refraction; the composite then never reads the scene image at all. */
    public static final DistortionField NONE = new DistortionField(0.5f, 0.5f, 0f, 0f, 1f, 0f);

    /** Whether this field displaces anything, and therefore whether the scene has to be copied. */
    public boolean active() {
        return strength > 0f && radius > 0f;
    }
}
