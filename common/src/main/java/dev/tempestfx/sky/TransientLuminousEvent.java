package dev.tempestfx.sky;

/**
 * Electrical activity that happens <em>above</em> a thunderstorm rather than inside it.
 *
 * <p>These are not lightning. Lightning is a conducting channel in dense air; a sprite is a glow
 * discharge in air thin enough that the whole region lights up at once, which is why it has no
 * trunk, no attachment and no thunder. They were photographed for the first time in 1989, after
 * decades of pilots reporting them and being disbelieved, and that is roughly the reaction they are
 * here to produce.
 */
public enum TransientLuminousEvent {
    /**
     * A red sprite: a cluster of columns with tendrils hanging beneath, tens of kilometres across,
     * triggered by a powerful positive discharge in the storm below and gone in a fraction of a
     * second.
     */
    RED_SPRITE,
    /**
     * A blue jet: a narrow cone of violet-blue light climbing out of the cloud top and fading well
     * short of the sprite altitudes. Slower than a sprite - long enough to read as movement rather
     * than as a flash.
     */
    BLUE_JET;

    /** Whether the structure grows upward from its anchor, or hangs below it. */
    public boolean climbs() {
        return this == BLUE_JET;
    }
}
