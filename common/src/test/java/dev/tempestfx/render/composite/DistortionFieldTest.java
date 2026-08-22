package dev.tempestfx.render.composite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistortionFieldTest {
    @Test
    void nothingIsRefractedByDefault() {
        // The scene copy is the only expensive part of the composite, and this is the flag that
        // decides whether it happens at all.
        assertFalse(DistortionField.NONE.active());
    }

    @Test
    void aFieldNeedsBothWidthAndStrengthToCostAnything() {
        assertTrue(new DistortionField(0.5f, 0.5f, 0.2f, 1f, 1.77f, 3f).active());
        assertFalse(new DistortionField(0.5f, 0.5f, 0.2f, 0f, 1.77f, 3f).active());
        assertFalse(new DistortionField(0.5f, 0.5f, 0f, 1f, 1.77f, 3f).active());
    }
}
