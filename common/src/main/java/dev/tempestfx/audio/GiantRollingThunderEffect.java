package dev.tempestfx.audio;

import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Consumer;

/**
 * A four-to-fifteen second rolling thunder event, independent of any lightning.
 */
public final class GiantRollingThunderEffect {
    /** Nothing may be scheduled past this point; the effect is guaranteed to end. */
    public static final int MAX_DURATION_TICKS = 320;
    public static final int MIN_DURATION_TICKS = 80;
    /** Distant channels per second, not per event. */
    public static final int MIN_FLASH_RATE = 15;
    public static final int MAX_FLASH_RATE = 100;
    /** Audio stays modest however long the event is: voices are a scarce resource, channels are not. */
    private static final int MAX_PULSES = 22;
    /** Hard ceiling on generated channels, so a long event at the top rate still cannot run away. */
    private static final int MAX_TOTAL_BOLTS = 900;
    private static final double CLOUD_BASE = 96;
    private static final double CLOUD_VARIANCE = 62;
    /** Half-width of the storm front, in radians. Narrow: the wall has to read as one front. */
    private static final double MIN_FRONT_ARC = 0.20;
    private static final double MAX_FRONT_ARC = 0.45;
    /** Depth spread around the front distance. A wall, not a cloud of scattered points. */
    private static final double DEPTH_SPREAD = 0.16;
    /** Fallback when the caller does not know the view distance. */
    public static final double DEFAULT_VIEW_DISTANCE = 240;

    private final List<ThunderPulse> pulses;
    private final List<DistantBoltCue> bolts;
    private final int durationTicks;
    private final int flashRate;
    private final long seed;
    private int age;
    private int nextPulse;
    private int nextFlash;

    private GiantRollingThunderEffect(long seed, int durationTicks, int flashRate,
                                      List<ThunderPulse> pulses, List<DistantBoltCue> bolts) {
        this.seed = seed;
        this.durationTicks = durationTicks;
        this.flashRate = flashRate;
        this.pulses = pulses;
        this.bolts = bolts;
    }

    public static GiantRollingThunderEffect plan(long seed, Vec3d listener, Vec3d origin, float intensity) {
        return plan(seed, listener, origin, intensity, 0, 0, DEFAULT_VIEW_DISTANCE);
    }

    public static GiantRollingThunderEffect plan(long seed, Vec3d listener, Vec3d origin, float intensity,
                                                 int durationTicks, int flashRate) {
        return plan(seed, listener, origin, intensity, durationTicks, flashRate, DEFAULT_VIEW_DISTANCE);
    }

    /**
     * Plans a complete event.
     *
     * @param listener      where the player is; bearings are laid out around them
     * @param origin        where the triggering strike was, which sets the opening bearing
     * @param intensity     overall scale, 1 for a natural strike
     * @param durationTicks explicit length, or {@code 0} to roll one between four and fifteen seconds
     * @param flashRate     explicit channels per second, or {@code 0} to roll one
     * @param viewDistance  how far the player can actually see, in blocks; the front is placed well
     *                      inside it, because a channel behind the fog is a channel nobody sees
     */
    public static GiantRollingThunderEffect plan(long seed, Vec3d listener, Vec3d origin, float intensity,
                                                 int durationTicks, int flashRate, double viewDistance) {
        SplittableRandom random = new SplittableRandom(StrikeSeed.derive(seed, 0x8011));

        // Fourth-power roll: a typical storm sits around twenty a second, sixty-plus happens maybe
        // one time in six, and the hundred-a-second sky-splitters are genuinely rare.
        int rate = flashRate > 0
            ? FxMath.clamp(flashRate, 1, MAX_FLASH_RATE * 3)
            : MIN_FLASH_RATE + (int) Math.round(
                Math.pow(random.nextDouble(), 4) * (MAX_FLASH_RATE - MIN_FLASH_RATE));
        int duration = durationTicks > 0
            ? FxMath.clamp(durationTicks, 20, MAX_DURATION_TICKS)
            : MIN_DURATION_TICKS + random.nextInt(MAX_DURATION_TICKS - MIN_DURATION_TICKS + 1);

        double dx = origin.x() - listener.x();
        double dz = origin.z() - listener.z();
        double openingBearing = Math.atan2(dz, dx);
        double originDistance = Math.max(4, Math.hypot(dx, dz));
        float scale = (float) FxMath.clamp(intensity, 0.2, 2.0);
        // The front drifts one way over the event, but only slightly: a wall that swings right
        // around the player stops being a wall and turns into scattered strikes.
        double sweep = (random.nextBoolean() ? 1 : -1) * (0.2 + random.nextDouble() * 0.3);

        List<ThunderPulse> pulses = planAudio(random, listener, origin, openingBearing, originDistance,
            scale, duration);
        List<DistantBoltCue> cues = planBolts(random, listener, openingBearing, sweep, duration, rate,
            viewDistance);
        return new GiantRollingThunderEffect(seed, duration, rate, pulses, cues);
    }

