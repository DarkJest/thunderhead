package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.LightningLod;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.strike.StrikeAttachment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns the live bolt and shockwave lists.
 *
 * <p>Simulation only: nothing here talks to the renderer. Both lists are hard-bounded by
 * {@code performance.maxConcurrentEffects} and expire deterministically, so a burst of strikes
 * cannot grow memory or frame cost without limit.
 */
public final class EffectManager {
    private final List<ActiveLightningEffect> lightning = new ArrayList<>();
    private final List<ShockwaveEffect> shockwaves = new ArrayList<>();
    private final List<ActiveLightningEffect> lightningView = Collections.unmodifiableList(lightning);
    private final List<ShockwaveEffect> shockwaveView = Collections.unmodifiableList(shockwaves);
    private final LightningEffectFactory factory;

    public EffectManager(LightningEffectFactory factory) { this.factory = factory; }

    public void onStrike(LightningStrikeFxEvent event, Vec3d camera, TempestConfig config) {
        onStrike(event, camera, config, null);
    }

    public void onStrike(LightningStrikeFxEvent event, Vec3d camera, TempestConfig config,
                         StrikeAttachment attachment) {
        double distance = camera.distanceTo(event.position());
        if (distance > config.performance.renderDistance) return;

        LightningLod lod = config.performance.lod ? LightningLod.forDistance(distance) : LightningLod.FULL;
        int limit = config.performance.maxConcurrentEffects;
        while (lightning.size() >= limit) lightning.removeFirst();
        lightning.add(factory.create(event, lod, config, attachment));

        if (config.impact.shockwave && lod != LightningLod.ATMOSPHERIC) {
            while (shockwaves.size() >= limit) shockwaves.removeFirst();
            shockwaves.add(new ShockwaveEffect(event));
        }
    }

    public void tick() {
        for (int index = lightning.size() - 1; index >= 0; index--) {
            ActiveLightningEffect effect = lightning.get(index);
            effect.tick();
            if (!effect.alive()) lightning.remove(index);
        }
        for (int index = shockwaves.size() - 1; index >= 0; index--) {
            ShockwaveEffect effect = shockwaves.get(index);
            effect.tick();
            if (!effect.alive()) shockwaves.remove(index);
        }
    }

    public List<ActiveLightningEffect> lightning() { return lightningView; }

    public List<ShockwaveEffect> shockwaves() { return shockwaveView; }

    public void clear() { lightning.clear(); shockwaves.clear(); }

    public int activeCount() { return lightning.size() + shockwaves.size(); }

    public int activeLightningCount() { return lightning.size(); }
}
