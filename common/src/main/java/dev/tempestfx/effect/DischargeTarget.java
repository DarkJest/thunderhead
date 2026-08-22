package dev.tempestfx.effect;

import dev.tempestfx.math.Vec3d;

/**
 * Snapshot of an entity the discharge effect can attach to.
 *
 * <p>Both positions come from server-replicated state ({@code position} and the entity's previous
 * tick position), so the measured speed is the same on every client and the effect stays in sync
 * without any extra packets.
 *
 * @param entityId         replicated entity id
 * @param player           whether this is a player
 * @param position         feet position this tick
 * @param previousPosition feet position last tick
 * @param width            bounding-box width
 * @param height           bounding-box height
 */
public record DischargeTarget(int entityId, boolean player, Vec3d position, Vec3d previousPosition,
                              float width, float height) {
    /** Blocks travelled during the previous tick. */
    public double speed() { return position.distanceTo(previousPosition); }

    /** Lookup used while a discharge is running; returns {@code null} once the entity is gone. */
    @FunctionalInterface
    public interface Lookup {
        DischargeTarget resolve(int entityId);
    }
}
