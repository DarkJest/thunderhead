package dev.tempestfx.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderPackProfileTest {
    @Test
    void vanillaRenderingIsUntouched() {
        // The whole point of the profile is that turning it on changes nothing for anybody else.
        ShaderPackProfile full = ShaderPackProfile.of(false);
        assertEquals(1f, full.emissiveScale(), 1e-6);
        assertEquals(1f, full.widthScale(), 1e-6);
        assertEquals(1f, full.minWidthScale(), 1e-6);
        assertTrue(full.drawsWideGlow());
        for (float intensity : new float[] { 0f, 0.22f, 0.5f, 1f }) {
            assertEquals(intensity, full.liftIntensity(intensity), 1e-6);
        }
    }

    @Test
    void aShaderPackGetsBrighterWiderAndFewerPasses() {
        ShaderPackProfile pack = ShaderPackProfile.of(true);
        assertTrue(pack.emissiveScale() > 1f);
        assertTrue(pack.widthScale() > 1f);
        assertTrue(pack.minWidthScale() > 1f);
        assertFalse(pack.drawsWideGlow(), "the wide quads are what showed as discs under a pack");
    }

    @Test
    void theIntensityLiftRescuesBranchesWithoutFlatteningTheTrunk() {
        ShaderPackProfile pack = ShaderPackProfile.of(true);
        // A fourth-level fork carries about a fifth of the trunk's intensity.
        float twig = pack.liftIntensity(0.22f);
        float trunk = pack.liftIntensity(1f);

        assertTrue(twig > 0.22f * 2, "a twig has to gain enough to survive a tonemapper: " + twig);
        assertEquals(1f, trunk, 1e-6, "the trunk is already at full and must not be pushed past it");
        assertTrue(twig < trunk, "the hierarchy has to survive the lift");
        assertTrue(pack.liftIntensity(0.5f) < pack.liftIntensity(0.8f), "ordering is preserved");
    }
}
