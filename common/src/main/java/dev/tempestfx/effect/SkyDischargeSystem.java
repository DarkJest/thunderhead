package dev.tempestfx.effect;

import dev.tempestfx.api.LightningEnvironment;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.api.StrikeOptions;
import dev.tempestfx.api.StrikeTarget;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.AerialChannelStrategy;
import dev.tempestfx.lightning.DischargeProfile;
import dev.tempestfx.lightning.DischargeProfiles;
import dev.tempestfx.lightning.LightningBolt;
import dev.tempestfx.lightning.LightningGenerationConfig;
import dev.tempestfx.lightning.LightningGeometryStrategy;
import dev.tempestfx.lightning.LightningLod;
import dev.tempestfx.storm.AmbientDischarge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The discharges that never reach the ground: cloud-to-cloud, intracloud and the rare megaflash.
 *
 * <p>Separate from {@link EffectManager} on purpose. A ground strike has an impact, a target, a
 * surface and gameplay behind it; these have none of that, and giving them their own list keeps the
 * bolt path free of "is this one real" checks and lets the two be budgeted independently.
 *
 * <p>Geometry is generated once here and read by the renderer and the thunder scheduler alike.
 */
public final class SkyDischargeSystem {
    /** Segment budget per archetype. A megaflash is allowed more because it is far larger. */
    private static final int INTRACLOUD_SEGMENTS = 320;
    private static final int CLOUD_TO_CLOUD_SEGMENTS = 900;
    private static final int MEGAFLASH_SEGMENTS = 1900;
    /** Wander amplitude per block of channel; a horizontal run wanders less than a falling one. */
    private static final double DISPLACEMENT_PER_BLOCK = 0.055;

    private final LightningGeometryStrategy geometry;
    private final List<ActiveLightningEffect> active = new ArrayList<>();
    private final List<ActiveLightningEffect> view = Collections.unmodifiableList(active);

    public SkyDischargeSystem() {
        this(new AerialChannelStrategy());
    }

    public SkyDischargeSystem(LightningGeometryStrategy geometry) {
        this.geometry = geometry;
    }

    /**
     * Builds the channel for a planned discharge.
     *
     * @return the effect created, or {@code null} when the feature is off or the budget is full
     */
    public ActiveLightningEffect onDischarge(AmbientDischarge discharge, TempestConfig config) {
        if (!config.sky.skyActivity) return null;
        int limit = config.sky.maxAmbientDischarges;
        if (limit <= 0) return null;
        while (active.size() >= limit) active.removeFirst();

        DischargeProfile profile = DischargeProfiles.of(discharge.type());
        double span = discharge.span();
        LightningGenerationConfig base = LightningGenerationConfig.high()
            .withGenerations(Math.min(7, config.lightning.geometryQuality))
            .withDisplacement(Math.max(1.5, span * DISPLACEMENT_PER_BLOCK))
            .withMaxSegments(segmentBudget(discharge));
        // Canopy is a ground bolt's trick - a web hanging off the cloud base above the strike - and a
        // horizontal channel already lives up there, so it is switched off rather than scaled.
        LightningGenerationConfig selected = profile.geometry(base, 0);

        LightningBolt bolt = LightningBolt.builder()
            .start(discharge.origin())
            .end(discharge.target())
            .seed(discharge.seed())
            .intensity(discharge.energy())
            .config(selected)
            .build();

        // A synthetic event, the way the distant-bolt wall builds one: no impact, no surface, no
        // target, so nothing downstream can mistake an aerial discharge for a strike.
        LightningStrikeFxEvent event = new LightningStrikeFxEvent(discharge.midpoint(), discharge.seed(),
            discharge.energy(),
            new LightningEnvironment(LightningEnvironment.Type.LAND, 0x8a8a8a, true, 0f, Double.NaN, false, 1f),
            StrikeTarget.none(), 0, StrikeOptions.builder().type(discharge.type()).build());

        ActiveLightningEffect effect =
            new ActiveLightningEffect(event, geometry.generate(bolt), LightningLod.DISTANT, profile);
        active.add(effect);
        return effect;
    }

    private static int segmentBudget(AmbientDischarge discharge) {
        return switch (discharge.type()) {
            case INTRACLOUD -> INTRACLOUD_SEGMENTS;
            case MEGAFLASH -> MEGAFLASH_SEGMENTS;
            default -> CLOUD_TO_CLOUD_SEGMENTS;
        };
    }

    public void tick() {
        for (int index = active.size() - 1; index >= 0; index--) {
            ActiveLightningEffect effect = active.get(index);
            effect.tick();
            if (!effect.alive()) active.remove(index);
        }
    }

    public List<ActiveLightningEffect> discharges() { return view; }

    public int activeCount() { return active.size(); }

    public void clear() { active.clear(); }
}
