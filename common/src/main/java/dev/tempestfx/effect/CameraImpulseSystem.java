package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;

/**
 * Pressure-wave camera impulse.
 *
 * <p>Only the front arriving: one push that settles. The long response to the thunder that follows
 * belongs to {@link ThunderRumbleCameraEffect}, which is driven by the audio rather than the strike.
 */
public final class CameraImpulseSystem {
    private static final double STIFFNESS = 0.38;
    private static final double DAMPING = 0.58;
    private static final float DEGREES_PER_UNIT = 3.5f;
    private static final float BLOCKS_PER_UNIT = 0.06f;
    private static final double REST_EPSILON = 1.0e-4;

    private double displacement;
    private double previousDisplacement;
    private double velocity;
    private double pitchAxis = 1;
    private double yawAxis;

    public void onStrike(LightningStrikeFxEvent event, Vec3d camera, TempestConfig config) {
        if (!config.camera.cameraImpulse || config.camera.impulseStrength <= 0) return;
        double amount = FxMath.distanceFalloff(camera.distanceTo(event.position()), 3, 64)
            * config.camera.impulseStrength * event.intensity();
        if (amount <= 0) return;
        double bearing = StrikeSeed.unit(event.seed(), 0x5ca1) * Math.PI * 2;
        pitchAxis = Math.sin(bearing) * 0.6 + 0.4;
        yawAxis = Math.cos(bearing) * 0.75;
        velocity += amount;
    }

    public void tick() {
        previousDisplacement = displacement;
        velocity += -STIFFNESS * displacement;
        velocity *= DAMPING;
        displacement += velocity;
        if (Math.abs(displacement) < REST_EPSILON && Math.abs(velocity) < REST_EPSILON) {
            displacement = velocity = 0;
        }
    }

    public boolean active() { return displacement != 0 || velocity != 0; }

    public float pitchOffset(float partialTick) {
        return (float) (interpolated(partialTick) * pitchAxis) * DEGREES_PER_UNIT;
    }

    public float yawOffset(float partialTick) {
        return (float) (interpolated(partialTick) * yawAxis) * DEGREES_PER_UNIT;
    }

    /** Small translation component, so the impulse feels like pressure rather than a head turn. */
    public float verticalOffset(float partialTick) { return (float) interpolated(partialTick) * BLOCKS_PER_UNIT; }

    private double interpolated(float partialTick) {
        return FxMath.lerp(previousDisplacement, displacement, FxMath.clamp(partialTick, 0, 1));
    }
}
