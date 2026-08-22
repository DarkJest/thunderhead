package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.Vec3d;

/**
 * Extends the client-side sky flash so a close strike really does light the world up.
 *
 * <p>Vanilla already brightens the lightmap and sky colour for two ticks per bolt via
 * {@code ClientLevel#skyFlashTime}; Thunderhead only raises that same transient client value for a
 * few more ticks, scaled by distance. Nothing is written to chunk light data, no relight is
 * scheduled, and the value decays to zero on its own, so the world is untouched afterwards.
 */
public final class WorldFlashSystem {
    private int remaining;

    public void onStrike(LightningStrikeFxEvent event, Vec3d camera, TempestConfig config) {
        if (!config.lighting.worldFlash || config.lighting.worldFlashTicks <= 0) return;
        double factor = FxMath.distanceFalloff(camera.distanceTo(event.position()), 24, 260);
        int ticks = (int) Math.round(config.lighting.worldFlashTicks * factor * event.intensity());
        if (ticks > remaining) remaining = ticks;
    }

    /**
     * Raises the flash directly, without a strike.
     */
    public void pulse(int ticks, TempestConfig config) {
        if (!config.lighting.worldFlash || config.lighting.worldFlashTicks <= 0) return;
        int capped = Math.min(ticks, config.lighting.worldFlashTicks);
        if (capped > remaining) remaining = capped;
    }

    public void tick() { if (remaining > 0) remaining--; }

    /** Sky flash ticks Thunderhead is currently requesting; 0 means "leave vanilla alone". */
    public int flashTicks() { return remaining; }

    public void clear() { remaining = 0; }
}
