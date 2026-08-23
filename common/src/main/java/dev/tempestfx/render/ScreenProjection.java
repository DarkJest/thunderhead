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
     * World units one pixel covers, per block of distance from the camera.
     *
     * <p>Multiply by a distance and you have the size of a pixel out there. A ribbon narrower than
     * that cannot be rasterised honestly: it either drops out between samples or is widened into
     * something thicker than it should be, and widening is what makes a horizon full of thin branches
     * shimmer as the camera turns.
     *
     * <p>Taken from the projection matrix rather than from the field-of-view option, so it follows a
     * spyglass, a resolution change and any pipeline that hands the frame a projection of its own.
     * {@code m11} of a perspective matrix is {@code 1 / tan(fovY / 2)}.
     *
     * @param screenHeight height of the target being drawn into, in pixels
     */
    public static double pixelWorldScale(Matrix4f projection, int screenHeight) {
        float focal = Math.abs(projection.m11());
        // A perspective matrix built for a player's field of view puts the focal length between
        // roughly 0.6 (a very wide 110 degrees) and 4 (a spyglass). Anything outside that band is not
        // the projection this was written for - a GUI ortho matrix, an identity, a frame in a state
        // the mod was not expecting to be called from - and guessing from it would produce a floor
        // wildly larger than any ribbon, which would fade the whole storm out. Answering "no floor"
        // is always survivable; answering wrongly is not.
        if (!(focal > 0.35f) || focal > 12f || screenHeight <= 0) return 0;
        return 2.0 / (focal * screenHeight);
    }

    /**
     * Aspect ratio of a window, clamped away from zero so a degenerate frame cannot produce a
     * division by zero in a shader.
     */
    public static float aspect(int width, int height) {
        return Math.max(0.1f, width / (float) Math.max(1, height));
    }
}
