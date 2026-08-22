package dev.tempestfx.api;

/**
 * What kind of discharge a flash is.
 *
 * <p>Not a brightness multiplier with five names: each type is a different event with its own
 * geometry, its own timeline, its own colour and its own thunder, resolved once through
 * {@code dev.tempestfx.lightning.DischargeProfiles} and carried on the strike from then on.
 */
public enum DischargeType {
    /** The ordinary ground strike: stepped leader, heavy branching, several return strokes. */
    NEGATIVE_CLOUD_TO_GROUND,
    /**
     * The rare superbolt. One dominant stroke down a wide, nearly unbranched channel, far brighter
     * and far longer-lived than the negative flash, and able to land well outside the storm core.
     */
    POSITIVE_CLOUD_TO_GROUND,
    /** A horizontal channel travelling between cloud regions. Never reaches the ground. */
    CLOUD_TO_CLOUD,
    /** Activity buried inside one cloud: little exposed channel, several pulses of internal light. */
    INTRACLOUD,
    /** The very rare kilometre-scale horizontal event that crosses most of the visible storm. */
    MEGAFLASH;

    /** Whether this discharge terminates on terrain, and therefore has an impact at all. */
    public boolean reachesGround() {
        return this == NEGATIVE_CLOUD_TO_GROUND || this == POSITIVE_CLOUD_TO_GROUND;
    }

    /** Whether the channel lives in the cloud layer rather than hanging out of it. */
    public boolean aerial() {
        return !reachesGround();
    }

    /** Whether most of the channel stays hidden inside cloud, lighting it from within. */
    public boolean buriedInCloud() {
        return this == INTRACLOUD;
    }
}