    private static List<ThunderPulse> planAudio(SplittableRandom random, Vec3d listener, Vec3d origin,
                                                double openingBearing, double originDistance,
                                                float scale, int duration) {
        List<ThunderPulse> pulses = new ArrayList<>();

        // CRACK: the nearest section of the channel, from the strike itself.
        pulses.add(new ThunderPulse(0, ThunderProfile.ROLL_CRACK, origin,
            0.95f * scale, (float) random.nextDouble(1.0, 1.18), 0.5f));

        // BOOM: the body, a fraction of a second behind and noticeably lower.
        pulses.add(new ThunderPulse(2 + random.nextInt(3), ThunderProfile.ROLL_BOOM,
            at(listener, openingBearing + random.nextDouble(-0.2, 0.2), originDistance * 0.9, random),
            scale, (float) random.nextDouble(0.82, 0.94), 1f));

        // WALL: two or three overlapping low fronts. Their attacks are long, so starting them close
        // together builds one huge swell rather than three separate hits.
        int walls = 2 + random.nextInt(2);
        for (int index = 0; index < walls; index++) {
            pulses.add(new ThunderPulse(5 + random.nextInt(Math.max(2, duration / 8)), ThunderProfile.ROLL_WALL,
                at(listener, openingBearing + random.nextDouble(-0.9, 0.9),
                    originDistance * random.nextDouble(0.8, 2.2), random),
                (float) random.nextDouble(0.72, 1.0) * scale,
                (float) random.nextDouble(0.62, 0.8), 0.75f));
        }

        // ROLLS: gaps are irregular and often shorter than the clips, so rolls overlap.
        int rolls = 4 + random.nextInt(4);
        int cursor = Math.max(12, duration / 10);
        int span = Math.max(8, (int) (duration * 0.65 / rolls));
        float strength = 0.8f;
        for (int index = 0; index < rolls && cursor < duration; index++) {
            boolean wide = random.nextDouble() < 0.4;
            double bearing = random.nextDouble() < 0.55
                ? openingBearing + random.nextDouble(-1.4, 1.4)
                : random.nextDouble(Math.PI * 2);
            pulses.add(new ThunderPulse(cursor,
                wide ? ThunderProfile.ROLL_WALL : ThunderProfile.ROLL_BODY,
                at(listener, bearing, random.nextDouble(45, 190), random),
                Math.max(0.18f, strength * (float) random.nextDouble(0.7, 1.05) * scale),
                (float) random.nextDouble(0.62, 1.04),
                wide ? 0.55f : 0.3f));
            cursor += Math.max(6, span / 2 + random.nextInt(span));
            strength *= 0.86f;
        }

        // GRUMBLE: far sections of the same front, quiet and low, from anywhere.
        int grumbles = 2 + random.nextInt(2);
        for (int index = 0; index < grumbles; index++) {
            pulses.add(new ThunderPulse((int) (duration * 0.4) + random.nextInt(Math.max(2, (int) (duration * 0.45))),
                ThunderProfile.ROLL_FAR,
                at(listener, random.nextDouble(Math.PI * 2), random.nextDouble(190, 340), random),
                (float) random.nextDouble(0.22, 0.42) * scale,
                (float) random.nextDouble(0.58, 0.8), 0.12f));
        }

        // TAIL: the last echoes coming back off the terrain.
        pulses.add(new ThunderPulse((int) (duration * 0.55) + random.nextInt(Math.max(2, (int) (duration * 0.3))),
            ThunderProfile.ROLL_TAIL,
            at(listener, openingBearing + random.nextDouble(-2.4, 2.4), random.nextDouble(70, 220), random),
            (float) random.nextDouble(0.24, 0.4) * scale,
            (float) random.nextDouble(0.55, 0.72), 0.08f));

        pulses.sort(Comparator.comparingInt(ThunderPulse::delayTicks));
        if (pulses.size() > MAX_PULSES) pulses.subList(MAX_PULSES, pulses.size()).clear();
        return List.copyOf(pulses);
    }

