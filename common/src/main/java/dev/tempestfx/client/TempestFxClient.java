package dev.tempestfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tempestfx.TempestFx;
import dev.tempestfx.api.LightningEffect;
import dev.tempestfx.api.LightningKind;
import dev.tempestfx.api.LightningEnvironment;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.api.ParticleFamily;
import dev.tempestfx.api.ThunderRoll;
import dev.tempestfx.particle.FxParticleMaterial;
import dev.tempestfx.api.StrikeTarget;
import dev.tempestfx.api.TempestFxApi;
import dev.tempestfx.audio.ThunderMath;
import dev.tempestfx.audio.ThunderProfile;
import dev.tempestfx.audio.DistantBoltCue;
import dev.tempestfx.audio.ThunderPulse;
import dev.tempestfx.audio.ThunderRollSystem;
import dev.tempestfx.audio.ThunderSystem;
import dev.tempestfx.compat.BloomBackend;
import dev.tempestfx.compat.BloomBackendFactory;
import dev.tempestfx.compat.RenderCompatibilityMode;
import dev.tempestfx.compat.ShaderEnvironmentDetector;
import dev.tempestfx.config.ConfigManager;
import dev.tempestfx.config.QualityPreset;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.effect.AshImprint;
import dev.tempestfx.effect.ActiveLightningEffect;
import dev.tempestfx.effect.FlashSimulation;
import dev.tempestfx.effect.AshImprintSystem;
import dev.tempestfx.effect.CameraImpulseSystem;
import dev.tempestfx.effect.DischargeTarget;
import dev.tempestfx.effect.EffectManager;
import dev.tempestfx.effect.EntityDischargeSystem;
import dev.tempestfx.effect.LightningEffectFactory;
import dev.tempestfx.effect.ScreenFlashSystem;
import dev.tempestfx.effect.DistantBoltSystem;
import dev.tempestfx.effect.ThunderRumbleCameraEffect;
import dev.tempestfx.effect.TransientLightSystem;
import dev.tempestfx.effect.WorldFlashSystem;
import dev.tempestfx.entity.BallLightning;
import dev.tempestfx.entity.TempestEntities;
import dev.tempestfx.event.FxEventBus;
import dev.tempestfx.lightning.MidpointDisplacementStrategy;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.particle.AshImprintEmitter;
import dev.tempestfx.particle.FxParticleMaterial;
import dev.tempestfx.particle.FxParticleSystem;
import dev.tempestfx.particle.ImpactParticleSpawnStrategy;
import dev.tempestfx.platform.ClientPlatform;
import dev.tempestfx.particle.BallLightningEmitter;
import dev.tempestfx.render.AirDistortionSystem;
import dev.tempestfx.render.BallLightningDraw;
import dev.tempestfx.render.FxBatchTarget;
import dev.tempestfx.render.NativeFxBatchTarget;
import dev.tempestfx.render.TempestRenderTypes;
import dev.tempestfx.render.TempestShaders;
import dev.tempestfx.render.ShaderPackProfile;
import dev.tempestfx.render.VanillaFxBatchTarget;
import dev.tempestfx.render.WorldFxRenderer;
import dev.tempestfx.render.SurfaceLightingPolicy;
import dev.tempestfx.render.PackChannelRenderer;
import dev.tempestfx.render.composite.EffectCompositor;
import dev.tempestfx.render.composite.SceneLightField;
import org.joml.Matrix4f;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.tempestfx.render.composite.EffectCompositors;
import dev.tempestfx.render.gl.FxPrograms;
import dev.tempestfx.render.gl.FxStateGuard;
import dev.tempestfx.server.TempestFxServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Client orchestrator.
 *
 * <p>Responsibilities are on purpose thin: own the subsystems, wire them to the event bus, forward
 * the tick and the render passes, and translate between Minecraft state and the loader-agnostic
 * simulation. All simulation lives in {@code effect}, all geometry in {@code lightning}, all drawing
 * in {@code render}.
 */
public final class TempestFxClient {
    /** Enough for any plausible number of spheres on screen, and a bound on a frame that never draws. */
    private static final int MAX_DEFERRED_SPHERES = 64;

    private final ClientPlatform platform;
    private final ConfigManager configManager;
    /** Replaced wholesale by {@link #reloadConfig()}; no subsystem keeps a copy. */
    private TempestConfig config;

