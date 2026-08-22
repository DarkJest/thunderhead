package dev.tempestfx.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenProjectionTest {
    /** A plain perspective projection, the same shape the game builds for the world. */
    private static Matrix4f perspective() {
        return new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9f, 0.05f, 1000f);
    }

    @Test
    void aPointDeadAheadLandsInTheMiddleOfTheScreen() {
        float[] out = new float[2];
        // OpenGL view space looks down -z, so a point in front of the camera has a negative z.
        assertTrue(ScreenProjection.toScreen(perspective(), new Vector4f(), 0f, 0f, -20f, 1f, out));
        assertEquals(0.5f, out[0], 1e-5);
        assertEquals(0.5f, out[1], 1e-5);
    }

    @Test
    void screenSpaceRunsRightAndUpFromTheBottomLeft() {
        float[] right = new float[2];
        float[] up = new float[2];
        ScreenProjection.toScreen(perspective(), new Vector4f(), 4f, 0f, -20f, 1f, right);
        ScreenProjection.toScreen(perspective(), new Vector4f(), 0f, 4f, -20f, 1f, up);

        assertTrue(right[0] > 0.5f, "positive x has to move right: " + right[0]);
        assertEquals(0.5f, right[1], 1e-5);
        assertTrue(up[1] > 0.5f, "positive y has to move up, which is what the shader assumes: " + up[1]);
        assertEquals(0.5f, up[0], 1e-5);
    }

    @Test
    void aPointBehindTheCameraIsRejectedRatherThanMirrored() {
        float[] out = new float[2];
        // Without the w test this projects to a plausible-looking point on the opposite side, which is
        // exactly how a distortion centre ends up refracting the wrong half of the screen.
        assertFalse(ScreenProjection.toScreen(perspective(), new Vector4f(), 0f, 0f, 20f, 1f, out));
        assertFalse(ScreenProjection.toScreen(perspective(), new Vector4f(), 0f, 0f, 0f, 1f, out));
    }

    @Test
    void aspectIsClampedAwayFromZero() {
        assertEquals(16f / 9f, ScreenProjection.aspect(1600, 900), 1e-5);
        assertTrue(ScreenProjection.aspect(0, 0) > 0f, "a degenerate window must not divide by zero");
        assertTrue(ScreenProjection.aspect(1, 100000) > 0f);
    }
}
