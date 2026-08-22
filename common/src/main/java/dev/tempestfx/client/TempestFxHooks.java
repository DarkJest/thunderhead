package dev.tempestfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.tempestfx.effect.CameraImpulseSystem;
import dev.tempestfx.effect.ThunderRumbleCameraEffect;
import dev.tempestfx.entity.BallLightning;
import dev.tempestfx.render.BallLightningDraw;
import dev.tempestfx.render.ShaderPackProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;

/**
 * Null-safe entry points used by the mixins.
 */
public final class TempestFxHooks {
    private static volatile TempestFxClient client;

    private TempestFxHooks() {}

    public static void install(TempestFxClient instance) { client = instance; }

    public static void uninstall() { client = null; }

    /** {@code ClientLevel#addEntity}: the exact moment a replicated bolt or sphere appears. */
    public static void onEntityAdded(ClientLevel level, Entity entity) {
        TempestFxClient current = client;
        if (current == null) return;
        if (entity instanceof LightningBolt bolt) current.onLightningSpawn(level, bolt);
        else if (entity instanceof BallLightning ball) current.onBallLightningSpawn(ball);
    }

    /** {@code ClientLevel#getSkyFlashTime}: transient client-side sky flash extension. */
    public static int skyFlashTicks() {
        TempestFxClient current = client;
        return current == null ? 0 : current.skyFlashTicks();
    }

    /**
     * Whether Thunderhead is drawing lightning itself.
     *
     * <p>Read by the renderer registered for {@code minecraft:lightning_bolt}: when this is false the
     * vanilla visual is drawn, so switching the mod off does not hide lightning entirely.
     */
    public static boolean drawsOwnLightning() {
        TempestFxClient current = client;
        return current != null && current.config().general.enabled;
    }

    /**
     * The rendering profile for geometry drawn outside the mod's own world pass.
     *
     * <p>Entity renderers are constructed by the game and reached through the vanilla dispatcher, so
     * they cannot be handed the profile the way {@code WorldFxRenderer} is.
     */
    public static ShaderPackProfile shaderPackProfile() {
        TempestFxClient current = client;
        return current == null ? ShaderPackProfile.FULL : current.shaderPackProfile();
    }

    /**
     * Offers a ball lightning sphere to the mod's world pass.
     *
     * @return {@code false} when the entity renderer has to draw it itself
     */
    public static boolean deferBallLightning(BallLightningDraw sphere) {
        TempestFxClient current = client;
        return current != null && current.deferBallLightning(sphere);
    }

    /** {@code SoundEngine#play}: drops the two vanilla lightning clips Thunderhead replaces. */
    public static boolean suppressVanillaSound(ResourceLocation sound) {
        TempestFxClient current = client;
        return current != null && current.suppressVanillaSound(sound);
    }

    /**
     * {@code GameRenderer#bobHurt}: composes the two independent camera responses.
     */
    public static void applyCameraImpulse(PoseStack stack, float partialTick) {
        TempestFxClient current = client;
        if (current == null) return;
        CameraImpulseSystem impulse = current.cameraImpulse();
        ThunderRumbleCameraEffect rumble = current.thunderRumble();
        boolean impulseActive = impulse.active();
        if (!impulseActive && !rumble.active()) return;

        if (impulseActive) stack.translate(0, impulse.verticalOffset(partialTick), 0);
        float pitch = (impulseActive ? impulse.pitchOffset(partialTick) : 0) + rumble.pitchOffset(partialTick);
        float yaw = (impulseActive ? impulse.yawOffset(partialTick) : 0) + rumble.yawOffset(partialTick);
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
    }

    /** {@code GameRenderer#render}, right after the level: applies the effect to the finished frame. */
    public static void processPostLevel() {
        TempestFxClient current = client;
        if (current != null) current.renderPostLevel();
    }
}
