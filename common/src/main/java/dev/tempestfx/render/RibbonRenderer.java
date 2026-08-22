package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Emits camera-facing ribbon quads.
 *
 * <p>Segments are never drawn as line primitives. Each one becomes a quad whose width axis points
 * away from the camera-to-segment vector, so the ribbon always presents its full width, and whose
 * {@code v} coordinate runs 0..1 across that width. The shader turns that coordinate into the soft
 * cross-section, which is what makes the channel look volumetric instead of flat.
 */
public final class RibbonRenderer {
    /** Constant column of the profile mask; the mask tapers along u, which we do not want here. */
    private static final float PROFILE_U = 0.5f;
    /** Ceiling on how far a joint may widen; a hairpin would otherwise throw a spike across the screen. */
    private static final double MAX_MITER = 2.9;

    private RibbonRenderer() {}

    /** Generic camera-facing ribbon between two world points. Silently skips degenerate segments. */
    public static void renderRibbon(PoseStack.Pose pose, VertexConsumer consumer,
                                    double sx, double sy, double sz, double ex, double ey, double ez,
                                    double cameraX, double cameraY, double cameraZ,
                                    double startWidth, double endWidth,
                                    float red, float green, float blue, float alpha) {
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > 1.0e-7) || alpha <= 0) return;
        dx /= length; dy /= length; dz /= length;

        double vx = cameraX - (sx + ex) * 0.5;
        double vy = cameraY - (sy + ey) * 0.5;
        double vz = cameraZ - (sz + ez) * 0.5;
        double viewLength = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (viewLength > 1.0e-9) { vx /= viewLength; vy /= viewLength; vz /= viewLength; }

        double sideX = dy * vz - dz * vy;
        double sideY = dz * vx - dx * vz;
        double sideZ = dx * vy - dy * vx;
        double sideLength = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
        if (sideLength < 1.0e-8) {
            // Segment points straight at the camera: any perpendicular reads the same on screen.
            sideX = -dz; sideY = 0; sideZ = dx;
            sideLength = Math.sqrt(sideX * sideX + sideZ * sideZ);
            if (sideLength < 1.0e-8) { sideX = 1; sideY = 0; sideZ = 0; sideLength = 1; }
        }
        sideX /= sideLength; sideY /= sideLength; sideZ /= sideLength;

        vertex(pose, consumer, sx + sideX * startWidth, sy + sideY * startWidth, sz + sideZ * startWidth,
            PROFILE_U, 0f, red, green, blue, alpha);
        vertex(pose, consumer, ex + sideX * endWidth, ey + sideY * endWidth, ez + sideZ * endWidth,
            PROFILE_U, 0f, red, green, blue, alpha);
        vertex(pose, consumer, ex - sideX * endWidth, ey - sideY * endWidth, ez - sideZ * endWidth,
            PROFILE_U, 1f, red, green, blue, alpha);
        vertex(pose, consumer, sx - sideX * startWidth, sy - sideY * startWidth, sz - sideZ * startWidth,
            PROFILE_U, 1f, red, green, blue, alpha);
    }

    /**
     * Ribbon between two points whose side vectors the caller has already decided.
     *
     * <p>This is what makes a chain of segments one ribbon instead of a row of separate quads. Each
     * segment on its own produces a side vector from its own direction, so two segments meeting at an
     * angle do not share an edge and the outer corner of the joint opens by
     * {@code halfWidth * tan(kink / 2)} — invisible on a thin branched bolt, conspicuous on a wide
     * bare trunk. Given the side vector of the shared point instead, both quads put their vertices in
     * exactly the same place and the seam cannot exist.
     *
     * @param sideStart unit side vector at the start, already mitred by the caller
     * @param sideEnd   unit side vector at the end
     */
    public static void renderRibbon(PoseStack.Pose pose, VertexConsumer consumer,
                                    double sx, double sy, double sz, double ex, double ey, double ez,
                                    double[] sideStart, double[] sideEnd,
                                    double startWidth, double endWidth,
                                    float red, float green, float blue, float alpha) {
        if (alpha <= 0) return;
        double sxw = sideStart[0] * startWidth, syw = sideStart[1] * startWidth, szw = sideStart[2] * startWidth;
        double exw = sideEnd[0] * endWidth, eyw = sideEnd[1] * endWidth, ezw = sideEnd[2] * endWidth;
        vertex(pose, consumer, sx + sxw, sy + syw, sz + szw, PROFILE_U, 0f, red, green, blue, alpha);
        vertex(pose, consumer, ex + exw, ey + eyw, ez + ezw, PROFILE_U, 0f, red, green, blue, alpha);
        vertex(pose, consumer, ex - exw, ey - eyw, ez - ezw, PROFILE_U, 1f, red, green, blue, alpha);
        vertex(pose, consumer, sx - sxw, sy - syw, sz - szw, PROFILE_U, 1f, red, green, blue, alpha);
    }

    /**
     * The mitred side vector at a point where two directions meet.
     *
     * <p>Both directions must be unit length and point along the ribbon. Passing the same vector
     * twice gives the ordinary perpendicular, which is what the ends of a run want.
     *
     * @param out receives the unit side vector
     * @return the miter scale: multiply the half-width by it to keep the ribbon's thickness constant
     *     through the joint. Clamped, because a hairpin turn would otherwise throw a spike across the
     *     screen.
     */
    public static double miterSide(double ax, double ay, double az, double bx, double by, double bz,
                                   double viewX, double viewY, double viewZ, double[] out) {
        double dx = ax + bx, dy = ay + by, dz = az + bz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-8) {
            // The two directions cancel: the ribbon doubles back on itself. Use either one.
            dx = ax; dy = ay; dz = az;
            length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0e-8) { out[0] = 1; out[1] = 0; out[2] = 0; return 1; }
        }
        dx /= length; dy /= length; dz /= length;

        double sideX = dy * viewZ - dz * viewY;
        double sideY = dz * viewX - dx * viewZ;
        double sideZ = dx * viewY - dy * viewX;
        double sideLength = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
        if (sideLength < 1.0e-8) {
            // Pointing straight at the camera: any perpendicular reads the same on screen.
            sideX = -dz; sideY = 0; sideZ = dx;
            sideLength = Math.sqrt(sideX * sideX + sideZ * sideZ);
            if (sideLength < 1.0e-8) { out[0] = 1; out[1] = 0; out[2] = 0; return 1; }
        }
        out[0] = sideX / sideLength;
        out[1] = sideY / sideLength;
        out[2] = sideZ / sideLength;

        double cosine = dx * ax + dy * ay + dz * az;
        return cosine > 0.34 ? 1.0 / cosine : MAX_MITER;
    }

    /** Camera-facing square centred on a world point, used for burst and haze billboards. */
    public static void cameraQuad(PoseStack.Pose pose, VertexConsumer consumer, double x, double y, double z,
                                  double cameraX, double cameraY, double cameraZ, double halfSize,
                                  float red, float green, float blue, float alpha) {
        if (alpha <= 0.002f || halfSize <= 0) return;
        double viewX = cameraX - x, viewY = cameraY - y, viewZ = cameraZ - z;
        double viewLength = Math.sqrt(viewX * viewX + viewY * viewY + viewZ * viewZ);
        if (viewLength > 1.0e-9) { viewX /= viewLength; viewY /= viewLength; viewZ /= viewLength; }

        double rightX = -viewZ, rightZ = viewX;
        double rightLength = Math.sqrt(rightX * rightX + rightZ * rightZ);
        if (rightLength < 1.0e-9) { rightX = 1; rightZ = 0; } else { rightX /= rightLength; rightZ /= rightLength; }
        double upX = -rightZ * viewY, upY = rightZ * viewX - rightX * viewZ, upZ = rightX * viewY;
        rightX *= halfSize; rightZ *= halfSize;
        upX *= halfSize; upY *= halfSize; upZ *= halfSize;

        vertex(pose, consumer, x - rightX - upX, y - upY, z - rightZ - upZ, 0f, 0f, red, green, blue, alpha);
        vertex(pose, consumer, x + rightX - upX, y - upY, z + rightZ - upZ, 1f, 0f, red, green, blue, alpha);
        vertex(pose, consumer, x + rightX + upX, y + upY, z + rightZ + upZ, 1f, 1f, red, green, blue, alpha);
        vertex(pose, consumer, x - rightX + upX, y + upY, z - rightZ + upZ, 0f, 1f, red, green, blue, alpha);
    }

    public static void vertex(PoseStack.Pose pose, VertexConsumer consumer, double x, double y, double z,
                              float u, float v, float red, float green, float blue, float alpha) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z).setUv(u, v).setColor(red, green, blue, alpha);
    }

    /** Untextured variant for the solid debris pass, which uses {@code POSITION_COLOR}. */
    public static void plainVertex(PoseStack.Pose pose, VertexConsumer consumer, double x, double y, double z,
                                   float red, float green, float blue, float alpha) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z).setColor(red, green, blue, alpha);
    }
}
