package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.lightning.DischargeProfile;
import dev.tempestfx.lightning.DischargeProfiles;
import dev.tempestfx.lightning.LightningEnvelope;
import dev.tempestfx.lightning.LightningGeometry;
import dev.tempestfx.lightning.LightningLod;
import dev.tempestfx.lightning.LightningSegment;
import java.util.List;

/**
 * Live state of one bolt: fixed geometry plus a continuous time envelope.
 *
 * <p>Geometry is generated once and never rebuilt. Only brightness, visible extent and branch masks
 * change over time, all as continuous functions of {@code age + partialTick}.
 *
 * <p>The discharge profile travels with the effect so the renderer and the audio read the same
 * archetype the geometry was built from, rather than each deciding again.
 */
public final class ActiveLightningEffect {
    private final LightningStrikeFxEvent event;
    private final LightningGeometry geometry;
    private final LightningEnvelope envelope;
    private final LightningLod lod;
    private final DischargeProfile profile;
    private int age;

    public ActiveLightningEffect(LightningStrikeFxEvent event, LightningGeometry geometry, LightningLod lod) {
        this(event, geometry, lod, DischargeProfiles.negativeCloudToGround());
    }

    public ActiveLightningEffect(LightningStrikeFxEvent event, LightningGeometry geometry, LightningLod lod,
                                 DischargeProfile profile) {
        this.event = event;
        this.geometry = geometry;
        this.lod = lod;
        this.profile = profile;
        this.envelope = new LightningEnvelope(event.seed(), profile.envelope());
    }

    public void tick() { age++; }

    public boolean alive() { return age < envelope.duration(); }

    public float time(float partialTick) { return age + partialTick; }

    public float brightness(float partialTick, boolean flicker, boolean reducedFlashing) {
        return envelope.brightness(time(partialTick), flicker, reducedFlashing)
            * event.intensity() * profile.energyScale();
    }

    public float propagation(float partialTick) { return envelope.propagation(time(partialTick)); }

    public float impactFlash(float partialTick) { return envelope.impactFlash(time(partialTick)); }

    public boolean segmentVisible(LightningSegment segment, float partialTick) {
        float time = time(partialTick);
        if (segment.alongStart() > envelope.propagation(time)) return false;
        return segment.branchDepth() == 0 || envelope.branchVisible(segment.visibilityMask(), time);
    }

    public LightningStrikeFxEvent event() { return event; }

    public LightningGeometry geometry() { return geometry; }

    public List<LightningSegment> segments() { return geometry.segments(); }

    public LightningLod lod() { return lod; }

    public DischargeProfile profile() { return profile; }

    public int age() { return age; }
}
