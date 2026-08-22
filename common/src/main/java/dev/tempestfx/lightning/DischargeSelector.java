package dev.tempestfx.lightning;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.math.StrikeSeed;

/**
 * Decides which archetype a flash is, from the same seed every client derives everything else from.
 *
 * <p>Kept out of the ingest path itself so the policy is one testable function rather than a branch
 * inside the code that turns a vanilla bolt into an event. The roll is a pure function of the strike
 * seed, so two players watching the same bolt see the same archetype without a packet.
 */
public final class DischargeSelector {
    /** Salt for the positive/negative roll; distinct from every other use of the strike seed. */
    private static final int POLARITY_SALT = 0x504f4c;

    private DischargeSelector() {}

    /**
     * @param positiveChance fraction of ground strikes that are positive superbolts, {@code 0..1}
     */
    public static DischargeType forGroundStrike(long seed, float positiveChance) {
        if (!(positiveChance > 0)) return DischargeType.NEGATIVE_CLOUD_TO_GROUND;
        return StrikeSeed.unit(seed, POLARITY_SALT) < positiveChance
            ? DischargeType.POSITIVE_CLOUD_TO_GROUND
            : DischargeType.NEGATIVE_CLOUD_TO_GROUND;
    }
}
