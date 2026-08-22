package dev.tempestfx.api;

import dev.tempestfx.audio.ThunderProfile;

/**
 * Which thunder clip a strike should use.
 */
public enum ThunderVoice {
    /** Pick by distance, the way the mod does for its own strikes. */
    AUTO(null),
    /** Sharp, immediate. A strike almost on top of the listener. */
    CLOSE_CRACK(ThunderProfile.CLOSE_CRACK),
    /** Heavy and wide. Close, but with weight behind it. */
    CLOSE_HEAVY(ThunderProfile.CLOSE_HEAVY),
    MEDIUM_RUMBLE(ThunderProfile.MEDIUM_RUMBLE),
    DISTANT_THUNDER(ThunderProfile.DISTANT_THUNDER),
    /** The long one. Seven seconds of decay. */
    LONG_ROLLING_THUNDER(ThunderProfile.LONG_ROLLING_THUNDER),
    /** No thunder at all — the strike is seen and not heard. */
    SILENT(null);

    private final ThunderProfile profile;

    ThunderVoice(ThunderProfile profile) { this.profile = profile; }

    /** @return the clip to play, or {@code null} for {@link #AUTO} and {@link #SILENT} */
    public ThunderProfile profile() { return profile; }
}
