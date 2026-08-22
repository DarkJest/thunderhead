package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.Vec3d;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Distance-scaled exposure flash.
 */
public final class ScreenFlashSystem {
    private static final float DECAY_PER_TICK = 0.46f;
    private static final float SECONDARY_TRIGGER = 0.08f;
    private static final float CUTOFF = 0.006f;

    private float primary;
    private float previousPrimary;
    private float secondary;

    public void onStrike(LightningStrikeFxEvent event, Vec3d camera, TempestConfig config) {
        if (!config.camera.screenFlash || config.camera.flashStrength <= 0) return;
        float distanceFactor = (float) FxMath.distanceFalloff(camera.distanceTo(event.position()), 6, 140);
        float reduction = config.general.reducedFlashing ? 0.22f : 1f;
        float target = distanceFactor * config.camera.flashStrength * reduction * event.intensity();
        if (target <= primary) return;
        primary = target;
        previousPrimary = Math.max(previousPrimary, target);
        secondary = config.general.reducedFlashing ? 0 : Math.max(secondary, target * 0.24f);
    }

    public void tick() {
        previousPrimary = primary;
        primary *= DECAY_PER_TICK;
        if (primary < SECONDARY_TRIGGER && secondary > 0) {
            primary = secondary;
            secondary = 0;
        }
        if (primary < CUTOFF) primary = 0;
    }

    public void render(GuiGraphics graphics, TempestConfig config, float partialTick) {
        float intensity = intensity(partialTick);
        if (intensity <= 0 || !config.camera.screenFlash) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centerAlpha = Math.min(215, (int) (intensity * 195));
        int edgeAlpha = Math.min(150, (int) (intensity * 105));
        // Two gradients and nothing else. There used to be a third layer for a hot core, inset from
        // the screen edges, and any centre-weighted layer on top of a flash this bright is a mistake:
        // the base already sits near white, so the extra layer does not read as a brighter middle, it
        // reads as a shape with an edge. Inset rectangle, radial mask, more bands - all the same
        // problem, because the boundary is where the sum stops changing, not where the layer stops.
        graphics.fillGradient(0, 0, width, height / 2, argb(edgeAlpha, 190, 210, 255), argb(centerAlpha, 236, 243, 255));
        graphics.fillGradient(0, height / 2, width, height, argb(centerAlpha, 236, 243, 255), argb(edgeAlpha, 155, 180, 235));
    }

    public float intensity(float partialTick) {
        return FxMath.lerp(previousPrimary, primary, FxMath.clamp(partialTick, 0, 1));
    }

    public void clear() { primary = previousPrimary = secondary = 0; }

    private static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }
}
