package dev.tempestfx.effect;

import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.DischargeProfile;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns the cloud regions currently lit from inside.
 *
 * <p>Simulation only, and hard-bounded: a discharge asks for a few regions along its channel, they
 * pulse and expire on their own, and the renderer reads the list. Nothing here allocates per frame
 * and nothing here knows how a lit cloud is drawn.
 */
public final class CloudIlluminationSystem {
    /** Most regions one discharge may light, before the global cap. */
    private static final int MAX_PER_DISCHARGE = 6;
    /** Radius of a lit region as a fraction of the channel it belongs to. */
    private static final double RADIUS_RATIO = 0.42;
    private static final double MIN_RADIUS = 22;
    private static final double MAX_RADIUS = 190;

    private final List<CloudLightSource> sources = new ArrayList<>();
    private final List<CloudLightSource> view = Collections.unmodifiableList(sources);

    /**
     * Lights the cloud along a channel.
     *
     * @return how many regions were added
     */
    public int illuminate(Vec3d start, Vec3d end, long seed, float energy, DischargeProfile profile,
                          TempestConfig config) {
        if (!config.sky.cloudIllumination || config.sky.cloudIlluminationStrength <= 0) return 0;
        int limit = config.sky.maxCloudLightSources;
        if (limit <= 0) return 0;

        double span = start.distanceTo(end);
        // A short buried event is one glowing region; a channel crossing the sky is a row of them.
        int count = (int) Math.max(1, Math.min(MAX_PER_DISCHARGE, Math.round(span / 90.0) + 1));
        float strength = energy * profile.cloudGlow() * config.sky.cloudIlluminationStrength;
        float radius = (float) Math.max(MIN_RADIUS, Math.min(MAX_RADIUS,
            Math.max(span * RADIUS_RATIO / count, MIN_RADIUS)));

        int added = 0;
        for (int index = 0; index < count; index++) {
            while (sources.size() >= limit) sources.removeFirst();
            double t = count == 1 ? 0.5 : (index + 0.5) / count;
            long regionSeed = StrikeSeed.derive(seed, 0xc10d0 + index);
            // Regions sit near the channel rather than exactly on it: cloud is not a tube of light.
            Vec3d anchor = start.lerp(end, t).add(
                StrikeSeed.signed(regionSeed, 0x1) * radius * 0.35,
                StrikeSeed.signed(regionSeed, 0x2) * radius * 0.2,
                StrikeSeed.signed(regionSeed, 0x3) * radius * 0.35);
            sources.add(new CloudLightSource(anchor, radius,
                strength * (float) (0.7 + StrikeSeed.unit(regionSeed, 0x4) * 0.5),
                profile.warmth(), regionSeed));
            added++;
        }
        return added;
    }

    public void tick() {
        for (int index = sources.size() - 1; index >= 0; index--) {
            CloudLightSource next = sources.get(index).next();
            if (next.expired()) sources.remove(index); else sources.set(index, next);
        }
    }

    public List<CloudLightSource> sources() { return view; }

    public int activeCount() { return sources.size(); }

    public void clear() { sources.clear(); }
}
