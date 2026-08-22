package dev.tempestfx.compat;

import dev.tempestfx.config.BloomMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomBackendFactoryTest {
    @Test
    void autoAvoidsThirdPartyFramebufferOwnership() {
        assertFalse(BloomBackendFactory.create(BloomMode.AUTO, RenderCompatibilityMode.IRIS).isAvailable());
        assertFalse(BloomBackendFactory.create(BloomMode.AUTO, RenderCompatibilityMode.OPTIFINE).isAvailable());
        assertFalse(BloomBackendFactory.create(BloomMode.AUTO, RenderCompatibilityMode.UNKNOWN_SHADER_PIPELINE).isAvailable());
        assertTrue(BloomBackendFactory.create(BloomMode.AUTO, RenderCompatibilityMode.VANILLA).isAvailable());
    }

    @Test
    void offAlwaysDisablesBackend() {
        assertFalse(BloomBackendFactory.create(BloomMode.OFF, RenderCompatibilityMode.VANILLA).isAvailable());
    }

    @Test
    void unbalancedLifecycleNeverThrowsOutOfARenderCallback() {
        BloomBackend backend = BloomBackendFactory.create(BloomMode.COMPATIBILITY, RenderCompatibilityMode.VANILLA);
        backend.begin();
        backend.begin();
        backend.end();
        backend.end();
        assertEquals(1f, backend.emissiveBoost(), 1e-6);
    }

    @Test
    void activeBackendBoostsTheAdditiveLayersOnly() {
        BloomBackend safe = BloomBackendFactory.create(BloomMode.COMPATIBILITY, RenderCompatibilityMode.VANILLA);
        assertEquals(1f, safe.emissiveBoost(), 1e-6);
        safe.begin();
        assertTrue(safe.emissiveBoost() > 1f);
        safe.end();

        BloomBackend disabled = BloomBackendFactory.create(BloomMode.OFF, RenderCompatibilityMode.VANILLA);
        disabled.begin();
        assertEquals(1f, disabled.emissiveBoost(), 1e-6);
    }
}