    private final FxEventBus events = new FxEventBus();
    private final EffectManager effects = new EffectManager(new LightningEffectFactory(new MidpointDisplacementStrategy()));
    private FxParticleSystem particles;
    private final ScreenFlashSystem screenFlash = new ScreenFlashSystem();
    private final CameraImpulseSystem cameraImpulse = new CameraImpulseSystem();
    private final TransientLightSystem lights = new TransientLightSystem();
    private final WorldFlashSystem worldFlash = new WorldFlashSystem();
    private final EntityDischargeSystem discharges = new EntityDischargeSystem();
    private final AshImprintSystem imprints = new AshImprintSystem();
    private final FlashSimulation flashes = new FlashSimulation(effects);
    private final List<BallLightning> ballLightning = new ArrayList<>();
    private final ThunderSystem thunder;
    private final ThunderRollSystem thunderRolls;
    private final ThunderRumbleCameraEffect rumble = new ThunderRumbleCameraEffect();
    private final DistantBoltSystem distantBolts = new DistantBoltSystem();
    private final ShowcaseCameraController showcaseCamera = new ShowcaseCameraController();

    private final StrikeIngest ingest = new StrikeIngest();
    private final WorldFxRenderer worldRenderer = new WorldFxRenderer();
    private final PackChannelRenderer packChannels = new PackChannelRenderer();
    private boolean channelsInPack;
    private boolean packFramePrepared;
    private boolean packFrameDrawn;
    /** The mod's own programs; the whole native path depends on them and nothing else does. */
    private final FxPrograms programs = new FxPrograms();
    private final NativeFxBatchTarget nativeTarget = new NativeFxBatchTarget(programs);
    private final VanillaFxBatchTarget vanillaTarget = new VanillaFxBatchTarget();
    private final FxStateGuard worldGuard = new FxStateGuard();
    private final AirDistortionSystem distortion = new AirDistortionSystem();
    private EffectCompositor compositor;
    /**
     * Spheres offered by the entity dispatcher this frame, drained by the world pass.
     *
     * <p>Bounded, so a frame where the world pass never runs cannot grow it without limit; the entity
     * renderer draws the overflow itself.
     */
    private final List<BallLightningDraw> sphereDraws = new ArrayList<>();

    private final ShaderEnvironmentDetector shaders;
    private final RenderCompatibilityMode detectedCompatibility;
    private BloomBackend bloomBackend;

    private ClientLevel currentLevel;
    private boolean eventThreadBound;
    private boolean automatedSmokeStrikeTriggered;
    private int smokeStrikeCountdown;
    private final DevelopmentCapture developmentCapture = new DevelopmentCapture();
    private long lastWorldFrameNanos;
    private boolean lastIsolationFailed;
    private String lastWorldPath = "not yet drawn";

    public TempestFxClient(ClientPlatform platform) {
        this.platform = platform;
        this.configManager = new ConfigManager(platform.configDirectory());
        this.config = configManager.load();
        this.particles = new FxParticleSystem(config.performance.maxParticles, new ImpactParticleSpawnStrategy());
        this.thunder = new ThunderSystem(platform);
        this.thunderRolls = new ThunderRollSystem(platform, thunder.budget());
        this.shaders = new ShaderEnvironmentDetector();
        this.detectedCompatibility = shaders.detect();
        this.bloomBackend = BloomBackendFactory.create(config.compatibility.bloomMode, compatibilityMode());
        this.compositor = EffectCompositors.create(config.compatibility.effectCompositor, programs);

        TempestShaders.setEnabled(config.compatibility.customShaders);
        programs.setEnabled(config.compatibility.customShaders);
        // Only used on the fallback path; a shader pack can be switched on and off without restarting.
        TempestShaders.setForeignPipelineProbe(() -> shaders.shaderPackActive(compatibilityMode()));
        TempestFx.log().info("Render pipeline: {}; custom shaders {}; effect compositor {}",
            compatibilityMode(), config.compatibility.customShaders ? "allowed" : "off by config",
            config.compatibility.effectCompositor ? "on" : "off by config");
        thunder.setPlaybackListener(this::onThunderPlayed);
        thunderRolls.setPulseListener(this::onRollPulse);
        thunderRolls.setBoltListener(this::onRollBolt);
        registerSubsystems();
        TempestFxApi.Internal.install(events::publish, this::triggerThunderRoll);
        TempestFxHooks.install(this);
    }

    /**
     * One strike event, many independent subscribers. Nothing here knows about the others, so a
     * feature can be disabled or replaced without touching the rest of the storm.
     */
    private void registerSubsystems() {
        events.subscribeStrike(event -> {
            if (!config.general.enabled || currentLevel == null) return;
            flashes.enqueue(event);
        });
        // Last, so an integration sees the strike only once the mod's own subsystems have accepted
        // it, and so a slow listener delays nothing that is already on screen.
    }

    /** Contact effects never publish another geometry event or start another flash. */
    private void onFlashContact(LightningStrikeFxEvent event) {
        if (!config.general.enabled || currentLevel == null) return;
        if (config.general.reducedFlashing && !event.primary()) return;
        if (event.kind().contactsGround()) {
            effects.onContact(event, platform.cameraPosition(), config);
            emitImpactParticles(event);
            // Keep the cheap fallback state even when surface shading is requested. Actual frame
            // availability decides which path draws it; a failed depth attachment must not erase light.
            lights.onStrike(event, config);
        }
        screenFlash.onStrike(event, platform.cameraPosition(), config);
        // Preserve fallback timing even before a frame discovers an unsupported depth target.
        worldFlash.onStrike(event, platform.cameraPosition(), config);
        if (!config.realistic() && event.kind().contactsGround()) {
            cameraImpulse.onStrike(event, platform.cameraPosition(), config);
            if (event.primary()) { startEntityDischarges(event); leaveAshImprint(event); }
        }
        playStrikeAudio(event);
        if (!event.primary()) TempestFxApi.Internal.fireStrike(event);
    }

