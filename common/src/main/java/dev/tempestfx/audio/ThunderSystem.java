package dev.tempestfx.audio;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.api.ThunderOptions;
import dev.tempestfx.api.ThunderVoice;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.platform.ClientPlatform;
import java.util.ArrayList;
import java.util.List;

/**
 * Schedules thunder at the speed of sound.
 *
 * <p>Light is instantaneous, sound is not: every layer is queued for {@code distance / 343} seconds
 * plus its own musical offset. The volume handed to the platform is the value that makes the engine
 * reproduce the loudness this system asked for at the listener's distance
 * (see {@link ThunderMath#spatialVolume(double, float)}).
 */
public final class ThunderSystem {
    /** Hard cap so a pathological strike burst cannot grow the queue without bound. */
    private static final int MAX_PENDING = 192;
    /** Length of the playback rate window, in ticks. */
    public static final int VOICE_WINDOW_TICKS = 20;
    /** Clips Thunderhead may start inside one window before it starts dropping layers. */
    public static final int MAX_VOICES_PER_WINDOW = 18;

    private final List<ScheduledThunder> scheduled = new ArrayList<>();
    private final ThunderSoundStrategy strategy;
    private final ClientPlatform platform;
    private final VoiceBudget budget;
    private PlaybackListener listener = (profile, position, gain) -> {};

    public ThunderSystem(ClientPlatform platform) {
        this(platform, new DistanceThunderSoundStrategy(),
            new VoiceBudget(VOICE_WINDOW_TICKS, MAX_VOICES_PER_WINDOW));
    }

    public ThunderSystem(ClientPlatform platform, ThunderSoundStrategy strategy) {
        this(platform, strategy, new VoiceBudget(VOICE_WINDOW_TICKS, MAX_VOICES_PER_WINDOW));
    }

    public ThunderSystem(ClientPlatform platform, ThunderSoundStrategy strategy, VoiceBudget budget) {
        this.platform = platform;
        this.strategy = strategy;
        this.budget = budget;
    }

    /** The budget is shared with the rolling thunder system so neither can starve the other. */
    public VoiceBudget budget() { return budget; }

    /** Notified whenever a clip actually starts, so the client can react to what is audible. */
    public void setPlaybackListener(PlaybackListener value) { this.listener = value; }

    public void onStrike(LightningStrikeFxEvent event, Vec3d listener, TempestConfig config) {
        onStrike(event, listener, config, false);
    }

    /**
     * @param headOnly play only the sharp opening layers and drop the long tail, because a rolling
     *                 thunder event is already covering the body and the decay. Without this the two
     *                 paths stack and a single strike rumbles for the better part of twenty seconds.
     */
    public void onStrike(LightningStrikeFxEvent event, Vec3d listener, TempestConfig config, boolean headOnly) {
        if (!config.audio.customThunder || config.audio.thunderVolume <= 0) return;
        ThunderOptions options = event.options().thunder();
        if (options != null && options.voice() == ThunderVoice.SILENT) return;

        double distance = listener.distanceTo(event.position());
        if (distance > config.audio.maxThunderDistance) return;

        float requested = options == null ? 1f : options.volume();
        float eventGain = ThunderMath.thunderGain(distance, event.intensity(), config.audio.thunderVolume) * requested;
        if (eventGain <= 0) return;
        // The player's own delay preference still wins when they turned realism off: an integration
        // is asking for a timing, not for the right to override an audio setting.
        int propagation = !config.audio.realisticSoundDelay ? 0
            : options == null || options.delayFromDistance() ? ThunderMath.delayTicks(distance)
            : options.delayTicks();

        for (ThunderLayer layer : chooseLayers(event, distance, options)) {
            if (headOnly && layer.extraDelayTicks() > 0) continue;
            float volume = ThunderMath.spatialVolume(distance, eventGain * layer.gain());
            if (volume <= 0) continue;
            int delay = propagation + layer.extraDelayTicks();
            if (delay <= 0) {
                play(layer.profile(), event.position(), volume, layer.pitch());
            } else if (scheduled.size() < MAX_PENDING) {
                scheduled.add(new ScheduledThunder(delay, layer.profile(), event.position(), volume, layer.pitch()));
            }
        }
    }

    /**
     * The layers a strike plays: normally chosen by distance, or one named clip when an integration
     * asked for a specific voice.
     */
    private List<ThunderLayer> chooseLayers(LightningStrikeFxEvent event, double distance, ThunderOptions options) {
        ThunderProfile named = options == null ? null : options.voice().profile();
        if (named != null) return List.of(new ThunderLayer(named, 0, 1f, 1f));
        return strategy.select(distance, event.seed(), event.intensity());
    }

    /**
     * Plays a one-shot effect clip that is not part of a thunder cue, such as the discharge crackle.
     * Shares the same voice budget so incidental sounds cannot starve the thunder itself.
     */
    public void playIncidental(ThunderProfile profile, Vec3d position, float volume, float pitch) {
        play(profile, position, volume, pitch);
    }

    public void tick() {
        budget.tick();
        for (int index = scheduled.size() - 1; index >= 0; index--) {
            ScheduledThunder pending = scheduled.get(index).next();
            if (pending.ticks() <= 0) {
                scheduled.remove(index);
                play(pending.profile(), pending.position(), pending.volume(), pending.pitch());
            } else {
                scheduled.set(index, pending);
            }
        }
    }

    /** Drops queued thunder that belongs to a level the player already left. */
    public void clear() {
        scheduled.clear();
        budget.clear();
    }

    public int pendingCount() { return scheduled.size(); }

    /** Clips started inside the current window; exposed for the debug overlay and tests. */
    public int voicesInWindow() { return budget.started(); }

    private void play(ThunderProfile profile, Vec3d position, float volume, float pitch) {
        if (!budget.claim()) return;
        platform.playThunder(profile, position, volume, pitch);
        listener.onPlayed(profile, position, volume);
    }

    /** Callback for clips that just started, so the client can react to what is actually audible. */
    @FunctionalInterface
    public interface PlaybackListener {
        void onPlayed(ThunderProfile profile, Vec3d position, float volume);
    }

    private record ScheduledThunder(int ticks, ThunderProfile profile, Vec3d position, float volume, float pitch) {
        ScheduledThunder next() { return new ScheduledThunder(ticks - 1, profile, position, volume, pitch); }
    }
}
