package dev.tempestfx.audio;

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

class ThunderRollSystemTest {
    private static final double VIEW = 192;
    private static final Vec3d LISTENER = Vec3d.ZERO;
    private static final Vec3d ORIGIN = new Vec3d(30, 0, 10);

    private static TempestConfig config() {
        TempestConfig config = new TempestConfig();
        config.audio.giantRollChance = 1f;
        return config.validate();
    }

    @Test
    void aStrikeStartsAnEventThatThenRunsOnItsOwn() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderRollSystem system = new ThunderRollSystem(platform, budget());
        assertTrue(system.onStrike(LISTENER, ORIGIN, 0x1234, 1f, config(), VIEW));
        assertEquals(1, system.activeCount());

        for (int tick = 0; tick <= GiantRollingThunderEffect.MAX_DURATION_TICKS + 5; tick++) {
            system.tick(config());
        }
        assertEquals(0, system.activeCount(), "the event must end on its own");
        assertTrue(platform.profiles.size() > 8, "only played " + platform.profiles.size() + " pulses");
    }

    @Test
    void pulsesArriveSpreadOverTimeRatherThanAllAtOnce() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderRollSystem system = new ThunderRollSystem(platform, budget());
        system.onStrike(LISTENER, ORIGIN, 0x5eed, 1f, config(), VIEW);

        system.tick(config());
        int opening = platform.profiles.size();
        assertTrue(opening <= 2, "the whole roll fired at once: " + opening);

        for (int tick = 0; tick < 60; tick++) system.tick(config());
        int middle = platform.profiles.size();
        assertTrue(middle > opening, "nothing followed the opening");

        for (int tick = 0; tick < 200; tick++) system.tick(config());
        assertTrue(platform.profiles.size() > middle, "the tail never arrived");
    }

    @Test
    void chanceAndDistanceGateTheEvent() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderRollSystem system = new ThunderRollSystem(platform, budget());

        TempestConfig never = new TempestConfig();
        never.audio.giantRollChance = 0f;
        never.validate();
        assertFalse(system.onStrike(LISTENER, ORIGIN, 1, 1f, never, VIEW));

        TempestConfig near = config();
        near.audio.giantRollDistance = 10f;
        assertFalse(system.onStrike(LISTENER, new Vec3d(400, 0, 0), 1, 1f, near, VIEW),
            "a strike beyond the range must not open the sky");

        TempestConfig off = config();
        off.audio.giantRoll = false;
        assertFalse(system.onStrike(LISTENER, ORIGIN, 1, 1f, off, VIEW));
        assertEquals(0, system.activeCount());
    }

    @Test
    void aStormCannotChainRollsIntoContinuousNoise() {
        // The regression this guards: every strike opening its own ten-second roll during a
        // thunderstorm means the rumble literally never stops.
        RecordingPlatform platform = new RecordingPlatform();
        ThunderRollSystem system = new ThunderRollSystem(platform, budget());
        for (int index = 0; index < 30; index++) {
            system.onStrike(LISTENER, ORIGIN, index, 1f, config(), VIEW);
        }
        assertEquals(1, system.activeCount(), "only one roll may run at a time");

        // Even after the first one ends, the next strike has to wait out the cooldown.
        for (int tick = 0; tick <= GiantRollingThunderEffect.MAX_DURATION_TICKS + 5; tick++) {
            system.tick(config());
        }
        assertEquals(0, system.activeCount());
        assertFalse(system.onStrike(LISTENER, ORIGIN, 99, 1f, config(), VIEW),
            "a roll must not start again immediately");

        for (int tick = 0; tick < 200; tick++) system.tick(config());
        assertTrue(system.onStrike(LISTENER, ORIGIN, 98, 1f, config(), VIEW),
            "and it must become available again afterwards");

        system.clear();
        assertEquals(0, system.activeCount());
        assertEquals(0, system.pendingPulses());
    }

    @Test
    void theSharedBudgetStopsARollFromStarvingTheRestOfTheMix() {
        RecordingPlatform platform = new RecordingPlatform();
        VoiceBudget shared = new VoiceBudget(20, 6);
        ThunderRollSystem system = new ThunderRollSystem(platform, shared);
        system.trigger(LISTENER, ORIGIN, 0x99, 1f);
        system.trigger(LISTENER, ORIGIN, 0x9a, 1f);
        system.trigger(LISTENER, ORIGIN, 0x9b, 1f);
        for (int tick = 0; tick < 6; tick++) system.tick(config());
        assertTrue(platform.profiles.size() <= 6, "started " + platform.profiles.size() + " inside one window");
    }

    @Test
    void channelsAreReportedSeparatelyAndVastlyOutnumberTheSounds() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderRollSystem system = new ThunderRollSystem(platform, budget());
        List<DistantBoltCue> seen = new ArrayList<>();
        system.setBoltListener(seen::add);
        GiantRollingThunderEffect effect = system.trigger(LISTENER, ORIGIN, 0x4242, 1f, 160, 48, VIEW);
        for (int tick = 0; tick <= 170; tick++) system.tick(config());

        assertEquals(effect.totalBolts(), seen.size(), "every planned channel must be released once");
        assertEquals(48 * 8, seen.size(), "eight seconds at forty-eight a second");
        assertTrue(seen.size() > effect.totalPulses() * 10);
    }

    @Test
    void everyPulseThatPlaysIsReportedForTheCamera() {
        RecordingPlatform platform = new RecordingPlatform();
        ThunderRollSystem system = new ThunderRollSystem(platform, budget());
        List<ThunderPulse> heard = new ArrayList<>();
        system.setPulseListener(heard::add);
        system.trigger(LISTENER, ORIGIN, 0x2468, 1f);
        for (int tick = 0; tick <= GiantRollingThunderEffect.MAX_DURATION_TICKS; tick++) {
            system.tick(config());
        }
        assertEquals(platform.profiles.size(), heard.size());
        assertTrue(heard.stream().anyMatch(pulse -> pulse.impact() > 0.7f), "no strong transient reported");
    }

    private static VoiceBudget budget() {
        return new VoiceBudget(ThunderSystem.VOICE_WINDOW_TICKS, ThunderSystem.MAX_VOICES_PER_WINDOW);
    }

    private static final class RecordingPlatform implements ClientPlatform {
        private final List<ThunderProfile> profiles = new ArrayList<>();

        @Override
        public Path configDirectory() { return Path.of("."); }

        @Override
        public Vec3d cameraPosition() { return LISTENER; }

        @Override
        public void playThunder(ThunderProfile profile, Vec3d position, float volume, float pitch) {
            profiles.add(profile);
        }
    }
}
