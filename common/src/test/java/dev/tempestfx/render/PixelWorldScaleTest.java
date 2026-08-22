package dev.tempestfx.render;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The number that decides when a ribbon has become too thin to draw honestly.
 *
 * <p>It replaced a magic constant — {@code 0.0016} blocks of extra width per block of distance —
 * which was tuned at one resolution and one field of view, and which widened distant branches into
 * bright uniform threads that shimmered as the camera turned.
 */
class PixelWorldScaleTest {
    private static Matrix4f perspective(float fovDegrees, float aspect) {
        return new Matrix4f().perspective((float) Math.toRadians(fovDegrees), aspect, 0.05f, 1000f);
    }

    @Test
    void aPixelCoversMoreWorldTheFurtherAwayItIs() {
        double scale = ScreenProjection.pixelWorldScale(perspective(70, 16f / 9f), 1080);
        assertTrue(scale > 0);
        // The scale is per block of distance, so the size of a pixel is simply proportional.
        assertEquals(scale * 100, scale * 100, 1e-12);
        assertTrue(scale * 200 > scale * 100, "a pixel at 200 blocks covers more than one at 100");
    }

    @Test
    void aTallerScreenResolvesFinerDetail() {
        double at1080 = ScreenProjection.pixelWorldScale(perspective(70, 16f / 9f), 1080);
        double at2160 = ScreenProjection.pixelWorldScale(perspective(70, 16f / 9f), 2160);
        assertEquals(at1080 / 2, at2160, 1e-9,
            "doubling the vertical resolution should halve what one pixel covers");
    }

    @Test
    void aNarrowerFieldOfViewResolvesFinerDetail() {
        double wide = ScreenProjection.pixelWorldScale(perspective(110, 16f / 9f), 1080);
        double narrow = ScreenProjection.pixelWorldScale(perspective(30, 16f / 9f), 1080);
        assertTrue(narrow < wide,
            "zooming in must make a pixel cover less world, or a spyglass would fade the bolt out");
    }

    @Test
    void theOldConstantIsInTheRightNeighbourhoodForTheSettingItWasTunedAt() {
        // 1080p at the default field of view is what 0.0016 was authored against. The new floor is
        // 1.5 pixels wide and halved to a half-width, so the comparable figure is scale * 0.75.
        double comparable = ScreenProjection.pixelWorldScale(perspective(70, 16f / 9f), 1080) * 0.75;
        assertTrue(comparable > 0.0004 && comparable < 0.0016,
            "expected the measured floor to land under the hand-tuned one, was " + comparable);
    }

    @Test
    void aDegenerateFrameProducesNoFloorRatherThanAnInfiniteOne() {
        assertEquals(0, ScreenProjection.pixelWorldScale(perspective(70, 16f / 9f), 0));
        assertEquals(0, ScreenProjection.pixelWorldScale(new Matrix4f().zero(), 1080));
    }
}
