package dev.tempestfx.sky;

import dev.tempestfx.lightning.EnvelopeProfile;
import java.util.EnumMap;
import java.util.Map;

/** The profile of every transient luminous event, in one table. */
public final class LuminousProfiles {
    private static final Map<TransientLuminousEvent, LuminousProfile> PROFILES = build();

    private LuminousProfiles() {}

    public static LuminousProfile of(TransientLuminousEvent type) {
        LuminousProfile profile = PROFILES.get(type);
        return profile != null ? profile : PROFILES.get(TransientLuminousEvent.RED_SPRITE);
    }

    private static Map<TransientLuminousEvent, LuminousProfile> build() {
        Map<TransientLuminousEvent, LuminousProfile> profiles = new EnumMap<>(TransientLuminousEvent.class);

        // Present almost at once - propagation under a tick, so the tendrils only suggest reaching
        // downward - then gone inside four hundred milliseconds, with one weaker second pulse the way
        // a real sprite often flickers twice. Red through the body, pink where it is brightest,
        // dark carmine at the tips.
        profiles.put(TransientLuminousEvent.RED_SPRITE, new LuminousProfile(
            TransientLuminousEvent.RED_SPRITE,
            new EnvelopeProfile(8f, 0.7f, 0.85f, 1, 1.6f, 0.3f, 0.5f),
            190, 0.72,
            0xff8496, 0xff2b3c, 0xa2123a, 0x9aa6ff,
            1.0f));

        // An order of magnitude slower, and the climb is the whole point: nine ticks of propagation
        // over a fourteen-tick life means the cone is visibly seen to rise out of the cloud top
        // rather than to appear above it.
        profiles.put(TransientLuminousEvent.BLUE_JET, new LuminousProfile(
            TransientLuminousEvent.BLUE_JET,
            new EnvelopeProfile(15f, 9f, 0.3f, 0, 0f, 0.3f, 0.5f),
            125, 0.34,
            0x4a6cff, 0x7a5cff, 0xa06cff, 0x000000,
            0.7f));

        return profiles;
    }
}
