package dev.tempestfx.fabric;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import dev.tempestfx.TempestFx;
import dev.tempestfx.audio.TempestSounds;
import dev.tempestfx.audio.ThunderProfile;
import dev.tempestfx.client.TempestFxClient;
import dev.tempestfx.client.TempestOptionsScreen;
import dev.tempestfx.entity.TempestEntities;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.platform.ClientPlatform;
import dev.tempestfx.render.BallLightningEntityRenderer;
import dev.tempestfx.render.EmptyLightningRenderer;
import dev.tempestfx.render.TempestShaders;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;

/**
 * Fabric client bootstrap.
 */
public final class TempestFxFabricClient implements ClientModInitializer {
    private static TempestFxClient client;

    @Override
    public void onInitializeClient() {
        client = new TempestFxClient(new FabricPlatform());
        EntityRendererRegistry.register(EntityType.LIGHTNING_BOLT, EmptyLightningRenderer::new);
        var ballLightning = TempestEntities.ballLightning();
        if (ballLightning != null) {
            EntityRendererRegistry.register(ballLightning, BallLightningEntityRenderer::new);
        } else {
            TempestFx.log().error("Ball lightning entity type is missing; its renderer was skipped");
        }
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            // Core shaders resolve in the minecraft namespace, so the bundled files live there too.
            for (String name : TempestShaders.NAMES) {
                context.register(ResourceLocation.withDefaultNamespace(name),
                    DefaultVertexFormat.POSITION_TEX_COLOR, shader -> TempestShaders.set(name, shader));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> client.shutdown());
        // LAST matches NeoForge's AFTER_WEATHER: terrain, particles and weather are already drawn.
        WorldRenderEvents.LAST.register(context -> {
            if (context.matrixStack() != null) {
                client.renderWorld(context.matrixStack(), context.tickCounter().getGameTimeDeltaPartialTick(false));
            }
        });
        HudRenderCallback.EVENT.register((graphics, tickCounter) ->
            client.renderHud(graphics, tickCounter.getGameTimeDeltaPartialTick(false)));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommands(dispatcher));
        // A way into the settings that does not need ModMenu installed. Vanilla's video settings is
        // where a player looking for a visual mod's options would look first, and the screen API can
        // add a widget there without a mixin - so a Minecraft update that moves the screen around
        // costs a missing button rather than a crash.
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (!(screen instanceof VideoSettingsScreen)) return;
            Screens.getButtons(screen).add(Button.builder(TempestOptionsScreen.buttonLabel(),
                    button -> minecraft.setScreen(client.settingsScreen(screen)))
                .bounds(SETTINGS_BUTTON_MARGIN, height - SETTINGS_BUTTON_HEIGHT - SETTINGS_BUTTON_MARGIN,
                    SETTINGS_BUTTON_WIDTH, SETTINGS_BUTTON_HEIGHT)
                .build());
        });
    }

    /** Bottom-left, where vanilla puts nothing and mods conventionally do. */
    private static final int SETTINGS_BUTTON_WIDTH = 110;
    private static final int SETTINGS_BUTTON_HEIGHT = 20;
    private static final int SETTINGS_BUTTON_MARGIN = 6;

    /** The live client, for the optional ModMenu entrypoint. */
    static TempestFxClient client() { return client; }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var strike = ClientCommandManager.literal("strike")
            .executes(context -> { client.debugStrike(10, "land"); return 1; })
            .then(ClientCommandManager.literal("--seed")
                .then(ClientCommandManager.argument("seed", LongArgumentType.longArg())
                    .executes(context -> {
                        client.debugStrike(10, "land", LongArgumentType.getLong(context, "seed"));
                        return 1;
                    })))
            .then(ClientCommandManager.argument("value", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                .executes(context -> {
                    client.debugStrike(DoubleArgumentType.getDouble(context, "value"), "land");
                    return 1;
                })
                .then(ClientCommandManager.literal("--seed")
                    .then(ClientCommandManager.argument("seed", LongArgumentType.longArg())
                        .executes(context -> {
                            client.debugStrike(DoubleArgumentType.getDouble(context, "value"), "land",
                                LongArgumentType.getLong(context, "seed"));
                            return 1;
                        })))
                .then(ClientCommandManager.argument("y", DoubleArgumentType.doubleArg(-2048, 2048))
                    .then(ClientCommandManager.argument("z", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                        .executes(context -> {
                            client.debugStrikeAt(DoubleArgumentType.getDouble(context, "value"),
                                DoubleArgumentType.getDouble(context, "y"),
                                DoubleArgumentType.getDouble(context, "z"), null);
                            return 1;
                        })
                        .then(ClientCommandManager.literal("--seed")
                            .then(ClientCommandManager.argument("seed", LongArgumentType.longArg())
                                .executes(context -> {
                                    client.debugStrikeAt(DoubleArgumentType.getDouble(context, "value"),
                                        DoubleArgumentType.getDouble(context, "y"),
                                        DoubleArgumentType.getDouble(context, "z"),
                                        LongArgumentType.getLong(context, "seed"));
                                    return 1;
                                }))))));
        for (String environment : new String[] { "water", "snow", "sand", "stone", "forest" }) {
            strike = strike.then(ClientCommandManager.literal(environment)
                .executes(context -> { client.debugStrike(10, environment); return 1; })
                .then(ClientCommandManager.literal("--seed")
                    .then(ClientCommandManager.argument("seed", LongArgumentType.longArg())
                        .executes(context -> {
                            client.debugStrike(10, environment, LongArgumentType.getLong(context, "seed"));
                            return 1;
                        }))));
        }
        var strikeCamera = ClientCommandManager.literal("strike-camera")
            .executes(context -> { client.debugStrike(10, "land"); return 1; })
            .then(ClientCommandManager.argument("distance", DoubleArgumentType.doubleArg(0, 512))
                .executes(context -> {
                    client.debugStrike(DoubleArgumentType.getDouble(context, "distance"), "land");
                    return 1;
                })
                .then(ClientCommandManager.literal("--seed")
                    .then(ClientCommandManager.argument("seed", LongArgumentType.longArg())
                        .executes(context -> {
                            client.debugStrike(DoubleArgumentType.getDouble(context, "distance"), "land",
                                LongArgumentType.getLong(context, "seed"));
                            return 1;
                        }))));
        var camera = ClientCommandManager.literal("camera")
            .then(ClientCommandManager.literal("cinematic")
                .executes(context -> { client.enableShowcaseCamera(); return 1; }))
            .then(ClientCommandManager.literal("off")
                .executes(context -> { client.disableShowcaseCamera(); return 1; }))
            .then(ClientCommandManager.literal("speed")
                .then(ClientCommandManager.argument("speed", DoubleArgumentType.doubleArg(0.01, 0.5))
                    .executes(context -> {
                        client.setShowcaseCameraSpeed(DoubleArgumentType.getDouble(context, "speed"));
                        return 1;
                    })));
        // One ambient discharge of a named archetype, at cloud height in front of the player. The
        // planner raises these on its own and rarely, so this is how they are actually inspected.
        var sky = ClientCommandManager.literal("sky")
            .executes(context -> { client.debugSkyDischarge("cloud_to_cloud", 160); return 1; });
        for (String type : new String[] { "cloud_to_cloud", "intracloud", "megaflash",
            "positive_cloud_to_ground", "negative_cloud_to_ground" }) {
            sky = sky.then(ClientCommandManager.literal(type)
                .executes(context -> { client.debugSkyDischarge(type, 160); return 1; })
                .then(ClientCommandManager.argument("distance", DoubleArgumentType.doubleArg(16, 1024))
                    .executes(context -> {
                        client.debugSkyDischarge(type, DoubleArgumentType.getDouble(context, "distance"));
                        return 1;
                    })));
        }

        // The two events above the storm. Rare enough by design that a command is the only way to
        // look at one on purpose.
        var aloft = ClientCommandManager.literal("aloft")
            .executes(context -> { client.debugLuminousEvent("red_sprite", 220); return 1; });
        for (String type : new String[] { "red_sprite", "blue_jet" }) {
            aloft = aloft.then(ClientCommandManager.literal(type)
                .executes(context -> { client.debugLuminousEvent(type, 220); return 1; })
                .then(ClientCommandManager.argument("distance", DoubleArgumentType.doubleArg(32, 1024))
                    .executes(context -> {
                        client.debugLuminousEvent(type, DoubleArgumentType.getDouble(context, "distance"));
                        return 1;
                    })));
        }

        var settings = ClientCommandManager.literal("settings")
            .executes(context -> {
                // Deferred: the command runs while the chat screen is still up, and setScreen from
                // inside it would be torn down again the moment chat closes.
                net.minecraft.client.Minecraft.getInstance().execute(() -> client.openSettings(null));
                return 1;
            });

        var reload = ClientCommandManager.literal("reload")
            .executes(context -> {
                context.getSource().sendFeedback(net.minecraft.network.chat.Component.translatable(
                    "commands.tempestfx.reloaded", client.reloadConfig().name()));
                return 1;
            });

        dispatcher.register(ClientCommandManager.literal("tempestfx")
            .then(settings)
            .then(reload)
            .then(strike)
            .then(strikeCamera)
            .then(sky)
            .then(aloft)
            .then(camera)
            .then(ClientCommandManager.literal("directhit")
                .executes(context -> { client.debugDirectHit(); return 1; }))
            .then(ClientCommandManager.literal("summon")
                .executes(context -> { client.summonRealBolt(); return 1; }))
            .then(ClientCommandManager.literal("ball")
                .executes(context -> { client.summonBallLightning(); return 1; }))
            .then(ClientCommandManager.literal("roll")
                .executes(context -> { client.debugThunderRoll(0, 0); return 1; })
                .then(ClientCommandManager.argument("seconds", DoubleArgumentType.doubleArg(0.5, 16))
                    .executes(context -> {
                        client.debugThunderRoll(DoubleArgumentType.getDouble(context, "seconds"), 0);
                        return 1;
                    })
                    .then(ClientCommandManager.argument("flashes", IntegerArgumentType.integer(1, 300))
                        .executes(context -> {
                            client.debugThunderRoll(DoubleArgumentType.getDouble(context, "seconds"),
                                IntegerArgumentType.getInteger(context, "flashes"));
                            return 1;
                        }))))
            .then(ClientCommandManager.literal("stress")
                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 100))
                    .executes(context -> {
                        client.stress(IntegerArgumentType.getInteger(context, "count"));
                        return 1;
                    }))));
    }

    private static final class FabricPlatform implements ClientPlatform {
        @Override
        public Path configDirectory() { return FabricLoader.getInstance().getConfigDir(); }

        @Override
        public Vec3d cameraPosition() {
            var position = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            return new Vec3d(position.x, position.y, position.z);
        }

        @Override
        public void playThunder(ThunderProfile profile, Vec3d position, float volume, float pitch) {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            var sound = TempestSounds.event(profile);
            if (sound == null) return;
            level.playLocalSound(position.x(), position.y(), position.z(), sound,
                SoundSource.WEATHER, volume, pitch, false);
        }
    }
}
