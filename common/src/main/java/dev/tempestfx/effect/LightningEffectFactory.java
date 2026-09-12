package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.LightningBolt;
import dev.tempestfx.lightning.LightningGenerationConfig;
import dev.tempestfx.lightning.LightningGeometryStrategy;
import dev.tempestfx.lightning.LightningLod;
import dev.tempestfx.lightning.FlashTimeline;
import dev.tempestfx.lightning.DischargeGeometryStrategy;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;

/** Turns a strike event plus user settings into ready-to-render bolt geometry. */
public final class LightningEffectFactory {
    private static final double CLOUD_HEIGHT = 132;
    private static final double CLOUD_HEIGHT_VARIANCE = 58;
    /** Horizontal lean of the channel as a fraction of its height, giving the bolt a natural slant. */
    private static final double LEAN_RATIO = 0.26;
    /** Channel height the tuned displacement amplitude was authored against. */
    private static final double REFERENCE_HEIGHT = 110;

    private final LightningGeometryStrategy geometryStrategy;
    private final DischargeGeometryStrategy dischargeGeometry = new DischargeGeometryStrategy();

    public LightningEffectFactory(LightningGeometryStrategy geometryStrategy) {
        this.geometryStrategy = geometryStrategy;
    }

    public ActiveLightningEffect create(LightningStrikeFxEvent event, LightningLod lod, TempestConfig config) {
        return create(event, lod, config, FlashTimeline.plan(event.seed(), config.effectiveReturnStrokes(), config.realistic(), event.kind()));
    }

    public ActiveLightningEffect create(LightningStrikeFxEvent event, LightningLod lod, TempestConfig config, FlashTimeline timeline) {
        long seed = event.seed();
        LightningLook look = LightningLook.resolve(config, event.style());
        float scale = look.scale();
        // An explicit origin is the caller stating the bolt's angle and length outright; without one
        // the channel hangs from the cloud base with a seeded lean, which is every strike the mod
        // raises itself. Displacement is then scaled by the channel that actually exists, not by the
        // one that would have been derived, or a short slanted bolt wanders like a tall one.
        Vec3d start = originFor(event, lod, config);
        double height = Math.max(1, start.distanceTo(event.position()));

        LightningGenerationConfig base = LightningGenerationConfig.high();
        double probability = Math.min(0.75, base.branchProbability() * look.branchCount() / 18.0);
        LightningGenerationConfig selected = base
            .withGenerations(config.lightning.geometryQuality)
            .withBranchProbability(probability)
            // Amplitude is authored for a reference height; a taller channel needs a wider wander
            // or it reads as a straight wire stretched across the sky.
            .withDisplacement(base.displacement() * height / REFERENCE_HEIGHT)
            .withSkySpread(config.lightning.skySpread)
            .forLod(lod);

        LightningBolt bolt = LightningBolt.builder()
            .start(start)
            .end(event.position())
            .seed(seed)
            .intensity(1f)
            .config(selected)
            .build();
        return new ActiveLightningEffect(event, config.realistic()
            ? dischargeGeometry.generate(bolt, event.kind(), lod) : geometryStrategy.generate(bolt), lod, timeline);
    }

    public Vec3d originFor(LightningStrikeFxEvent event, LightningLod lod, TempestConfig config) {
        float scale = LightningLook.resolve(config, event.style()).scale();
        return event.origin() != null ? event.origin() : config.realistic() || !event.kind().contactsGround()
            ? realisticOrigin(event, config, event.seed(), scale) : derivedOrigin(event, event.seed(), lod, scale);
    }

    private static Vec3d realisticOrigin(LightningStrikeFxEvent event, TempestConfig config, long seed, float scale) {
        if (!event.kind().contactsGround()) {
            double span = event.kind() == dev.tempestfx.api.LightningKind.INTERCLOUD ? 180 : 85;
            return event.position().add(-span * scale, 12 * StrikeSeed.signed(seed, 13), 35 * scale);
        }
        double y = Math.max(config.lightning.cloudBaseY, event.position().y() + 32);
        double height = (y - event.position().y()) * scale;
        return event.position().add(StrikeSeed.signed(seed, 11) * height * .25, height,
            StrikeSeed.signed(seed, 12) * height * .25);
    }

    /** Where a bolt leaves the cloud when the caller did not say: up, and leaning by its seed. */
    private static Vec3d derivedOrigin(LightningStrikeFxEvent event, long seed, LightningLod lod, float scale) {
        double height = (lod == LightningLod.ATMOSPHERIC
            ? CLOUD_HEIGHT * 0.75
            : CLOUD_HEIGHT + StrikeSeed.unit(seed, 0x01) * CLOUD_HEIGHT_VARIANCE) * scale;
        double lean = height * LEAN_RATIO;
        return event.position().add(
            StrikeSeed.signed(seed, 0x02) * lean, height, StrikeSeed.signed(seed, 0x03) * lean);
    }
}
