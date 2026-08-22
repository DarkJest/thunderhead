package dev.tempestfx.audio;

/** One recorded thunder layer. Each value maps to an original OGG produced by the asset script. */
public enum ThunderProfile {
    /** Sharp, dry, very close discharge. */
    CLOSE_CRACK("close_crack"),
    /** Close strike with a heavy low body behind the crack. */
    CLOSE_HEAVY("close_heavy"),
    /** Mid-distance rumble. */
    MEDIUM_RUMBLE("medium_rumble"),
    /** Soft transient with a long tail. */
    DISTANT_THUNDER("distant_thunder"),
    /** Long rolling tail used as a second layer under closer profiles. */
    LONG_ROLLING_THUNDER("long_rolling_thunder"),
    /** Sub-bass ground thump for near-field impacts. */
    IMPACT_THUMP("impact_thump"),
    /** Sharp wide-band transient opening a rolling thunder event. */
    ROLL_CRACK("roll_crack"),
    /** Massive low body arriving just behind the crack. */
    ROLL_BOOM("roll_boom"),
    /** Slow-swelling low-frequency wall of sound. */
    ROLL_WALL("roll_wall"),
    /** Mid rolling body used for the irregular secondary rolls. */
    ROLL_BODY("roll_body"),
    /** Distant grumble from the far side of the storm front. */
    ROLL_FAR("roll_far"),
    /** Very long decaying tail. */
    ROLL_TAIL("roll_tail"),
    /** Dry crackle used by the electrical discharge effect. */
    ELECTRIC_ARC("electric_arc");

    /** Profiles heavy enough that the listener should feel them, not just hear them. */
    public boolean shakesTheAir() {
        return this == CLOSE_HEAVY || this == IMPACT_THUMP || this == ROLL_BOOM || this == ROLL_WALL;
    }

    private final String path;

    ThunderProfile(String path) { this.path = path; }

    public String path() { return path; }
}
