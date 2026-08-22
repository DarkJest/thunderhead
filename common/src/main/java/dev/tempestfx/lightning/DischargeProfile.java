package dev.tempestfx.lightning;

import dev.tempestfx.api.DischargeType;
import java.util.Objects;

/**
 * Everything that makes one discharge type look, sound and behave like itself.
 *
 * <p>One record per archetype, resolved once per flash through {@link DischargeProfiles} and carried
 * on the effect from then on. Subsystems read the numbers they need off it instead of branching on
 * the type, which is what keeps a five-way switch out of the geometry, the renderer and the audio.
 *
 * <p>One invariant governs the geometry numbers and is worth stating outright: a segment has to stay
 * several times longer than the ribbon drawn along it is wide. Below roughly 4:1 the camera-facing
 * quads of consecutive segments overlap at sharp angles instead of joining, and an additive stack of
 * overlapping quads reads as torn lumps rather than as a channel. So widening a channel means
 * lengthening its segments too - which is what {@code generationsDelta} is for - and a wide channel
 * wanders <em>less</em> than a thin one, not more.
 *
 * @param type            the archetype this profile describes
 * @param envelope        its timeline
 * @param generationsDelta subdivisions added to or removed from the player's geometry quality; a
 *                        wider channel needs fewer, longer segments to stay a channel
 * @param widthScale      trunk half-width multiplier; a positive flash is a visibly wider channel
 * @param branchScale     fork probability multiplier
 * @param displacementScale wander amplitude multiplier
 * @param roughnessScale  multiplier on the per-generation amplitude decay, which is what sets how
 *                        sharply the channel kinks from one segment to the next; a bare, wide
 *                        channel needs this low or the joints between ribbon quads become visible
 * @param maxBranchDepth  fork recursion limit
 * @param canopyScale     multiplier on the near-horizontal cloud-base canopy
 * @param forkBiasY       vertical bias of every fork; negative hangs down, zero spreads flat
 * @param energyScale     brightness and impact multiplier relative to an ordinary negative flash
 * @param channelOpacity  how much of the channel is actually exposed; low for a buried event
 * @param warmth          0 draws the cold blue-white channel, 1 the warm violet-white of a superbolt
 * @param cloudGlow       how strongly this discharge lights the cloud around it
 * @param thunderScale    multiplier on the thunder impulse it schedules
 */
public record DischargeProfile(
    DischargeType type,
    EnvelopeProfile envelope,
    int generationsDelta,
    double widthScale,
    double branchScale,
    double displacementScale,
    double roughnessScale,
    int maxBranchDepth,
    double canopyScale,
    double forkBiasY,
    float energyScale,
    float channelOpacity,
    float warmth,
    float cloudGlow,
    float thunderScale
) {
    public DischargeProfile {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(envelope, "envelope");
        if (!(widthScale > 0)) throw new IllegalArgumentException("widthScale must be positive");
        if (branchScale < 0 || displacementScale < 0) throw new IllegalArgumentException("negative scale");
        if (!(roughnessScale > 0)) throw new IllegalArgumentException("roughnessScale must be positive");
        if (channelOpacity < 0 || channelOpacity > 1) throw new IllegalArgumentException("channelOpacity must be 0..1");
        if (energyScale <= 0) throw new IllegalArgumentException("energyScale must be positive");
    }

    /**
     * Applies this profile to the shared tuning.
     *
     * @param base      the mod's generation defaults, already carrying the player's quality settings
     * @param skySpread the player's canopy setting, which this profile scales rather than replaces
     */
    public LightningGenerationConfig geometry(LightningGenerationConfig base, double skySpread) {
        return base
            .withGenerations(Math.max(3, Math.min(9, base.generations() + generationsDelta)))
            .withBranchProbability(Math.min(0.95, base.branchProbability() * branchScale))
            .withDisplacement(base.displacement() * displacementScale)
            // Clamped rather than validated away: the legal band is the one the subdivision was
            // written for, and a profile asking to leave it should be pulled back, not rejected.
            .withRoughness(Math.max(0.35, Math.min(0.85, base.roughness() * roughnessScale)))
            .withMaxBranchDepth(Math.min(4, maxBranchDepth))
            .withForkBiasY(forkBiasY)
            .withSkySpread(skySpread * canopyScale);
    }
}
