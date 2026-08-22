package dev.tempestfx.lightning;

import dev.tempestfx.api.DischargeType;
import java.util.EnumMap;
import java.util.Map;

/**
 * The parameter profile of every discharge archetype, in one place.
 *
 * <p>Deliberately a single lookup table rather than a switch scattered through generation, rendering
 * and audio: adding an archetype means adding a row here, and nothing downstream has to learn about
 * it.
 */
public final class DischargeProfiles {
    private static final Map<DischargeType, DischargeProfile> PROFILES = build();

    private DischargeProfiles() {}

    public static DischargeProfile of(DischargeType type) {
        DischargeProfile profile = PROFILES.get(type);
        return profile != null ? profile : PROFILES.get(DischargeType.NEGATIVE_CLOUD_TO_GROUND);
    }

    /** The ordinary ground flash, and the profile everything without an opinion falls back to. */
    public static DischargeProfile negativeCloudToGround() {
        return PROFILES.get(DischargeType.NEGATIVE_CLOUD_TO_GROUND);
    }

    private static Map<DischargeType, DischargeProfile> build() {
        Map<DischargeType, DischargeProfile> profiles = new EnumMap<>(DischargeType.class);

        // The reference flash. Every scale here is 1 by definition: this is what the geometry, the
        // envelope and the renderer were tuned against.
        profiles.put(DischargeType.NEGATIVE_CLOUD_TO_GROUND, new DischargeProfile(
            DischargeType.NEGATIVE_CLOUD_TO_GROUND, EnvelopeProfile.DEFAULT,
            0, 1.0, 1.0, 1.0, 1.0, 3, 1.0, -0.18,
            1.0f, 1.0f, 0f, 1.0f, 1.0f));

        // A superbolt is not a bright negative flash. The channel is wide and nearly bare, it holds
        // for half a second instead of stuttering, and the one stroke it does have dominates the
        // frame - so branching is cut hard, and the timeline gets longer and flatter rather than
        // louder.
        //
        // The geometry numbers move together on purpose, and two effects drive them.
        //
        // Widening the ribbon without changing the subdivision halves the segment-length-to-width
        // ratio, and the quads then overlap at sharp angles instead of joining - a wide bolt drawn
        // that way reads as torn lumps rather than as lightning. So the channel loses a generation as
        // it gains width.
        //
        // The joints themselves are the second effect. Each segment is its own camera-facing quad
        // with its own side vector, so two segments meeting at an angle do not share an edge, and the
        // mismatch is half-width times the tangent of half the kink angle. An ordinary bolt hides
        // that among its branches; a positive flash is a bare bright trunk with almost no forks, so
        // the same seam is conspicuous. Hence the low roughness: it is the late generations that set
        // the kink angle, and taking the decay from 0.56 to 0.42 drops the seam from roughly two
        // pixels to under half of one at thirty blocks, while the early generations still carry the
        // shape of the channel. A high-current channel being smooth is also simply what it is.
        profiles.put(DischargeType.POSITIVE_CLOUD_TO_GROUND, new DischargeProfile(
            DischargeType.POSITIVE_CLOUD_TO_GROUND,
            new EnvelopeProfile(13f, 0.85f, 0.34f, 1, 2.2f, 0.55f, 0.72f),
            -1, 1.55, 0.22, 0.7, 0.75, 1, 0.45, -0.30,
            1.75f, 1.0f, 0.62f, 2.4f, 2.2f));

        // Horizontal, no ground contact, and long. Forks spread flat instead of hanging down, and
        // the leader takes visibly longer to cross the sky than a ground flash takes to land.
        profiles.put(DischargeType.CLOUD_TO_CLOUD, new DischargeProfile(
            DischargeType.CLOUD_TO_CLOUD,
            new EnvelopeProfile(11f, 3.6f, 0.46f, 2, 5.0f, 0.4f, 0.8f),
            0, 1.15, 0.75, 0.8, 1.0, 2, 0f, 0.02,
            1.15f, 0.9f, 0.15f, 2.2f, 1.1f));

        // Mostly buried: the channel is barely exposed and the cloud around it does the work. The
        // pulse train is what makes a distant storm read as electrically busy rather than as one
        // flash.
        profiles.put(DischargeType.INTRACLOUD, new DischargeProfile(
            DischargeType.INTRACLOUD,
            new EnvelopeProfile(14f, 2.4f, 0.38f, 3, 8.0f, 0.5f, 1.0f),
            0, 1.0, 0.9, 0.7, 1.0, 2, 0f, 0.0,
            0.85f, 0.16f, 0.22f, 3.4f, 0.75f));

        // The rare one. Very long channel, large-scale branching, a leader that travels for more
        // than a second, and a decay slow enough to stay readable across the whole horizon. Same
        // trade as the superbolt: wider ribbon, one generation fewer, less wander.
        profiles.put(DischargeType.MEGAFLASH, new DischargeProfile(
            DischargeType.MEGAFLASH,
            new EnvelopeProfile(30f, 13f, 0.19f, 3, 18f, 0.55f, 1.0f),
            -1, 1.7, 0.85, 0.9, 0.85, 3, 0f, 0.01,
            1.8f, 0.95f, 0.3f, 4.5f, 2.6f));

        return profiles;
    }
}
