package dev.tempestfx.effect;

import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.StrikeSeed;

/**
 * Camera response to thunder, driven by the sound rather than by the strike.
 */
public final class ThunderRumbleCameraEffect {
    /** More than this and the offsets just average out into mush. */
    private static final int MAX_SHAKES = 6;
    /** Peak deflection of a full-strength boom, in degrees. */
    private static final float DEGREES_PER_UNIT = 1.5f;
    /** Slow: a pressure wave moving the ground, not a vibration. */
    private static final double MIN_RATE = 0.09;
    private static final double MAX_RATE = 0.2;

    private final Shake[] shakes = new Shake[MAX_SHAKES];
    private int cursor;

    /**
     * Adds one impulse.
     *
     * @param strength {@code 0..1}, normally a pulse's planned impact scaled by its audible gain
     * @param seed     decides frequency, phase and bearing, so no two booms shake the same way
     */
    public void onTransient(float strength, long seed) {
        float amount = FxMath.clamp(strength, 0f, 1f);
        if (amount <= 0.02f) return;
        // Heavier transients ring longer and lower, the way a big body of air does - but a boom
        // still has to settle within about a second, or the camera reads as broken rather than shoved.
        int decay = (int) (6 + amount * 14);
        double rate = MIN_RATE + (MAX_RATE - MIN_RATE) * (1 - amount) + StrikeSeed.unit(seed, 3) * 0.03;
        double bearing = StrikeSeed.unit(seed, 4) * Math.PI * 2;
        shakes[cursor] = new Shake(amount, decay, rate, StrikeSeed.unit(seed, 5) * Math.PI * 2,
            Math.sin(bearing) * 0.7 + 0.3, Math.cos(bearing));
        cursor = (cursor + 1) % MAX_SHAKES;
    }

    public void tick() {
        for (int index = 0; index < shakes.length; index++) {
            Shake shake = shakes[index];
            if (shake == null) continue;
            shakes[index] = shake.age >= shake.decayTicks * 4 ? null : shake.next();
        }
    }

    public boolean active() {
        for (Shake shake : shakes) if (shake != null) return true;
        return false;
    }

    public float pitchOffset(float partialTick) { return offset(partialTick, true); }

    public float yawOffset(float partialTick) { return offset(partialTick, false); }

    public void clear() {
        java.util.Arrays.fill(shakes, null);
        cursor = 0;
    }

    private float offset(float partialTick, boolean pitch) {
        float total = 0;
        float t = FxMath.clamp(partialTick, 0, 1);
        for (Shake shake : shakes) {
            if (shake == null) continue;
            total += (float) shake.value(shake.age + t) * (float) (pitch ? shake.pitchAxis : shake.yawAxis);
        }
        return total * DEGREES_PER_UNIT;
    }

    /** One damped oscillator: sharp attack, exponential decay, its own frequency and axis. */
    private record Shake(float amplitude, int decayTicks, double rate, double phase,
                         double pitchAxis, double yawAxis, int age) {
        Shake(float amplitude, int decayTicks, double rate, double phase, double pitchAxis, double yawAxis) {
            this(amplitude, decayTicks, rate, phase, pitchAxis, yawAxis, 0);
        }

        Shake next() {
            return new Shake(amplitude, decayTicks, rate, phase, pitchAxis, yawAxis, age + 1);
        }

        double value(double time) {
            // Attack over two ticks, then decay: the ground is shoved, it does not fade in.
            double attack = FxMath.clamp(time / 2.0, 0, 1);
            double envelope = attack * Math.exp(-time / decayTicks);
            return Math.sin(time * rate * Math.PI * 2 + phase) * envelope * amplitude;
        }
    }
}
