package dev.tempestfx.render.composite;

import dev.tempestfx.render.gl.FxPrograms;

/**
 * Chooses a compositor. The only place in the mod that names a concrete implementation.
 */
public final class EffectCompositors {
    private EffectCompositors() {
    }

    /**
     * @param isolate whether the effect should be drawn into a framebuffer of its own
     * @param programs the mod's own programs; isolation is pointless without the composite one
     */
    public static EffectCompositor create(boolean isolate, FxPrograms programs) {
        return isolate ? new FramebufferEffectCompositor(programs) : new DirectEffectCompositor();
    }
}
