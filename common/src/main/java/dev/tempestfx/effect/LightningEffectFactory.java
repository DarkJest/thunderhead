package dev.tempestfx.effect;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.DischargeProfile;
import dev.tempestfx.lightning.DischargeProfiles;
import dev.tempestfx.lightning.LightningBolt;
import dev.tempestfx.lightning.LightningGenerationConfig;
import dev.tempestfx.lightning.LightningGeometryStrategy;
import dev.tempestfx.lightning.LightningLod;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.strike.StrikeAttachment;

/** Turns a strike event plus user settings into ready-to-render bolt geometry. */
public final class LightningEffectFactory {
    private static final double CLOUD_HEIGHT = 132;
    private static final double CLOUD_HEIGHT_VARIANCE = 58;
    /** Horizontal lean of the channel as a fraction of its height, giving the bolt a natural slant. */
    private static final double LEAN_RATIO = 0.26;
    /** Channel height the tuned displacement amplitude was authored against. */
    private static final double REFERENCE_HEIGHT = 110;
    /** How much taller a positive channel hangs; part of what makes it read as a different event. */
    private static final double POSITIVE_HEIGHT_SCALE = 1.35;

    private final LightningGeometryStrategy geometryStrategy;

    public LightningEffectFactory(LightningGeometryStrategy geometryStrategy) {
        this.geometryStrategy = geometryStrategy;
    }

    public ActiveLightningEffect create(LightningStrikeFxEvent event, LightningLod lod, TempestConfig config) {
        return create(event, lod, config, null);
    }

    /**
     * @param attachment where an upward streamer met the leader, or {@code null} to end the channel
     *                   at the event's own position the way every earlier release did
     */
    public ActiveLightningEffect create(LightningStrikeFxEvent event, LightningLod lod, TempestConfig config,
                                        StrikeAttachment attachment) {
        long seed = event.seed();
        DischargeProfile profile = DischargeProfiles.of(event.dischargeType());
        // A player who does not want the channel to build has it revealed the way it always was.
        if (!config.lightning.steppedLeader) profile = profile.withEnvelope(profile.envelope().withoutSteps());
        LightningLook look = LightningLook.resolve(config, event.style());
        float scale = look.scale();
        // An explicit origin is the caller stating the bolt's angle and length outright; without one
        // the channel hangs from the cloud base with a seeded lean, which is every strike the mod
        // raises itself. Displacement is then scaled by the channel that actually exists, not by the
        // one that would have been derived, or a short slanted bolt wanders like a tall one.
        Vec3d start = event.origin() != null ? event.origin()
            : derivedOrigin(event, seed, lod, scale, profile.type());
        // The channel ends where something reached up and met it, which is a rod's tip rather than
        // the ground beside it whenever a rod is involved.
        Vec3d end = attachment != null ? attachment.point() : event.position();
        double height = Math.max(1, start.distanceTo(end));

        LightningGenerationConfig base = LightningGenerationConfig.high();
        double probability = Math.min(0.75, base.branchProbability() * look.branchCount() / 18.0);
        LightningGenerationConfig selected = profile
            .geometry(base
                .withGenerations(config.lightning.geometryQuality)
                .withBranchProbability(probability)
                // Amplitude is authored for a reference height; a taller channel needs a wider wander
                // or it reads as a straight wire stretched across the sky.
                .withDisplacement(base.displacement() * height / REFERENCE_HEIGHT),
                config.lightning.skySpread)
            .forLod(lod);

        LightningBolt bolt = LightningBolt.builder()
            .start(start)
            .end(end)
            .seed(seed)
            .intensity(event.intensity())
            .config(selected)
            .build();
        return new ActiveLightningEffect(event, geometryStrategy.generate(bolt), lod, profile, attachment);
    }

    /** Where a bolt leaves the cloud when the caller did not say: up, and leaning by its seed. */
    private static Vec3d derivedOrigin(LightningStrikeFxEvent event, long seed, LightningLod lod, float scale,
                                       DischargeType type) {
        // A positive flash comes out of the anvil rather than the cloud base: a longer channel, and a
        // steeper one, which is part of why it can land well away from the storm it belongs to.
        double typeScale = type == DischargeType.POSITIVE_CLOUD_TO_GROUND ? POSITIVE_HEIGHT_SCALE : 1.0;
        double height = (lod == LightningLod.ATMOSPHERIC
            ? CLOUD_HEIGHT * 0.75
            : CLOUD_HEIGHT + StrikeSeed.unit(seed, 0x01) * CLOUD_HEIGHT_VARIANCE) * scale * typeScale;
        double lean = height * LEAN_RATIO;
        return event.position().add(
            StrikeSeed.signed(seed, 0x02) * lean, height, StrikeSeed.signed(seed, 0x03) * lean);
    }
}
