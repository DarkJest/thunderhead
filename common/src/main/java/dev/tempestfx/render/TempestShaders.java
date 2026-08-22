package dev.tempestfx.render;

import java.util.function.BooleanSupplier;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Holder for the bundled core shaders, with a working vanilla fallback.
 *
 * <p>Both programs live in {@code assets/minecraft/shaders/core} under a {@code tempest_} prefix,
 * because vanilla resolves core shader names in the {@code minecraft} namespace only; that keeps the
 * loading path identical on Fabric and NeoForge. Loader bootstraps register them and call the
 * setters here.
 */
public final class TempestShaders {
    public static final String BOLT_NAME = "tempest_bolt";
    public static final String PARTICLE_NAME = "tempest_particle";
    public static final String SHOCKWAVE_NAME = "tempest_shockwave";
    public static final String SMOKE_NAME = "tempest_smoke";
    /** Every program registered with the game, in the order the loaders register them. */
    public static final String[] NAMES = { BOLT_NAME, PARTICLE_NAME, SHOCKWAVE_NAME, SMOKE_NAME };

    private static ShaderInstance bolt;
    private static ShaderInstance particle;
    private static ShaderInstance shockwave;
    private static ShaderInstance smoke;
    private static boolean enabled = true;
    /**
     * Answers "is a third-party shader pack compositing the world right now".
     *
     * <p>Consulted per draw rather than once at startup, because a player can toggle shaders without
     * restarting and the mod has to follow them.
     */
    private static BooleanSupplier foreignPipeline = () -> false;

    private TempestShaders() {}

    public static void setBolt(ShaderInstance instance) { bolt = instance; }

    public static void setParticle(ShaderInstance instance) { particle = instance; }

    public static void setShockwave(ShaderInstance instance) { shockwave = instance; }

    public static void setSmoke(ShaderInstance instance) { smoke = instance; }

    /** Installs a program by name; keeps the loader bootstraps down to one loop each. */
    public static void set(String name, ShaderInstance instance) {
        switch (name) {
            case BOLT_NAME -> setBolt(instance);
            case PARTICLE_NAME -> setParticle(instance);
            case SHOCKWAVE_NAME -> setShockwave(instance);
            case SMOKE_NAME -> setSmoke(instance);
            default -> { }
        }
    }

    /** Turned off by config. */
    public static void setEnabled(boolean value) { enabled = value; }

    /** Installed once by the client; see {@link #foreignPipeline}. */
    public static void setForeignPipelineProbe(BooleanSupplier probe) {
        foreignPipeline = probe == null ? () -> false : probe;
    }

    /**
     * Whether the bundled programs may be used for this draw.
     *
     * <p>Under a shader pack they may not, and this is the reason the mod compiles its own programs
     * rather than registering them with the game. A pack that is compositing the world has to decide
     * what to do about a shader object it does not recognise, and Iris, for one, disables colour and
     * depth writes around it: the geometry is silently discarded. Drawing through a vanilla program
     * instead gives the pack something it can map. The masks carry their shape in alpha, so the
     * silhouette survives the swap; the analytic cross-section does not.
     *
     * <p>This whole path is the fallback, taken only when the mod's own programs could not be
     * compiled. See {@code FxProgram}.
     */
    private static boolean customAvailable() {
        return enabled && !foreignPipeline.getAsBoolean();
    }

    public static boolean usingCustomShaders() {
        return customAvailable() && bolt != null && particle != null && shockwave != null && smoke != null;
    }

    public static ShaderInstance boltShader() {
        return customAvailable() && bolt != null ? bolt : GameRenderer.getPositionTexColorShader();
    }

    public static ShaderInstance particleShader() {
        return customAvailable() && particle != null ? particle : GameRenderer.getPositionTexColorShader();
    }

    public static ShaderInstance shockwaveShader() {
        return customAvailable() && shockwave != null ? shockwave : particleShader();
    }

    public static ShaderInstance smokeShader() {
        return customAvailable() && smoke != null ? smoke : particleShader();
    }

    /** Drops references on shutdown; the shader instances themselves are owned by the loader. */
    public static void clear() {
        bolt = null;
        particle = null;
        shockwave = null;
        smoke = null;
    }
}