    /**
     * The wall of distant channels.
     */
    private static List<DistantBoltCue> planBolts(SplittableRandom random, Vec3d listener,
                                                  double openingBearing, double sweep,
                                                  int duration, int rate, double viewDistance) {
        int total = Math.min(MAX_TOTAL_BOLTS, (int) Math.round(rate * duration / 20.0));
        List<DistantBoltCue> cues = new ArrayList<>(total);

        // The whole storm sits at one distance, comfortably inside the fog, in a narrow arc. Spread
        // the strokes over hundreds of blocks of depth and eighty degrees of sky and they stop
        // looking like a storm front and start looking like scattered noise.
        double frontDistance = FxMath.clamp(viewDistance * 0.42, 55, 190);
        double frontArc = MIN_FRONT_ARC + random.nextDouble() * (MAX_FRONT_ARC - MIN_FRONT_ARC);

        for (int index = 0; index < total; index++) {
            double progress = index / (double) Math.max(1, total);
            // Jitter inside the slot rather than a running cursor: the rate stays exact however
            // clustered the individual strokes are.
            int delay = FxMath.clamp(
                (int) Math.round(progress * duration + random.nextDouble(-1.5, 1.5) * (duration / (double) total)),
                0, duration);

            double bearing = openingBearing + sweep * progress + random.nextDouble(-frontArc, frontArc);
            double distance = frontDistance * (1 + random.nextDouble(-DEPTH_SPREAD, DEPTH_SPREAD));
            double height = CLOUD_BASE + random.nextDouble() * CLOUD_VARIANCE;
            // Lean: real cloud-to-ground strokes are far from plumb, and a wall of perfectly
            // vertical lines looks like a fence.
            double lean = height * random.nextDouble(0.12, 0.45);
            double leanBearing = random.nextDouble(Math.PI * 2);

            Vec3d top = new Vec3d(
                listener.x() + Math.cos(bearing) * distance,
                listener.y() + height,
                listener.z() + Math.sin(bearing) * distance);
            Vec3d ground = new Vec3d(
                top.x() + Math.cos(leanBearing) * lean,
                listener.y() + random.nextDouble(-18, 6),
                top.z() + Math.sin(leanBearing) * lean);

            cues.add(new DistantBoltCue(delay, top, ground,
                (float) random.nextDouble(0.5, 1.0), random.nextLong()));
        }

        cues.sort(Comparator.comparingInt(DistantBoltCue::delayTicks));
        return List.copyOf(cues);
    }

    /** A point on a bearing around the listener, lifted into the air so the roll is overhead. */
    private static Vec3d at(Vec3d listener, double bearing, double distance, SplittableRandom random) {
        double elevation = distance * random.nextDouble(0.2, 0.65);
        return new Vec3d(
            listener.x() + Math.cos(bearing) * distance,
            listener.y() + elevation,
            listener.z() + Math.sin(bearing) * distance);
    }

    /** Advances one tick and releases everything that has come due. */
    public void tick(Consumer<ThunderPulse> audio, Consumer<DistantBoltCue> visual) {
        age++;
        while (nextPulse < pulses.size() && pulses.get(nextPulse).delayTicks() <= age) {
            audio.accept(pulses.get(nextPulse++));
        }
        while (nextFlash < bolts.size() && bolts.get(nextFlash).delayTicks() <= age) {
            visual.accept(bolts.get(nextFlash++));
        }
    }

    public boolean finished() {
        return (nextPulse >= pulses.size() && nextFlash >= bolts.size()) || age > MAX_DURATION_TICKS;
    }

    public int durationTicks() { return durationTicks; }

    /** Distant channels per second. */
    public int flashRate() { return flashRate; }

    public int remainingPulses() { return Math.max(0, pulses.size() - nextPulse); }

    public int totalPulses() { return pulses.size(); }

    public int totalBolts() { return bolts.size(); }

    /** True when the generation ceiling cut the requested rate short. */
    public boolean boltsWereTruncated() {
        return bolts.size() < (int) Math.round(flashRate * durationTicks / 20.0);
    }

    public List<ThunderPulse> pulses() { return pulses; }

    public List<DistantBoltCue> bolts() { return bolts; }

    public long seed() { return seed; }
}
