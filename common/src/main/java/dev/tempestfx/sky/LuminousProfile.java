package dev.tempestfx.sky;

import dev.tempestfx.lightning.EnvelopeProfile;
import java.util.Objects;

/**
 * What one kind of transient luminous event looks like and how long it lasts.
 *
 * <p>The timeline is an {@link EnvelopeProfile}, the same record the discharge archetypes use, so
 * these get the tested propagation-and-decay maths rather than a second implementation of it.
 * {@code propagationTicks} is what separates the two events as much as their colour does: a sprite
 * is essentially present at once and its tendrils only hint at growing downward, while a jet is
 * visibly seen to climb.
 *
 * @param type        the event this profile describes
 * @param envelope    its timeline; {@code propagation} drives the reveal along the structure
 * @param height      nominal extent along the structure's axis, in blocks
 * @param widthRatio  lateral spread as a fraction of {@code height}
 * @param headColor   {@code 0xRRGGBB} at the anchor end of the structure
 * @param bodyColor   colour through the middle of it
 * @param tipColor    colour at the far end, where the filaments taper out
 * @param wispColor   the faint cool fringe above a sprite; unused where there are no wisps
 * @param glowStrength peak output of the diffuse halo behind the filaments
 */
public record LuminousProfile(
    TransientLuminousEvent type,
    EnvelopeProfile envelope,
    double height,
    double widthRatio,
    int headColor,
    int bodyColor,
    int tipColor,
    int wispColor,
    float glowStrength
) {
    public LuminousProfile {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(envelope, "envelope");
        if (!(height > 0)) throw new IllegalArgumentException("height must be positive");
        if (!(widthRatio > 0)) throw new IllegalArgumentException("widthRatio must be positive");
        if (glowStrength < 0) throw new IllegalArgumentException("glowStrength must not be negative");
    }
}
