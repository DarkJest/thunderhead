package dev.tempestfx.sky;

import dev.tempestfx.lightning.LightningSegment;
import dev.tempestfx.math.Vec3d;
import java.util.List;

/**
 * The finished shape of one transient luminous event.
 *
 * <p>Generated once and never rebuilt, like bolt geometry. Filaments reuse {@link LightningSegment}
 * because the renderer needs exactly what it carries - two endpoints, a tapering width and a
 * position along the structure - and a second record with the same fields would only be a second
 * thing to keep correct.
 *
 * <p>{@code alongStart} and {@code alongEnd} run 0 at the anchor to 1 at the far end of the
 * structure, which for a sprite means downward and for a jet upward. The renderer uses that both to
 * reveal the structure as it develops and to run the colour ramp along it.
 *
 * @param filaments the columns and tendrils that carry the event's colour
 * @param wisps     the faint cool fringe reaching above a sprite; empty for a jet
 * @param glows     diffuse halos, which is most of what a sprite actually looks like from far away
 * @param anchor    where the structure is pinned: the top of a sprite, the cloud top under a jet
 * @param height    extent along the axis, in blocks
 * @param width     lateral extent, in blocks
 */
public record LuminousStructure(List<LightningSegment> filaments,
                                List<LightningSegment> wisps,
                                List<LuminousGlow> glows,
                                Vec3d anchor,
                                double height,
                                double width) {
    public LuminousStructure {
        filaments = List.copyOf(filaments);
        wisps = List.copyOf(wisps);
        glows = List.copyOf(glows);
    }

    public int segmentCount() { return filaments.size() + wisps.size(); }

    /**
     * One soft emissive blob.
     *
     * <p>A sprite seen from far enough away is mostly this: the filaments give it its silhouette,
     * but the light that carries across the distance is diffuse.
     *
     * @param position centre
     * @param radius   in blocks
     * @param strength peak output relative to the event's own brightness
     * @param along    position along the structure, so a halo appears when its part of it does
     */
    public record LuminousGlow(Vec3d position, double radius, float strength, double along) {}
}
