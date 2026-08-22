package dev.tempestfx.neoforge;

import dev.tempestfx.TempestFx;
import dev.tempestfx.audio.TempestSounds;
import dev.tempestfx.audio.ThunderProfile;
import dev.tempestfx.entity.BallLightning;
import dev.tempestfx.entity.TempestEntities;
import dev.tempestfx.server.TempestFxServer;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge bootstrap that runs on both sides.
 *
 * <p>Registries have to exist on a dedicated server too, otherwise the ball lightning entity could
 * not be spawned or replicated. Gameplay lives in {@link TempestFxServer}; everything visual is set
 * up separately in {@link TempestFxNeoForgeClient}.
 */
@Mod(TempestFx.MOD_ID)
public final class TempestFxNeoForge {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, TempestFx.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, TempestFx.MOD_ID);

    private static final Supplier<EntityType<BallLightning>> BALL_LIGHTNING =
        ENTITY_TYPES.register(TempestEntities.BALL_LIGHTNING_ID.getPath(), TempestEntities::buildBallLightning);

    public TempestFxNeoForge(IEventBus modBus, ModContainer container) {
        for (ThunderProfile profile : ThunderProfile.values()) {
            SOUND_EVENTS.register(profile.path(), () -> SoundEvent.createVariableRangeEvent(TempestSounds.id(profile)));
        }
        SOUND_EVENTS.register(modBus);
        ENTITY_TYPES.register(modBus);
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(TempestFxNeoForge::completeSetup));
        NeoForge.EVENT_BUS.addListener(TempestFxNeoForge::onEntityJoin);
        // Re-read the gameplay config whenever a server comes up, matching Fabric. On a dedicated
        // server that is the same moment either way; on a client it means editing the file and
        // reopening a world is enough, without restarting the game.
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class,
            event -> TempestFxServer.load(FMLPaths.CONFIGDIR.get()));
    }

    /**
     * Runs once the registries are frozen. Everything resolved here is also resolvable lazily from
     * the registry, because {@code RegisterRenderers} fires before this work completes.
     */
    private static void completeSetup() {
        for (ThunderProfile profile : ThunderProfile.values()) {
            TempestSounds.bind(profile, BuiltInRegistries.SOUND_EVENT.get(TempestSounds.id(profile)));
        }
        TempestEntities.setBallLightning(BALL_LIGHTNING.get());
        TempestFxServer.load(FMLPaths.CONFIGDIR.get());
        TempestFx.log().info("Thunderhead common bootstrap complete");
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof LightningBolt bolt) {
            TempestFxServer.onLightningSpawn(level, bolt);
        }
    }
}
