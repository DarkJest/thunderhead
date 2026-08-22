package dev.tempestfx.audio;

import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.platform.ClientPlatform;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the rolling thunder events.
 *
 * <p>Completely separate from the lightning: it never sees a channel, a segment or an effect list.
 * A strike hands it a position, a seed and an intensity, and from then on each
 * {@link GiantRollingThunderEffect} runs on its own clock. Nothing about the roll changes if the
 * visual bolt is disabled, culled or already gone.
 */
public final class ThunderRollSystem {
    /**
     * One at a time. Overlapping ten-second rolls do not sound bigger, they sound like the storm
     * never stops - which is exactly what a dense thunderstorm turns into if this is not capped.
     */
    private static final int MAX_CONCURRENT = 1;
    /** Silence between events, so a burst of strikes cannot chain rolls end to end. */
    private static final int COOLDOWN_TICKS = 140;

    private final List<GiantRollingThunderEffect> active = new ArrayList<>();
    private final ClientPlatform platform;
    private final VoiceBudget budget;
    private PulseListener listener = pulse -> {};
    private BoltListener boltListener = cue -> {};
    private int cooldown;

    public ThunderRollSystem(ClientPlatform platform, VoiceBudget budget) {
        this.platform = platform;
        this.budget = budget;
    }

    /** Notified for every pulse that actually started. */
    public void setPulseListener(PulseListener value) { this.listener = value; }

    /** Notified for every distant channel the event schedules. */
    public void setBoltListener(BoltListener value) { this.boltListener = value; }

    /**
     * Starts a roll for a strike, if the settings and the distance call for one.
     *
     * @return {@code true} when an event was started
     */
    public boolean onStrike(Vec3d listenerPosition, Vec3d origin, long seed, float intensity,
                            TempestConfig config, double viewDistance) {
        if (!config.audio.customThunder || !config.audio.giantRoll || config.audio.thunderVolume <= 0) {
            return false;
        }
        double distance = listenerPosition.distanceTo(origin);
        if (distance > config.audio.giantRollDistance) return false;
        if (StrikeSeed.unit(seed, 0x8012) >= config.audio.giantRollChance) return false;
        if (cooldown > 0 || !active.isEmpty()) return false;

        active.add(GiantRollingThunderEffect.plan(seed, listenerPosition, origin, intensity, 0, 0, viewDistance));
        cooldown = COOLDOWN_TICKS + GiantRollingThunderEffect.MAX_DURATION_TICKS;
        return true;
    }

    /**
     * Forces a roll regardless of chance, distance or cooldown; used by the debug command.
     *
     * @param durationTicks explicit length, or {@code 0} to roll one
     * @param flashRate     explicit channels per second, or {@code 0} to roll one
     */
    public GiantRollingThunderEffect trigger(Vec3d listenerPosition, Vec3d origin, long seed, float intensity,
                                             int durationTicks, int flashRate, double viewDistance) {
        while (active.size() >= MAX_CONCURRENT) active.removeFirst();
        GiantRollingThunderEffect effect = GiantRollingThunderEffect.plan(
            seed, listenerPosition, origin, intensity, durationTicks, flashRate, viewDistance);
        active.add(effect);
        cooldown = COOLDOWN_TICKS + GiantRollingThunderEffect.MAX_DURATION_TICKS;
        return effect;
    }

    public GiantRollingThunderEffect trigger(Vec3d listenerPosition, Vec3d origin, long seed, float intensity) {
        return trigger(listenerPosition, origin, seed, intensity, 0, 0,
            GiantRollingThunderEffect.DEFAULT_VIEW_DISTANCE);
    }

    public void tick(TempestConfig config) {
        if (cooldown > 0) cooldown--;
        for (int index = active.size() - 1; index >= 0; index--) {
            GiantRollingThunderEffect effect = active.get(index);
            effect.tick(pulse -> release(pulse, config), boltListener::onBolt);
            if (effect.finished()) active.remove(index);
        }
    }

    private void release(ThunderPulse pulse, TempestConfig config) {
        // Gain is planned as perceived loudness; the engine needs the volume argument that
        // reproduces it at this pulse's own distance and bearing.
        float gain = pulse.gain() * config.audio.thunderVolume;
        double distance = platform.cameraPosition().distanceTo(pulse.position());
        float volume = ThunderMath.spatialVolume(distance, Math.min(0.98f, gain));
        if (volume <= 0 || !budget.claim()) return;
        platform.playThunder(pulse.profile(), pulse.position(), volume, pulse.pitch());
        listener.onPulse(pulse);
    }

    public int activeCount() { return active.size(); }

    public int pendingPulses() {
        int total = 0;
        for (GiantRollingThunderEffect effect : active) total += effect.remainingPulses();
        return total;
    }

    public void clear() { active.clear(); cooldown = 0; }

    /** Receives pulses that actually started, so the camera can follow the individual booms. */
    @FunctionalInterface
    public interface PulseListener {
        void onPulse(ThunderPulse pulse);
    }

    /** Receives the distant channels the event schedules. */
    @FunctionalInterface
    public interface BoltListener {
        void onBolt(DistantBoltCue cue);
    }
}
