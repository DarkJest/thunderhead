package dev.tempestfx.api;

import dev.tempestfx.math.Vec3d;

/**
 * The entity a bolt came down on, when there is one.
 *
 * @param entityId replicated entity id, or {@code -1} when the bolt hit terrain
 * @param player   whether the struck entity is a player
 * @param position feet position of the struck entity at strike time
 * @param width    entity bounding-box width, used to size the imprint
 * @param height   entity bounding-box height, used to size the discharge
 */
public record StrikeTarget(int entityId, boolean player, Vec3d position, float width, float height) {
    public static final StrikeTarget NONE = new StrikeTarget(-1, false, Vec3d.ZERO, 0, 0);

    public StrikeTarget {
        if (position == null) throw new IllegalArgumentException("position must not be null");
    }

    public static StrikeTarget none() { return NONE; }

    public boolean present() { return entityId >= 0; }
}
