package dev.tempestfx.audio;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.api.ThunderOptions;
import dev.tempestfx.api.ThunderVoice;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.platform.ClientPlatform;
import java.util.ArrayList;
import java.util.List;
import dev.tempestfx.lightning.FlashTimeline;
import dev.tempestfx.lightning.LightningGeometry;

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
    private final List<Wave> waves = new ArrayList<>();
    private long clock;
    private TempestConfig waveConfig = new TempestConfig();
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

    /** One acoustic event per flash; every return pulse reuses the same channel sources. */
    public void onChannelFlash(LightningStrikeFxEvent event, LightningGeometry geometry, FlashTimeline timeline, TempestConfig config) {
        onChannelFlash(event, geometry, timeline, config, 0);
    }
    public void onChannelFlash(LightningStrikeFxEvent event, LightningGeometry geometry, FlashTimeline timeline, TempestConfig config, int elapsed) {
        waveConfig = config;
        if (!config.audio.customThunder || config.audio.thunderVolume <= 0) return;
        var options = event.options().thunder();
        if (options != null && options.voice() == ThunderVoice.SILENT) return;
        var sources = ChannelAcoustics.sources(geometry);
        for (var pulse : timeline.pulses()) for (var source : sources) {
            if (waves.size() + scheduled.size() >= MAX_PENDING) return;
            double delay = !config.audio.realisticSoundDelay ? 0 : options != null && !options.delayFromDistance()
                ? options.delayTicks() : platform.cameraPosition().distanceTo(source.position()) / 343.0 * 20;
            if (elapsed > 0 && elapsed - pulse.atTicks() > delay + 1) continue;
            float gain = event.intensity() * pulse.strength() * source.weight();
            if (options != null) gain *= options.volume();
            waves.add(new Wave(source.position(), clock + pulse.atTicks() - elapsed, gain, options));
        }
    }

    /**
     * @param headOnly play only the sharp opening layers and drop the long tail, because a rolling
     *                 thunder event is already covering the body and the decay. Without this the two
     *                 paths stack and a single strike rumbles for the better part of twenty seconds.
     */
    public void onStrike(LightningStrikeFxEvent event, Vec3d listener, TempestConfig config, boolean headOnly) {
        scheduleLegacy(event, listener, config, headOnly, 0);
    }

    /** Past optical contacts still have future acoustic arrivals. Do not replay their visuals. */
    public void onPastContacts(LightningStrikeFxEvent event, FlashTimeline timeline, int elapsed, Vec3d camera, TempestConfig config) {
        for (var pulse : timeline.pulses()) {
            if (pulse.atTicks() > elapsed) continue;
            var stroke = event.asStroke(event.position(), event.seed(), event.intensity()*pulse.strength(), event.environment(), pulse.index());
            scheduleLegacy(stroke, camera, config, false, elapsed-pulse.atTicks());
        }
    }

    private void scheduleLegacy(LightningStrikeFxEvent event, Vec3d listener, TempestConfig config, boolean headOnly, double elapsed) {
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
            double remaining = propagation + layer.extraDelayTicks() - elapsed;
            if (elapsed > 0 && remaining < -1) continue;
            int delay = (int) Math.ceil(remaining);
            if (delay <= 0) {
                play(layer.profile(), event.position(), volume, layer.pitch());
            } else if (scheduled.size() + waves.size() < MAX_PENDING) {
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
        clock++;
        budget.tick();
        tickWaves();
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

    public void tick(TempestConfig config) {
        waveConfig = config;
        if (!config.general.enabled || !config.audio.customThunder || config.audio.thunderVolume <= 0) { clear(); return; }
        tick();
    }

    private void tickWaves() {
        if (!waveConfig.general.enabled || !waveConfig.audio.customThunder) { waves.clear(); return; }
        Vec3d camera = platform.cameraPosition();
        for (int i = 0; i < waves.size();) {
            Wave wave = waves.get(i);
            double age = clock - wave.emittedAt();
            double distance = camera.distanceTo(wave.position());
            double delay = !waveConfig.audio.realisticSoundDelay ? 0
                : wave.options() != null && !wave.options().delayFromDistance() ? wave.options().delayTicks()
                : distance / 343.0 * 20;
            if (age > 600) { waves.remove(i); continue; }
            if (age < delay) { i++; continue; }
            waves.remove(i);
            if (distance > waveConfig.audio.maxThunderDistance) continue;
            float gain = ThunderMath.thunderGain(distance, wave.gain(), waveConfig.audio.thunderVolume);
            if (gain <= 0 || !budget.claim()) continue;
            float transmission = waveConfig.audio.shelterAttenuation ? platform.thunderTransmission(wave.position()) : 1;
            transmission = Float.isFinite(transmission) ? Math.max(0, Math.min(1, transmission)) : 1;
            ThunderProfile named = wave.options() == null ? null : wave.options().voice().profile();
            ThunderProfile profile = named != null ? named : transmission < .8 || distance > 160
                ? ThunderProfile.DISTANT_THUNDER : distance < 40 ? ThunderProfile.CLOSE_HEAVY : ThunderProfile.MEDIUM_RUMBLE;
            float volume = ThunderMath.spatialVolume(distance, gain * transmission);
            platform.playThunder(profile, wave.position(), volume, transmission < .8 ? .9f : 1f);
            listener.onPlayed(profile, wave.position(), volume);
        }
    }

    /** Drops queued thunder that belongs to a level the player already left. */
    public void clear() {
        scheduled.clear();
        waves.clear();
        budget.clear();
    }

    public int pendingCount() { return scheduled.size() + waves.size(); }

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
    private record Wave(Vec3d position, double emittedAt, float gain, ThunderOptions options) {}
}
