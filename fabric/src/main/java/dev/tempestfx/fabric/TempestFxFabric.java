package dev.tempestfx.fabric;

import dev.tempestfx.TempestFx;
import dev.tempestfx.audio.TempestSounds;
import dev.tempestfx.audio.ThunderProfile;
import dev.tempestfx.entity.BallLightning;
import dev.tempestfx.entity.TempestEntities;
import dev.tempestfx.server.TempestFxServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

/**
 * Fabric bootstrap that runs on both sides.
 *
 * <p>Registries have to exist on a dedicated server too, otherwise the ball lightning entity could
 * not be spawned or replicated. Gameplay lives in {@link TempestFxServer}; everything visual is set
 * up separately in {@link TempestFxFabricClient}.
 */
public final class TempestFxFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        for (ThunderProfile profile : ThunderProfile.values()) {
            ResourceLocation id = TempestSounds.id(profile);
            TempestSounds.bind(profile, Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                SoundEvent.createVariableRangeEvent(id)));
        }
        EntityType<BallLightning> ballLightning = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            TempestEntities.BALL_LIGHTNING_ID, TempestEntities.buildBallLightning());
        TempestEntities.setBallLightning(ballLightning);

        ServerLifecycleEvents.SERVER_STARTING.register(
            server -> TempestFxServer.load(FabricLoader.getInstance().getConfigDir()));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof LightningBolt bolt) TempestFxServer.onLightningSpawn(level, bolt);
        });
        TempestFx.log().info("Thunderhead common bootstrap complete");
    }
}
