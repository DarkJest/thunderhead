package dev.tempestfx.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * World space to screen space, as pure functions.
 *
 * <p>Kept apart from the systems that use it so the arithmetic can be tested without a window, a
 * context or a frame in progress; it is also the only place where the sign and range conventions of
 * the mod's screen-space uniforms are decided.
 */
public final class ScreenProjection {
    private ScreenProjection() {
    }

    /**
     * Projects a point already in view space.
     *
     * @param scratch reusable vector, so a per-frame call allocates nothing
     * @return {@code true} when the point is in front of the camera, with {@code out} filled with
     *     normalised screen coordinates in {@code [0,1]}, {@code y} up
     */
    public static boolean toScreen(Matrix4f projection, Vector4f scratch,
                                   float viewX, float viewY, float viewZ, float viewW, float[] out) {
        scratch.set(viewX, viewY, viewZ, viewW);
        projection.transform(scratch);
        if (scratch.w() <= 1.0e-4f) return false;
        out[0] = scratch.x() / scratch.w() * 0.5f + 0.5f;
        out[1] = scratch.y() / scratch.w() * 0.5f + 0.5f;
        return true;
    }

    /**
     * Aspect ratio of a window, clamped away from zero so a degenerate frame cannot produce a
     * division by zero in a shader.
     */
    public static float aspect(int width, int height) {
        return Math.max(0.1f, width / (float) Math.max(1, height));
    }
}
