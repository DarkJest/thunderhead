package dev.tempestfx.entity;

import dev.tempestfx.TempestFx;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Entity types owned by the mod.
 *
 * <p>The {@link EntityType} itself is built here so both loaders register the exact same definition;
 * each loader only supplies its own registry call.
 */
public final class TempestEntities {
    public static final ResourceLocation BALL_LIGHTNING_ID =
        ResourceLocation.fromNamespaceAndPath(TempestFx.MOD_ID, "ball_lightning");

    private static EntityType<BallLightning> ballLightning;

    private TempestEntities() {}

    /** Definition shared by both loaders. Small, unsaved, cheaply tracked, immune to its own fire. */
    public static EntityType<BallLightning> buildBallLightning() {
        return EntityType.Builder.<BallLightning>of(BallLightning::new, MobCategory.MISC)
            .sized(0.9f, 0.9f)
            .fireImmune()
            .noSave()
            .clientTrackingRange(6)
            .updateInterval(2)
            .build(BALL_LIGHTNING_ID.toString());
    }

    public static void setBallLightning(EntityType<BallLightning> type) { ballLightning = type; }

    /** @return the registered type, or {@code null} if registration has not happened yet. */
    @SuppressWarnings("unchecked")
    public static EntityType<BallLightning> ballLightning() {
        if (ballLightning == null) {
            ballLightning = (EntityType<BallLightning>) BuiltInRegistries.ENTITY_TYPE
                .getOptional(BALL_LIGHTNING_ID).orElse(null);
        }
        return ballLightning;
    }

    public static boolean available() { return ballLightning() != null; }
}
