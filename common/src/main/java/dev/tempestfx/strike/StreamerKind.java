package dev.tempestfx.strike;

/**
 * What kind of object is answering the leader.
 *
 * <p>The differences are small but deliberate: a lightning rod is built to do this and throws a
 * long, bright, nearly straight streamer, while a hillside manages a short faint wisp. That contrast
 * is the point — it is what makes a rod look like it is doing its job.
 *
 * @see StreamerCandidate
 */
public enum StreamerKind {
    /** A lightning rod. Longest reach, brightest, and the strongest claim on the strike. */
    ROD(1.8, 1.0f, 3.2),
    /** Exposed metal: a copper block, an iron structure, a building corner sheathed in it. */
    METAL(1.15, 0.72f, 1.9),
    /** Anything else tall enough to compete: a treetop, a tower, a ridge. */
    TERRAIN(0.85, 0.5f, 1.35);

    private final double reach;
    private final float brightness;
    private final double baseWeight;

    StreamerKind(double reach, float brightness, double baseWeight) {
        this.reach = reach;
        this.brightness = brightness;
        this.baseWeight = baseWeight;
    }

    /**
     * How far the streamer climbs, in blocks.
     *
     * <p>Deliberately small. These are metre-scale reaches on a one-block rod, and the channel is
     * built to end near the top of one: make them long and the bolt terminates in mid-air with a
     * detached spark hanging under it, which is not an attachment, it is two unrelated effects.
     */
    public double reach() { return reach; }

    /** Output relative to the channel it is reaching for. */
    public float brightness() { return brightness; }

    /** Multiplier on the height-derived weight; a rod outranks a taller tree on purpose. */
    public double baseWeight() { return baseWeight; }
}
