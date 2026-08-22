package dev.tempestfx.client;

/**
 * Decides whether a sound about to play is one of the two vanilla lightning clips Thunderhead
 * replaces. Matching is by exact id, so nothing else is ever affected.
 */
public final class VanillaSoundFilter {
    private static final String THUNDER = "minecraft:entity.lightning_bolt.thunder";
    private static final String IMPACT = "minecraft:entity.lightning_bolt.impact";

    private VanillaSoundFilter() {}

    public static boolean shouldSuppress(String id, boolean customThunderEnabled) {
        return customThunderEnabled && (THUNDER.equals(id) || IMPACT.equals(id));
    }
}
