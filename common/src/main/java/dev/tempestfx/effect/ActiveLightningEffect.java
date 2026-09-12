package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.lightning.FlashTimeline;
import dev.tempestfx.lightning.LightningGeometry;
import dev.tempestfx.lightning.LightningLod;
import dev.tempestfx.lightning.LightningSegment;
import java.util.List;

/**
 * Live state of one bolt: fixed geometry plus a continuous time envelope.
 *
 * <p>Geometry is generated once and never rebuilt. Only brightness, visible extent and branch masks
 * change over time, all as continuous functions of {@code age + partialTick}.
 */
public final class ActiveLightningEffect {
    private final LightningStrikeFxEvent event;
    private final LightningGeometry geometry;
    private final FlashTimeline timeline;
    private final LightningLod lod;
    private int age;
    private float frameTicks = 1f / 3f;
    private float lastSample = Float.NaN;
    private final float returnTravelTicks;

    public ActiveLightningEffect(LightningStrikeFxEvent event, LightningGeometry geometry, LightningLod lod) {
        this(event, geometry, lod, FlashTimeline.plan(event.seed(), 0, false));
    }

    public ActiveLightningEffect(LightningStrikeFxEvent event, LightningGeometry geometry, LightningLod lod,
                                 FlashTimeline timeline) {
        this.event = event;
        this.geometry = geometry;
        this.lod = lod;
        this.timeline = timeline;
        double length = 0;
        for (LightningSegment segment : geometry.segments()) if (segment.branchDepth() == 0) length += segment.length();
        // One block = one metre, return front 100 million m/s. Too fast to animate as a slow beam;
        // the exposure integral retains its direction even when the whole front fits inside a frame.
        this.returnTravelTicks = event.kind().contactsGround() ? (float) (length / 100_000_000.0 * 20) : 0;
    }

    public void tick() { age++; }
    public void seek(int ticks) { age = Math.max(0, ticks); lastSample = age; }

    public boolean alive() { return age < Math.max(7, timeline.durationTicks()); }

    public float time(float partialTick) { return age + partialTick; }

    public float brightness(float partialTick, boolean flicker, boolean reducedFlashing) {
        float t = time(partialTick);
        return (reducedFlashing ? Math.max(0, 1 - t / 6) * .5f
            : timeline.average(t - frameTicks, t)) * event.intensity();
    }

    public float propagation(float partialTick) { return timeline.propagation(time(partialTick)); }

    public float impactFlash(float partialTick) { return timeline.value(time(partialTick)); }

    public FlashTimeline timeline() { return timeline; }
    public void frameInterval(float ticks) { frameTicks = Math.max(.001f, Math.min(4f, ticks)); }

    public void prepareFrame(float partialTick, float fallbackTicks) {
        float sample = time(partialTick);
        float from = Float.isNaN(lastSample) ? 0 : lastSample;
        if (sample > from) frameTicks = sample - from;
        else frameInterval(fallbackTicks);
        lastSample = sample;
    }

    public float segmentBrightness(LightningSegment segment, float partialTick, boolean reducedFlashing) {
        if (reducedFlashing) return brightness(partialTick, false, true);
        float shift = (float) ((1 - Math.max(0, Math.min(1, segment.alongStart()))) * returnTravelTicks);
        float end = time(partialTick) - shift;
        return timeline.average(end - frameTicks, end) * event.intensity();
    }

    public boolean segmentVisible(LightningSegment segment, float partialTick) {
        float time = time(partialTick);
        // Short upward connecting leader meets the downward tree near ground contact.
        if (event.kind().contactsGround() && segment.branchDepth() == 0 && segment.alongStart() >= .97
            && time >= timeline.leaderTicks() * .7f) return true;
        if (segment.alongStart() > timeline.propagation(time)) return false;
        return true;
    }

    public LightningStrikeFxEvent event() { return event; }

    public LightningGeometry geometry() { return geometry; }

    public List<LightningSegment> segments() { return geometry.segments(); }

    public LightningLod lod() { return lod; }

    public int age() { return age; }
}
