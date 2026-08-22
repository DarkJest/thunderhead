package dev.tempestfx.audio;

import dev.tempestfx.api.LightningEnvironment;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.platform.ClientPlatform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThunderSystemTest {
    @Test
    void propagationDelayMatchesTheSpeedOfSound() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderSystem system = new ThunderSystem(platform, singleLayer());
        system.onStrike(event(), new Vec3d(0, 0, 34.3), new TempestConfig().validate());

        assertEquals(1, system.pendingCount());
        system.tick();
        assertEquals(0, platform.plays.size());
        system.tick();
        assertEquals(1, platform.plays.size());
        assertEquals(0, system.pendingCount());
    }

    @Test
    void zeroDistanceHeadLayerPlaysImmediately() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderSystem system = new ThunderSystem(platform, singleLayer());
        system.onStrike(event(), Vec3d.ZERO, new TempestConfig().validate());
        assertEquals(1, platform.plays.size());
        assertEquals(0, system.pendingCount());
    }

    @Test
    void closeStrikeLayersACrackAThumpAndADelayedRoll() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderSystem system = new ThunderSystem(platform);
        system.onStrike(event(), Vec3d.ZERO, new TempestConfig().validate());

        assertEquals(2, platform.plays.size(), "crack and thump arrive with the flash");
        assertTrue(system.pendingCount() > 0, "the rolling tail is scheduled later");
        for (int tick = 0; tick < 40; tick++) system.tick();
        assertEquals(3, platform.plays.size());
        assertEquals(0, system.pendingCount());
    }

    @Test
    void headOnlyDropsTheTailSoARollDoesNotStackOnTopOfIt() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderSystem system = new ThunderSystem(platform);
        system.onStrike(event(), Vec3d.ZERO, new TempestConfig().validate(), true);

        assertEquals(2, platform.plays.size(), "the sharp opening still plays");
        assertEquals(0, system.pendingCount(), "nothing long may be queued behind a rolling event");
    }

    @Test
    void heavyLayersAreMarkedSoTheClientCanShakeTheWorld() {
        assertTrue(ThunderProfile.ROLL_BOOM.shakesTheAir());
        assertTrue(ThunderProfile.ROLL_WALL.shakesTheAir());
        assertTrue(ThunderProfile.IMPACT_THUMP.shakesTheAir());
        assertFalse(ThunderProfile.DISTANT_THUNDER.shakesTheAir());
        assertFalse(ThunderProfile.ELECTRIC_ARC.shakesTheAir());
    }

    @Test
    void playbackListenerSeesEveryClipThatActuallyStarted() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderSystem system = new ThunderSystem(platform);
        List<ThunderProfile> heard = new ArrayList<>();
        system.setPlaybackListener((profile, position, volume) -> heard.add(profile));
        system.onStrike(event(), Vec3d.ZERO, new TempestConfig().validate());
        for (int tick = 0; tick < 60; tick++) system.tick();
        assertEquals(platform.plays.size(), heard.size());
    }

    @Test
    void strikesBeyondTheConfiguredRangeAreIgnored() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderSystem system = new ThunderSystem(platform);
        TempestConfig config = new TempestConfig();
        config.audio.maxThunderDistance = 100;
        config.validate();
        system.onStrike(event(), new Vec3d(0, 0, 400), config);
        assertEquals(0, platform.plays.size());
        assertEquals(0, system.pendingCount());
    }

    @Test
    void queueStaysBoundedUnderAStormAndClearsWithTheLevel() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderSystem system = new ThunderSystem(platform);
        TempestConfig config = new TempestConfig().validate();
        for (int index = 0; index < 500; index++) {
            system.onStrike(new LightningStrikeFxEvent(new Vec3d(index, 64, 0), index, 1,
                LightningEnvironment.land(0x777777, false)), Vec3d.ZERO, config);
        }
        assertTrue(system.pendingCount() <= 192, "queue grew to " + system.pendingCount());
        system.clear();
        assertEquals(0, system.pendingCount());
    }

    @Test
    void aBurstOfStrikesCannotExhaustTheEngineSoundPool() {
        // Minecraft's static channel pool is a few hundred handles wide and is shared with every
        // other sound in the game, so an unlimited scheduler starves the whole mix during a storm.
        RecordingPlatform platform = new RecordingPlatform();
        ThunderSystem system = new ThunderSystem(platform);
        TempestConfig config = new TempestConfig().validate();

        for (int index = 0; index < 100; index++) {
            system.onStrike(new LightningStrikeFxEvent(new Vec3d(index * 0.1, 64, 0), index, 1,
                LightningEnvironment.land(0x777777, false)), Vec3d.ZERO, config);
        }
        assertTrue(platform.plays.size() <= 18, "started " + platform.plays.size() + " clips at once");

        int afterBurst = platform.plays.size();
        for (int tick = 0; tick < 200; tick++) system.tick();
        assertTrue(platform.plays.size() > afterBurst, "the queue must keep playing once the burst clears");
    }

    @Test
    void voiceBudgetRecoversAfterTheWindowPasses() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderSystem system = new ThunderSystem(platform, singleLayer());
        TempestConfig config = new TempestConfig().validate();
        for (int index = 0; index < 40; index++) system.onStrike(event(), Vec3d.ZERO, config);
        int burst = platform.plays.size();
        assertTrue(burst <= 18);

        for (int tick = 0; tick < 25; tick++) system.tick();
        assertEquals(0, system.voicesInWindow(), "the window must drain");
        system.onStrike(event(), Vec3d.ZERO, config);
        assertEquals(burst + 1, platform.plays.size(), "playback must resume after the window");
    }

    @Test
    void everyScheduledLayerIsAudibleAtItsDistance() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderSystem system = new ThunderSystem(platform);
        double distance = 240;
        system.onStrike(event(), new Vec3d(0, 0, distance), new TempestConfig().validate());
        for (int tick = 0; tick < 60; tick++) system.tick();
        assertTrue(platform.plays.size() >= 2);
        for (float volume : platform.plays) {
            assertTrue(ThunderMath.perceivedGain(distance, volume) > 0.05f,
                "layer inaudible at " + distance + " with volume " + volume);
        }
    }

    private static ThunderSoundStrategy singleLayer() {
        return (distance, seed, intensity) -> List.of(new ThunderLayer(ThunderProfile.CLOSE_CRACK, 0, 1f, 1f));
    }

    private static LightningStrikeFxEvent event() {
        return new LightningStrikeFxEvent(Vec3d.ZERO, 42, 1, LightningEnvironment.land(0x777777, false));
    }

    private static final class RecordingPlatform implements ClientPlatform {
        private final List<Float> plays = new ArrayList<>();
        private final List<ThunderProfile> profiles = new ArrayList<>();

        @Override
        public Path configDirectory() { return Path.of("."); }

        @Override
        public Vec3d cameraPosition() { return Vec3d.ZERO; }

        @Override
        public void playThunder(ThunderProfile profile, Vec3d position, float volume, float pitch) {
            plays.add(volume);
            profiles.add(profile);
        }
    }
}
