package dev.tempestfx.neoforge;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client bootstrap.
 */
@Mod(value = TempestFx.MOD_ID, dist = Dist.CLIENT)
public final class TempestFxNeoForgeClient {
    private final TempestFxClient client;

    public TempestFxNeoForgeClient(IEventBus modBus, ModContainer container) {
        client = new TempestFxClient(new NeoForgePlatform());
        // NeoForge's own mod-list config button. No dependency, no entrypoint file.
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (minecraft, parent) -> client.settingsScreen(parent));
        modBus.addListener(this::registerRenderers);
        modBus.addListener(this::registerShaders);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::renderWorld);
        NeoForge.EVENT_BUS.addListener(this::renderHud);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::addSettingsButton);
        // NeoForge has no client-stopping event in 21.1. GPU and native resources are instead
        // released on level change and by the idle timers, which covers every case that matters
        // while the process is alive; the OS reclaims the rest at exit.
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.LIGHTNING_BOLT, EmptyLightningRenderer::new);
        var ballLightning = TempestEntities.ballLightning();
        if (ballLightning != null) {
            event.registerEntityRenderer(ballLightning, BallLightningEntityRenderer::new);
        } else {
            TempestFx.log().error("Ball lightning entity type is missing; its renderer was skipped");
        }
    }

    private void registerShaders(RegisterShadersEvent event) {
        try {
            // Core shaders resolve in the minecraft namespace, so the bundled files live there too.
            for (String name : TempestShaders.NAMES) {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), name,
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> TempestShaders.set(name, shader));
            }
        } catch (Exception failure) {
            TempestFx.log().warn("Custom core shaders unavailable, using vanilla fallback", failure);
        }
    }

    private void tick(ClientTickEvent.Post event) { client.tick(Minecraft.getInstance()); }

    private void renderWorld(RenderLevelStageEvent event) {
        // AFTER_WEATHER matches Fabric's LAST: terrain, particles and weather are already drawn.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        client.renderWorld(event.getPoseStack(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    private void renderHud(RenderGuiEvent.Post event) {
        client.renderHud(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    /**
     * A way into the settings from vanilla's own video settings.
     *
     * <p>NeoForge already offers the config button on the mod list, but that is not where a player
     * looking for a visual mod's options looks first. Done through the screen event rather than a
     * mixin, so a Minecraft update that moves the screen around costs a missing button rather than a
     * crash.
     */
    private void addSettingsButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof VideoSettingsScreen screen)) return;
        event.addListener(Button.builder(TempestOptionsScreen.buttonLabel(),
                button -> Minecraft.getInstance().setScreen(client.settingsScreen(screen)))
            .bounds(SETTINGS_BUTTON_MARGIN,
                screen.height - SETTINGS_BUTTON_HEIGHT - SETTINGS_BUTTON_MARGIN,
                SETTINGS_BUTTON_WIDTH, SETTINGS_BUTTON_HEIGHT)
            .build());
    }

    /** Bottom-left, where vanilla puts nothing and mods conventionally do. */
    private static final int SETTINGS_BUTTON_WIDTH = 110;
    private static final int SETTINGS_BUTTON_HEIGHT = 20;
    private static final int SETTINGS_BUTTON_MARGIN = 6;

    private void registerCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> strike = Commands.literal("strike")
            .executes(context -> { client.debugStrike(10, "land"); return 1; })
            .then(Commands.literal("--seed")
                .then(Commands.argument("seed", LongArgumentType.longArg())
                    .executes(context -> {
                        client.debugStrike(10, "land", LongArgumentType.getLong(context, "seed"));
                        return 1;
                    })))
            .then(Commands.argument("value", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                .executes(context -> {
                    client.debugStrike(DoubleArgumentType.getDouble(context, "value"), "land");
                    return 1;
                })
                .then(Commands.literal("--seed")
                    .then(Commands.argument("seed", LongArgumentType.longArg())
                        .executes(context -> {
                            client.debugStrike(DoubleArgumentType.getDouble(context, "value"), "land",
                                LongArgumentType.getLong(context, "seed"));
                            return 1;
                        })))
                .then(Commands.argument("y", DoubleArgumentType.doubleArg(-2048, 2048))
                    .then(Commands.argument("z", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                        .executes(context -> {
                            client.debugStrikeAt(DoubleArgumentType.getDouble(context, "value"),
                                DoubleArgumentType.getDouble(context, "y"),
                                DoubleArgumentType.getDouble(context, "z"), null);
                            return 1;
                        })
                        .then(Commands.literal("--seed")
                            .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(context -> {
                                    client.debugStrikeAt(DoubleArgumentType.getDouble(context, "value"),
                                        DoubleArgumentType.getDouble(context, "y"),
                                        DoubleArgumentType.getDouble(context, "z"),
                                        LongArgumentType.getLong(context, "seed"));
                                    return 1;
                                }))))));
        for (String environment : new String[] { "water", "snow", "sand", "stone", "forest" }) {
            strike = strike.then(Commands.literal(environment)
                .executes(context -> { client.debugStrike(10, environment); return 1; })
                .then(Commands.literal("--seed")
                    .then(Commands.argument("seed", LongArgumentType.longArg())
                        .executes(context -> {
                            client.debugStrike(10, environment, LongArgumentType.getLong(context, "seed"));
                            return 1;
                        }))));
        }
        LiteralArgumentBuilder<CommandSourceStack> strikeCamera = Commands.literal("strike-camera")
            .executes(context -> { client.debugStrike(10, "land"); return 1; })
            .then(Commands.argument("distance", DoubleArgumentType.doubleArg(0, 512))
                .executes(context -> {
                    client.debugStrike(DoubleArgumentType.getDouble(context, "distance"), "land");
                    return 1;
                })
                .then(Commands.literal("--seed")
                    .then(Commands.argument("seed", LongArgumentType.longArg())
                        .executes(context -> {
                            client.debugStrike(DoubleArgumentType.getDouble(context, "distance"), "land",
                                LongArgumentType.getLong(context, "seed"));
                            return 1;
                        }))));
        LiteralArgumentBuilder<CommandSourceStack> camera = Commands.literal("camera")
            .then(Commands.literal("cinematic")
                .executes(context -> { client.enableShowcaseCamera(); return 1; }))
            .then(Commands.literal("off")
                .executes(context -> { client.disableShowcaseCamera(); return 1; }))
            .then(Commands.literal("speed")
                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.01, 0.5))
                    .executes(context -> {
                        client.setShowcaseCameraSpeed(DoubleArgumentType.getDouble(context, "speed"));
                        return 1;
                    })));
        // One ambient discharge of a named archetype, at cloud height in front of the player. The
        // planner raises these on its own and rarely, so this is how they are actually inspected.
        LiteralArgumentBuilder<CommandSourceStack> sky = Commands.literal("sky")
            .executes(context -> { client.debugSkyDischarge("cloud_to_cloud", 160); return 1; });
        for (String type : new String[] { "cloud_to_cloud", "intracloud", "megaflash",
            "positive_cloud_to_ground", "negative_cloud_to_ground" }) {
            sky = sky.then(Commands.literal(type)
                .executes(context -> { client.debugSkyDischarge(type, 160); return 1; })
                .then(Commands.argument("distance", DoubleArgumentType.doubleArg(16, 1024))
                    .executes(context -> {
                        client.debugSkyDischarge(type, DoubleArgumentType.getDouble(context, "distance"));
                        return 1;
                    })));
        }

        // The two events above the storm. Rare enough by design that a command is the only way to
        // look at one on purpose.
        LiteralArgumentBuilder<CommandSourceStack> aloft = Commands.literal("aloft")
            .executes(context -> { client.debugLuminousEvent("red_sprite", 220); return 1; });
        for (String type : new String[] { "red_sprite", "blue_jet" }) {
            aloft = aloft.then(Commands.literal(type)
                .executes(context -> { client.debugLuminousEvent(type, 220); return 1; })
                .then(Commands.argument("distance", DoubleArgumentType.doubleArg(32, 1024))
                    .executes(context -> {
                        client.debugLuminousEvent(type, DoubleArgumentType.getDouble(context, "distance"));
                        return 1;
                    })));
        }

        var settings = Commands.literal("settings")
            .executes(context -> {
                // Deferred: the command runs while the chat screen is still up, and setScreen from
                // inside it would be torn down again the moment chat closes.
                net.minecraft.client.Minecraft.getInstance().execute(() -> client.openSettings(null));
                return 1;
            });

        var reload = Commands.literal("reload")
            .executes(context -> {
                var preset = client.reloadConfig().name();
                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                    "commands.tempestfx.reloaded", preset), false);
                return 1;
            });

        event.getDispatcher().register(Commands.literal("tempestfx")
            .then(settings)
            .then(reload)
            .then(strike)
            .then(strikeCamera)
            .then(sky)
            .then(aloft)
            .then(camera)
            .then(Commands.literal("directhit").executes(context -> { client.debugDirectHit(); return 1; }))
            .then(Commands.literal("summon").executes(context -> { client.summonRealBolt(); return 1; }))
            .then(Commands.literal("ball").executes(context -> { client.summonBallLightning(); return 1; }))
            .then(Commands.literal("roll")
                .executes(context -> { client.debugThunderRoll(0, 0); return 1; })
                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.5, 16))
                    .executes(context -> {
                        client.debugThunderRoll(DoubleArgumentType.getDouble(context, "seconds"), 0);
                        return 1;
                    })
                    .then(Commands.argument("flashes", IntegerArgumentType.integer(1, 300))
                        .executes(context -> {
                            client.debugThunderRoll(DoubleArgumentType.getDouble(context, "seconds"),
                                IntegerArgumentType.getInteger(context, "flashes"));
                            return 1;
                        }))))
            .then(Commands.literal("stress")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                    .executes(context -> {
                        client.stress(IntegerArgumentType.getInteger(context, "count"));
                        return 1;
                    }))));
    }

    private static final class NeoForgePlatform implements ClientPlatform {
        @Override
        public Path configDirectory() { return FMLPaths.CONFIGDIR.get(); }

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
