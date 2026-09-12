package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.LightningLod;
import dev.tempestfx.lightning.FlashTimeline;
import dev.tempestfx.math.Vec3d;
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
    private ActiveLightningEffect latest;
    public ActiveLightningEffect latest() { return latest; }

    public EffectManager(LightningEffectFactory factory) { this.factory = factory; }

    public void onStrike(LightningStrikeFxEvent event, Vec3d camera, TempestConfig config) {
        onFlash(event, camera, config, FlashTimeline.plan(event.seed(), config.effectiveReturnStrokes(), config.realistic(), event.kind()));
    }

    public void onFlash(LightningStrikeFxEvent event, Vec3d camera, TempestConfig config, FlashTimeline timeline) {
        onFlash(event, camera, config, timeline, 0);
    }
    public void onFlash(LightningStrikeFxEvent event, Vec3d camera, TempestConfig config, FlashTimeline timeline, int age) {
        onFlash(event, camera, config, timeline, age, true);
    }
    public void onFlash(LightningStrikeFxEvent event, Vec3d camera, TempestConfig config, FlashTimeline timeline, int age, boolean visible) {
        double distance = camera.distanceTo(event.position());
        latest = null;
        // A far endpoint is not a far channel. Explicit origins may span the listener's range.
        // The generous precheck includes possible forks; exact generated bounds refine it below.
        var origin = factory.originFor(event, LightningLod.FULL, config);
        var envelope = dev.tempestfx.math.Bounds3d.empty().include(origin).include(event.position());
        double reach = origin.distanceTo(event.position()) * 4 + 64;
        if (envelope.distanceTo(camera) - reach > Math.max(config.performance.renderDistance, config.audio.maxThunderDistance)) return;
        distance = envelope.distanceTo(camera);

        LightningLod lod = config.performance.lod ? LightningLod.forDistance(distance) : LightningLod.FULL;
        int limit = config.performance.maxConcurrentEffects;
        latest = factory.create(event, lod, config, timeline);
        distance = latest.geometry().bounds().distanceTo(camera);
        if (distance > Math.max(config.performance.renderDistance, config.audio.maxThunderDistance)) { latest = null; return; }
        if (age > 0) latest.seek(age);
        if (visible && distance <= config.performance.renderDistance && latest.alive()) {
            while (lightning.size() >= limit) lightning.removeFirst();
            lightning.add(latest);
        }
    }

    public void onContact(LightningStrikeFxEvent event, Vec3d camera, TempestConfig config) {
        if (!event.kind().contactsGround()) return;
        if (config.impact.shockwave && !config.realistic() && camera.distanceTo(event.position()) < Math.min(256, config.performance.renderDistance)) {
            int limit = config.performance.maxConcurrentEffects;
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

    public void clear() { lightning.clear(); shockwaves.clear(); latest = null; }

    public int activeCount() { return lightning.size() + shockwaves.size(); }

    public int activeLightningCount() { return lightning.size(); }
}