    // ------------------------------------------------------------------ lifecycle

    public void tick(Minecraft minecraft) {
        if (!eventThreadBound) {
            // The mod constructor may run on a loader worker thread; the tick never does.
            events.bindToCurrentThread();
            eventThreadBound = true;
        }
        if (minecraft.level != currentLevel) {
            onLevelChanged(minecraft.level);
            return;
        }
        events.drain();

        ingest.tick();
        particles.tick();
        screenFlash.tick();
        cameraImpulse.tick();
        lights.tick();
        worldFlash.tick();
        imprints.tick();
        thunder.tick();
        thunderRolls.tick(config);
        rumble.tick();
        distantBolts.tick();
        showcaseCamera.tick(minecraft);
        flashes.tick(platform.cameraPosition(), config, this::onFlashContact, TempestFxApi.Internal::fireStrike);
        tickBallLightning();
        if (currentLevel != null) {
            ClientLevel level = currentLevel;
            discharges.tick(config, entityId -> StrikeIngest.snapshot(level, entityId), level.getGameTime());
        }

        boolean busy = !sceneIsEmpty();
        nativeTarget.tick(busy);
        vanillaTarget.tick(busy);
        compositor.tick(busy);

        maybeRunSmokeStrike(minecraft);
        developmentCapture.tick(minecraft, this);
    }

    private void onLevelChanged(ClientLevel level) {
        packFramePrepared = packFrameDrawn = channelsInPack = false;
        showcaseCamera.disable(Minecraft.getInstance(), false);
        currentLevel = level;
        ingest.clear();
        effects.clear();
        particles.clear();
        lights.clear();
        worldFlash.clear();
        discharges.clear();
        imprints.clear();
        flashes.clear();
        ballLightning.clear();
        thunder.clear();
        thunderRolls.clear();
        rumble.clear();
        distantBolts.clear();
        screenFlash.clear();
        cameraImpulse.clear();
        events.clearPending();
        sphereDraws.clear();
        distortion.clear();
        // A dimension change is the natural point to hand the framebuffers back; the next storm
        // allocates them again. It is also a fresh chance for the programs to compile.
        compositor.close();
        programs.reload();
        packChannels.close();
    }

