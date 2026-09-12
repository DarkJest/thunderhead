package dev.tempestfx.effect;

import dev.tempestfx.api.LightningEnvironment;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.audio.DistantBoltCue;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.LightningBolt;
import dev.tempestfx.lightning.LightningGenerationConfig;
import dev.tempestfx.lightning.LightningGeometryStrategy;
import dev.tempestfx.lightning.LightningLod;
import dev.tempestfx.lightning.MidpointDisplacementStrategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The wall of distant lightning that a rolling thunder event puts across the sky.
 */
public final class DistantBoltSystem {
    /** Ceiling on channels alive at once. At the top rate roughly half this many are live. */
    private static final int MAX_ACTIVE = 56;
    /** Enough tree for the dense forking these need, still bounded for dozens at once. */
    private static final int SEGMENT_BUDGET = 760;

    private final LightningGeometryStrategy geometry = new MidpointDisplacementStrategy();
    private final List<ActiveLightningEffect> bolts = new ArrayList<>();
    private final List<ActiveLightningEffect> view = Collections.unmodifiableList(bolts);

    /** @return the channel created, or {@code null} when the feature is off or the cap is reached. */
    public ActiveLightningEffect onCue(DistantBoltCue cue, TempestConfig config) {
        if (!config.lighting.distantBolts) return null;
        if (bolts.size() >= MAX_ACTIVE) bolts.removeFirst();

        // Heavy forking on purpose. These read as a storm front, and a front is mostly branches:
        // the reference for this is a photograph full of trees of lightning, not bare strands.
        // No LOD pass either - it exists to thin out distant detail, which is the one thing these
        // must not lose. No canopy: that is the main bolt's trick and a horizon of them would smear.
        LightningGenerationConfig base = LightningGenerationConfig.high();
        LightningGenerationConfig capped = new LightningGenerationConfig(
            6,
            Math.max(2.5, cue.height() * 0.05),
            base.roughness(),
            0.82,
            base.directionBias(),
            base.branchAngleRadians(),
            0.42,
            base.branchDecay(),
            base.branchJitter(),
            3,
            0.4,
            0,
            0,
            SEGMENT_BUDGET);

        LightningBolt bolt = LightningBolt.builder()
            .start(cue.top())
            .end(cue.ground())
            .seed(cue.seed())
            .intensity(cue.intensity())
            .config(capped)
            .build();

        // A synthetic event: distant channels have no impact, no surface and no target, so nothing
        // downstream can mistake one for a strike.
        LightningStrikeFxEvent event = new LightningStrikeFxEvent(cue.ground(), cue.seed(), cue.intensity(),
            new LightningEnvironment(LightningEnvironment.Type.LAND, 0x8a8a8a, true, 0f, Double.NaN, false, 1f));
        ActiveLightningEffect effect = new ActiveLightningEffect(event, geometry.generate(bolt), LightningLod.DISTANT);
        bolts.add(effect);
        return effect;
    }

    public void tick() {
        for (int index = bolts.size() - 1; index >= 0; index--) {
            ActiveLightningEffect effect = bolts.get(index);
            effect.tick();
            if (!effect.alive()) bolts.remove(index);
        }
    }

    public List<ActiveLightningEffect> bolts() { return view; }

    public int activeCount() { return bolts.size(); }

    public void clear() { bolts.clear(); }
}