    /**
     * Re-reads both config files without restarting the game.
     *
     * @return the quality preset now in force, for the command to report
     */
    public QualityPreset reloadConfig() {
        config = configManager.load();
        applyRenderConfig();
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) server.execute(() -> TempestFxServer.load(platform.configDirectory()));
        return config.performance.qualityPreset;
    }

    /** Opens the settings screen over whatever is on screen now. */
    public void openSettings(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(settingsScreen(parent));
    }

    /** The settings screen, for the loaders' own mod-list buttons. */
    public Screen settingsScreen(Screen parent) {
        return new TempestOptionsScreen(parent, config, () -> {
            configManager.saveQuietly();
            applyRenderConfig();
        });
    }

    private void applyRenderConfig() {
        packFramePrepared = packFrameDrawn = channelsInPack = false;
        packChannels.close();
        if (particles.capacity() != config.performance.maxParticles) {
            particles.clear();
            particles = new FxParticleSystem(config.performance.maxParticles, new ImpactParticleSpawnStrategy());
        }
        compositor.close();
        compositor = EffectCompositors.create(config.compatibility.effectCompositor, programs);
        TempestShaders.setEnabled(config.compatibility.customShaders);
        programs.setEnabled(config.compatibility.customShaders);
        programs.reload();
        bloomBackend = BloomBackendFactory.create(config.compatibility.bloomMode, compatibilityMode());
        // Drop pending flashes on accessibility changes; they may have been scheduled under the old mode.
        flashes.clear();
        screenFlash.clear();
        worldFlash.clear();
        cameraImpulse.clear();
        rumble.clear();
        distantBolts.clear();
        if (!config.general.enabled) onLevelChanged(currentLevel);
    }

    /** Called by the loader when the client stops, so native buffers and GL targets are released. */
    public void shutdown() {
        showcaseCamera.disable(Minecraft.getInstance(), false);
        TempestFxHooks.uninstall();
        TempestFxApi.Internal.uninstall();
        nativeTarget.close();
        vanillaTarget.close();
        compositor.close();
        programs.close();
        packChannels.close();
        TempestShaders.clear();
    }

    // ------------------------------------------------------------------ strike ingest

    /** Hook target for {@code ClientLevel#addEntity}. */
    public void onLightningSpawn(ClientLevel level, LightningBolt bolt) {
        if (!config.general.enabled || level != currentLevel) return;
        LightningStrikeFxEvent event = ingest.ingest(level, bolt);
        if (event != null) events.publish(event);
    }

    /** Hook target for {@code ClientLevel#addEntity}: keeps a cheap list instead of querying. */
    public void onBallLightningSpawn(BallLightning ball) {
        if (config.general.enabled && config.impact.ballLightningEffects) ballLightning.add(ball);
    }

    /**
     * Takes a sphere off the entity dispatcher so the world pass can draw it.
     *
     * @return {@code false} when the caller has to draw it itself, which is the case whenever the
     *     world pass will not run this frame
     */
    public boolean deferBallLightning(BallLightningDraw sphere) {
        if (!config.general.enabled || currentLevel == null) return false;
        if (sphereDraws.size() >= MAX_DEFERRED_SPHERES) return false;
        sphereDraws.add(sphere);
        return true;
    }

    /** Sheds sparks and crackle from every tracked sphere, and drops the ones that are gone. */
    private void tickBallLightning() {
        for (int index = ballLightning.size() - 1; index >= 0; index--) {
            BallLightning ball = ballLightning.get(index);
            if (ball.isRemoved() || ball.level() != currentLevel) {
                ballLightning.remove(index);
                continue;
            }
            if (!config.impact.ballLightningEffects) continue;
            float output = ball.output(0f);
            if (output <= 0.05f) continue;
            Vec3d center = new Vec3d(ball.getX(), ball.getY(), ball.getZ());
            float radius = ball.nominalRadius();
            long tick = ball.tickCount;
            particles.emit(4, material -> config.impact.sparks || material == FxParticleMaterial.EMBER,
                sink -> BallLightningEmitter.spawn(sink, center, radius, output, ball.visualSeed(), tick));
            if (ball.tickCount % 11 == 0 && config.audio.thunderVolume > 0) {
                double distance = platform.cameraPosition().distanceTo(center);
                float gain = ThunderMath.thunderGain(distance, 0.22f, config.audio.thunderVolume);
                float volume = ThunderMath.spatialVolume(distance, gain);
                if (volume > 0) thunder.playIncidental(ThunderProfile.ELECTRIC_ARC, center, volume, 1.35f);
            }
        }
    }

    private void emitImpactParticles(LightningStrikeFxEvent event) {
        double distance = platform.cameraPosition().distanceTo(event.position());
        int budget = config.particleBudget(distance);
        if (config.realistic()) budget /= 10;
        if (budget <= 0) return;
        particles.emit(event, budget, material -> allowedByStrike(event, material) && switch (material) {
            case SPARK, MICRO_ARC -> config.impact.sparks;
            case SMOKE, STEAM -> config.impact.smoke;
            case DUST, DEBRIS -> config.impact.debris && event.environment().dusty();
            case ASH, EMBER -> config.impact.ash;
            case WATER -> event.environment().water();
        });
    }

    /**
     * Whether a strike's own family selection permits this material.
     */
    private static boolean allowedByStrike(LightningStrikeFxEvent event, FxParticleMaterial material) {
        Set<ParticleFamily> selection = event.options().particles();
        if (selection == null) return true;
        for (ParticleFamily family : selection) {
            if (family.material() == material) return true;
        }
        return false;
    }

    /**
     * Camera response to a rolling thunder pulse.
     */
    private void onRollPulse(ThunderPulse pulse) {
        double distance = platform.cameraPosition().distanceTo(pulse.position());
        float audible = ThunderMath.thunderGain(distance, 1f, 1f) * pulse.gain();
        long seed = StrikeSeed.of(pulse.position().x(), pulse.position().y(), pulse.position().z(),
            pulse.delayTicks());

        if (!config.camera.cameraImpulse || config.general.reducedFlashing) return;
        rumble.onTransient(pulse.impact() * audible * config.camera.impulseStrength * 2.6f, seed);
    }

    /** Ordinary heavy layers still register, just far more gently than a roll pulse. */
    private void onThunderPlayed(ThunderProfile profile, Vec3d position, float volume) {
        if (!profile.shakesTheAir() || !config.camera.cameraImpulse || config.general.reducedFlashing) return;
        double distance = platform.cameraPosition().distanceTo(position);
        float gain = ThunderMath.perceivedGain(distance, volume);
        if (gain <= 0.12f) return;
        rumble.onTransient(gain * config.camera.impulseStrength * 1.1f,
            StrikeSeed.of(position.x(), position.y(), position.z(), profile.ordinal()));
    }

    /**
     * Audio for a strike: either a rolling thunder event, or the ordinary cue, never both in full.
     */
    private void playStrikeAudio(LightningStrikeFxEvent event) {
        boolean roll = !config.realistic() && event.primary() && thunderRolls.onStrike(platform.cameraPosition(), event.position(),
            event.seed(), event.intensity(), config, viewDistance());
        thunder.onStrike(event, platform.cameraPosition(), config, roll);
    }

    /**
     * A distant channel scheduled by the roll.
     */
    private void onRollBolt(DistantBoltCue cue) {
        if (config.realistic()) return;
        if (distantBolts.onCue(cue, config) != null && cue.intensity() > 0.8f) {
            worldFlash.pulse(1, config);
        }
    }

    private void startEntityDischarges(LightningStrikeFxEvent event) {
        if (currentLevel == null || !config.impact.entityDischarge) return;
        List<DischargeTarget> nearby =
            ingest.collectDischargeTargets(currentLevel, event.position(), config.impact.entityDischargeRadius);
        if (nearby.isEmpty()) return;
        int started = discharges.onStrike(event, config, nearby);
        if (started > 0 && config.audio.customThunder && config.audio.thunderVolume > 0) {
            double distance = platform.cameraPosition().distanceTo(event.position());
            float gain = ThunderMath.thunderGain(distance, 0.35f, config.audio.thunderVolume);
            float volume = ThunderMath.spatialVolume(distance, gain);
            // Routed through the thunder system so it shares the voice budget.
            if (volume > 0) thunder.playIncidental(ThunderProfile.ELECTRIC_ARC, event.position(), volume, 1f);
        }
    }

    private void leaveAshImprint(LightningStrikeFxEvent event) {
        AshImprint imprint = imprints.onStrike(event, config);
        if (imprint == null) return;
        int budget = Math.max(16, config.particleBudget(platform.cameraPosition().distanceTo(event.position())) / 2);
        particles.emit(budget, material -> config.impact.ash || material == FxParticleMaterial.SMOKE,
            sink -> AshImprintEmitter.spawn(sink, imprint.position(), imprint.radius(),
                Math.max(1.2f, event.target().height()), imprint.seed(), budget));
    }

    // ------------------------------------------------------------------ rendering

    /** Recognized lightning geometry must enter before the pack's later weather/composite stages. */
    public void renderPackChannels(PoseStack stack, float partialTick) {
        packFramePrepared = packFrameDrawn = false;
        if (!config.general.enabled || currentLevel == null || !config.compatibility.customShaders || !config.compatibility.packNativeChannels
            || !shaders.shaderPackActive(compatibilityMode())) return;
        var scene = scene();
        if (scene.lightning().isEmpty() && scene.distantBolts().isEmpty()) return;
        prepareEffectFrames(scene, partialTick);
        packFramePrepared = true;
        packFrameDrawn = packChannels.draw(scene, stack, platform.cameraPosition(), partialTick, config);
    }

    private void prepareEffectFrames(WorldFxRenderer.Scene scene, float partialTick) {
        long now = System.nanoTime();
        float frameTicks = lastWorldFrameNanos == 0 ? 1f / 3f : (now - lastWorldFrameNanos) / 50_000_000f;
        lastWorldFrameNanos = now;
        for (ActiveLightningEffect effect : scene.lightning()) effect.prepareFrame(partialTick, frameTicks);
        for (ActiveLightningEffect effect : scene.distantBolts()) effect.prepareFrame(partialTick, frameTicks);
    }

    /**
     * The world pass.
     *
     * <p>Geometry is emitted exactly once, into whichever target the compositor opened. Nothing in
     * here, and nothing below it, knows whether that is a framebuffer of the mod's own or the frame
     * the game had bound - which is the entire reason the effect behaves the same under every shader
     * loader.
     */
    public void renderWorld(PoseStack stack, float partialTick) {
        if (!config.general.enabled || currentLevel == null) return;
        WorldFxRenderer.Scene scene = scene();
        if (!packFramePrepared) prepareEffectFrames(scene, partialTick);
        channelsInPack = packFrameDrawn;
        packFramePrepared = packFrameDrawn = false;
        if (scene.isEmpty()) return;

        Vec3d camera = platform.cameraPosition();
        // The mod's own programs when they compiled, Minecraft's shader objects when they did not.
        boolean own = nativeTarget.available();
        FxBatchTarget target = own ? nativeTarget : vanillaTarget;
        boolean bloomActive = bloomBackend.isAvailable();
        // Captured before anything is bound, and the only thing that puts the frame back. The native
        // path sets GL state with raw calls, so it restores state as well; the fallback goes through
        // the game's own state manager and has to be restored through it.
        worldGuard.capture(own);
        // Only isolated when the mod's own programs are drawing. On the fallback path the geometry goes
        // through Minecraft's shader objects, which a pack is free to redirect into its own buffers, so
        // a private attachment would collect nothing and cost a clear and a composite to prove it.
        boolean isolated = own && compositor.beginWorldPass();
        lastIsolationFailed = !isolated;
        lastWorldPath = own ? compositor.status() : "direct: vanilla programs";
        stack.pushPose();
        stack.translate(-camera.x(), -camera.y(), -camera.z());
        try {
            compositor.lighting(isolated && config.realistic() && !hideLightningFlash()
                ? SceneLightField.from(scene.lightning(), new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.last().pose()),
                    RenderSystem.getProjectionMatrix(), partialTick, config) : SceneLightField.NONE);
            distortion.capture(scene.shockwaves(), stack, camera, partialTick, config);
            if (bloomActive) bloomBackend.begin();
            worldRenderer.render(scene, stack, target, camera, partialTick, config,
                bloomBackend.emissiveBoost(), shaderPackProfile(isolated && own),
                SurfaceLightingPolicy.useSurfacePath(config, isolated, hideLightningFlash()), channelsInPack || !drawsOwnLightning());
        } finally {
            try {
                try {
                    if (bloomActive) bloomBackend.end();
                    stack.popPose();
                    if (!own) TempestRenderTypes.restoreRenderState();
                } finally {
                    compositor.endWorldPass();
                }
            } finally {
                worldGuard.restore();
                sphereDraws.clear();
            }
        }
    }

    /**
     * Runs once the scene image is finished - after Minecraft and after any shader pack - which is the
     * one point in the frame where the mod can apply an effect to it without contending with anybody.
     */
    public void renderPostLevel() {
        if (!config.general.enabled) return;
        try {
            compositor.composite(distortion.field());
        } finally {
            distortion.clear();
        }
        developmentCapture.frame(Minecraft.getInstance(), lastWorldPath);
    }

    public void renderHud(GuiGraphics graphics, float partialTick) {
        if (!config.general.enabled) return;
        if (!hideLightningFlash()) screenFlash.render(graphics, config, partialTick);
        if (config.general.debug) renderDebugOverlay(graphics);
    }

    private void renderDebugOverlay(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        int segments = 0;
        for (var effect : effects.lightning()) segments += effect.geometry().segmentCount();
        graphics.drawString(minecraft.font, "Thunderhead | bolts " + effects.activeLightningCount()
            + " | segments " + segments + " | particles " + particles.activeCount()
            + "/" + particles.capacity(), 8, 8, 0xffb9d7ff, true);
        graphics.drawString(minecraft.font, "pipeline " + compatibilityMode()
            + " | programs " + (programs.available() ? "own"
                : TempestShaders.usingCustomShaders() ? "bundled" : "vanilla")
            + " | compositor " + lastWorldPath + " | channel " + (channelsInPack ? "pack" : "own"), 8, 20, 0xff8eaccd, true);
        graphics.drawString(minecraft.font, "thunder queue " + thunder.pendingCount()
            + " | rolls " + thunderRolls.activeCount() + "/" + thunderRolls.pendingPulses()
            + " | sky " + distantBolts.activeCount()
            + " | lights " + lights.activeCount()
            + " | discharges " + discharges.activeCount()
            + " | imprints " + imprints.activeCount(), 8, 32, 0xff8eaccd, true);
    }

    private WorldFxRenderer.Scene scene() {
        return new WorldFxRenderer.Scene(effects.lightning(), effects.shockwaves(), particles.active(),
            lights.lights(), discharges.discharges(), imprints.imprints(), distantBolts.bolts(), sphereDraws);
    }

    private boolean sceneIsEmpty() {
        return effects.activeCount() == 0 && particles.activeCount() == 0 && lights.activeCount() == 0
            && discharges.activeCount() == 0 && imprints.activeCount() == 0 && distantBolts.activeCount() == 0
            && ballLightning.isEmpty();
    }

    // ------------------------------------------------------------------ mixin hooks

    /** A pack may discard generic fallback programs. Retain its real vanilla bolt renderer then. */
    public boolean drawsOwnLightning() {
        return config.general.enabled && (!shaders.shaderPackActive(compatibilityMode()) || programs.available());
    }

    /** Extra client-side sky-flash ticks, honouring the vanilla accessibility option. */
    public int skyFlashTicks() {
        if (!config.general.enabled || config.general.reducedFlashing || hideLightningFlash()) return 0;
        return SurfaceLightingPolicy.skyFlashTicks(worldFlash.flashTicks(), config,
            !lastIsolationFailed && "isolated".equals(compositor.status()));
    }

    public boolean suppressVanillaSound(ResourceLocation sound) {
        return config.general.enabled && config.audio.suppressVanillaThunder
            && VanillaSoundFilter.shouldSuppress(sound.toString(), config.audio.customThunder);
    }

    public CameraImpulseSystem cameraImpulse() { return cameraImpulse; }

    public ThunderRumbleCameraEffect thunderRumble() { return rumble; }

    // ------------------------------------------------------------------ debug commands

    public void debugStrike(double distance, String environmentName) {
        debugStrike(distance, environmentName, null);
    }

    public void debugStrike(double distance, String environmentName, Long fixedSeed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (currentLevel == null || minecraft.player == null) return;
        // Aim along the player's facing, then drop the strike onto the actual ground: a debug bolt
        // that ends in mid-air was exactly the "it hits slightly too high" report.
        float yaw = minecraft.player.getYRot() * ((float) Math.PI / 180f);
        Vec3d eye = platform.cameraPosition();
        Vec3d aimed = eye.add(-Math.sin(yaw) * distance, 0, Math.cos(yaw) * distance);
        Vec3d point = snapToSurface(currentLevel, aimed);

        triggerDebugStrike(point, environmentName, fixedSeed);
    }

    /** Fires a visual-only strike at an exact world-space position. */
    public void debugStrikeAt(double x, double y, double z, Long fixedSeed) {
        if (currentLevel == null) return;
        triggerDebugStrike(new Vec3d(x, y, z), "auto", fixedSeed);
    }

    /** Visual-only typed discharge, with cloud events entirely above the surface. */
    public void debugTypedStrike(LightningKind kind, long seed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (currentLevel == null || minecraft.player == null) return;
        Vec3d camera = platform.cameraPosition();
        double yaw = Math.toRadians(minecraft.player.getYRot());
        Vec3d center = camera.add(-Math.sin(yaw) * 100, 0, Math.cos(yaw) * 100);
        Vec3d end;
        Vec3d origin = null;
        if (kind.contactsGround()) end = snapToSurface(currentLevel, center);
        else {
            double height = Math.max(config.lightning.cloudBaseY, camera.y() + 40);
            double span = kind == LightningKind.INTERCLOUD ? 180 : 85;
            origin = new Vec3d(center.x() - span * .5, height, center.z());
            end = new Vec3d(center.x() + span * .5, height + (kind == LightningKind.INTERCLOUD ? 12 : -22), center.z() + 25);
        }
        TempestFxApi.triggerLightning(LightningEffect.builder().position(end).origin(origin)
            .kind(kind).seed(seed).environment(environmentAt(currentLevel, end)).build());
    }

    private void triggerDebugStrike(Vec3d point, String environmentName, Long fixedSeed) {
        if (currentLevel == null) return;

        LightningEnvironment sampled = environmentAt(currentLevel, point);
        LightningEnvironment.Type type = "auto".equalsIgnoreCase(environmentName)
            ? sampled.type()
            : parseEnvironment(environmentName);
        LightningEnvironment environment = new LightningEnvironment(type, sampled.groundColor(),
            sampled.raining(), type == LightningEnvironment.Type.WATER ? 1f : sampled.moisture(),
            sampled.surfaceY(point.y()), type == LightningEnvironment.Type.FOREST || sampled.foliage(),
            sampled.brightness());
        TempestFxApi.triggerLightning(LightningEffect.builder()
            .position(point)
            .seed(fixedSeed != null ? fixedSeed
                : StrikeSeed.of(point.x(), point.y(), point.z(), currentLevel.getGameTime()))
            .intensity(1f)
            .environment(environment)
            .target(StrikeTarget.none())
            .build());
    }

    /**
     * Starts a rolling thunder event on its own, with no lightning in front of it.
     */
    public void triggerThunderRoll(ThunderRoll roll) {
        if (currentLevel == null) return;
        thunderRolls.trigger(platform.cameraPosition(), roll.position(), roll.seed(), 1f,
            roll.durationTicks(), roll.flashesPerSecond(), viewDistance());
    }

    /** Enables the reversible spectator-based capture preset. */
    public void enableShowcaseCamera() { showcaseCamera.enable(Minecraft.getInstance()); }

    /** Restores the options and game mode saved when cinematic capture was enabled. */
    public void disableShowcaseCamera() { showcaseCamera.disable(Minecraft.getInstance()); }

    public void setShowcaseCameraSpeed(double speed) {
        showcaseCamera.setFlyingSpeed(Minecraft.getInstance(), (float) speed);
    }

    /** Moves a point down onto the surface of its column, the way a real bolt terminates. */
    private static Vec3d snapToSurface(ClientLevel level, Vec3d point) {
        BlockPos column = BlockPos.containing(point.x(), point.y(), point.z());
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        return new Vec3d(point.x(), surface.getY(), point.z());
    }

    private LightningEnvironment environmentAt(ClientLevel level, Vec3d point) {
        return ingest.environments().resolve(level, new Vec3(point.x(), point.y(), point.z()));
    }

    /** Simulates a direct hit on the local player, including the ash imprint. */
    public void debugDirectHit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (currentLevel == null) return;
        Vec3d point = new Vec3d(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
        LightningEnvironment environment = environmentAt(currentLevel, point);
        TempestFxApi.triggerLightning(LightningEffect.builder()
            .position(point)
            .seed(StrikeSeed.of(point.x(), point.y(), point.z(), currentLevel.getGameTime()))
            .intensity(1f)
            .environment(environment)
            .target(new StrikeTarget(minecraft.player.getId(), true, point,
                minecraft.player.getBbWidth(), minecraft.player.getBbHeight()))
            .build());
    }

    /**
     * Asks the server for a real bolt.
     *
     * <p>{@code /tempestfx strike} only draws; it never creates an entity, so it cannot damage
     * anything. This routes through the vanilla {@code /summon} command instead, which produces a
     * genuine bolt with vanilla damage, vanilla fire and the Thunderhead visuals on top - the way to
     * check gameplay rather than looks. Requires the usual command permission.
     */
    public void summonRealBolt() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.player.connection.sendCommand("summon minecraft:lightning_bolt ~ ~ ~");
    }

    /**
     * Fires a rolling thunder event on the spot, with no lightning involved at all.
     *
     * @param seconds length of the event, or {@code 0} to let it choose one
     * @param flashes distant channels <em>per second</em>, or {@code 0} to roll one
     */
    public void debugThunderRoll(double seconds, int flashes) {
        Vec3d camera = platform.cameraPosition();
        long time = currentLevel != null ? currentLevel.getGameTime() : 0;
        double bearing = StrikeSeed.unit(time, 0x1) * Math.PI * 2;
        Vec3d origin = camera.add(Math.cos(bearing) * 40, 0, Math.sin(bearing) * 40);
        var effect = thunderRolls.trigger(camera, origin,
            StrikeSeed.of(origin.x(), origin.y(), origin.z(), time), 1f,
            (int) Math.round(seconds * 20), flashes, viewDistance());
        TempestFx.log().info("Rolling thunder: {} channels/s ({} total{}) and {} sounds over {} ticks",
            effect.flashRate(), effect.totalBolts(),
            effect.boltsWereTruncated() ? ", capped from " + Math.round(effect.flashRate()
                * effect.durationTicks() / 20.0) : "",
            effect.totalPulses(), effect.durationTicks());
    }

    /** Asks the server for a ball lightning entity in front of the player. */
    public void summonBallLightning() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.player.connection.sendCommand("summon " + TempestEntities.BALL_LIGHTNING_ID + " ^ ^1 ^3");
    }

    public void stress(int count) {
        for (int index = 0; index < Math.min(100, count); index++) {
            debugStrike(8 + (index % 5) * 7, index % 3 == 0 ? "water" : "land");
        }
    }

    public TempestConfig config() { return config; }

    // ------------------------------------------------------------------ internals

    /**
     * How far the player can actually see, in blocks.
     */
    private double viewDistance() {
        Minecraft minecraft = Minecraft.getInstance();
        double chunks = minecraft.options != null ? minecraft.options.getEffectiveRenderDistance() : 12;
        return Math.min(chunks * 16.0, config.performance.renderDistance);
    }

    /**
     * Which rendering profile applies right now.
     *
     * <p>An isolated pass is always drawn in full: the mod owns the attachment, so its own programs
     * run, nothing tonemaps the result and none of the compromises a shader pack forces apply. The
     * degraded profile is for the fallback path only, where the mod is drawing straight into a frame
     * somebody else composites - and a pack can be toggled without a restart, so the question is asked
     * per frame rather than at startup.
     */
    private ShaderPackProfile shaderPackProfile(boolean isolated) {
        return isolated ? ShaderPackProfile.FULL
            : ShaderPackProfile.of(shaders.shaderPackActive(compatibilityMode()));
    }

    /** The profile for geometry drawn outside the mod's own world pass; see the entity renderer. */
    public ShaderPackProfile shaderPackProfile() {
        return shaderPackProfile(false);
    }

    private RenderCompatibilityMode compatibilityMode() {
        return config.compatibility.shaderCompatibilityMode == RenderCompatibilityMode.AUTO
            ? detectedCompatibility
            : config.compatibility.shaderCompatibilityMode;
    }

    private static boolean hideLightningFlash() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options != null && minecraft.options.hideLightningFlash().get();
    }

    /**
     * Development smoke test, enabled by {@code -Dtempestfx.smokeStrike=true}.
     */
    private void maybeRunSmokeStrike(Minecraft minecraft) {
        if (automatedSmokeStrikeTriggered || minecraft.player == null) return;
        if (!Boolean.getBoolean("tempestfx.smokeStrike")) return;
        // Give the connection a moment: commands sent on the join tick are not reliably accepted.
        if (++smokeStrikeCountdown < 40) return;
        automatedSmokeStrikeTriggered = true;
        debugStrike(10, "land");
        debugDirectHit();
        summonRealBolt();
        summonBallLightning();
        debugThunderRoll(8, 50);
        TempestFx.log().info("Automated smoke test triggered: visual strike, direct hit, real bolt, sphere, roll");
    }

    private static LightningEnvironment.Type parseEnvironment(String name) {
        try {
            return LightningEnvironment.Type.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LightningEnvironment.Type.LAND;
        }
    }
}
