package dev.tempestfx.audio;

import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GiantRollingThunderEffectTest {
    private static final Vec3d LISTENER = new Vec3d(0, 70, 0);
    private static final Vec3d ORIGIN = new Vec3d(60, 70, -20);

    private static GiantRollingThunderEffect plan(long seed) {
        return GiantRollingThunderEffect.plan(seed, LISTENER, ORIGIN, 1f);
    }

    @Test
    void theEventOpensWithACrackThenABoom() {
        List<ThunderPulse> pulses = plan(0xfeed).pulses();
        assertEquals(ThunderProfile.ROLL_CRACK, pulses.getFirst().profile());
        assertEquals(0, pulses.getFirst().delayTicks());

        ThunderPulse boom = pulses.stream()
            .filter(pulse -> pulse.profile() == ThunderProfile.ROLL_BOOM)
            .findFirst().orElseThrow();
        assertTrue(boom.delayTicks() > 0 && boom.delayTicks() < 8, "boom at " + boom.delayTicks());
        assertTrue(boom.pitch() < pulses.getFirst().pitch(), "the body must be lower than the crack");
        assertEquals(1f, boom.impact(), 1e-6, "the boom is what the camera should feel most");
    }

    @Test
    void theEventIsBuiltFromSeveralDistinctLayers() {
        Set<ThunderProfile> used = EnumSet.noneOf(ThunderProfile.class);
        for (ThunderPulse pulse : plan(0x1234).pulses()) used.add(pulse.profile());
        assertTrue(used.size() >= 5, "only used " + used);
        assertTrue(used.contains(ThunderProfile.ROLL_WALL));
        assertTrue(used.contains(ThunderProfile.ROLL_FAR));
        assertTrue(used.contains(ThunderProfile.ROLL_TAIL));
    }

    @Test
    void pulsesAreSpreadOverFiveToTenSecondsAndOrdered() {
        for (long seed = 0; seed < 120; seed++) {
            List<ThunderPulse> pulses = plan(seed).pulses();
            int previous = -1;
            for (ThunderPulse pulse : pulses) {
                assertTrue(pulse.delayTicks() >= previous, "pulses must be ordered");
                assertTrue(pulse.delayTicks() <= GiantRollingThunderEffect.MAX_DURATION_TICKS);
                previous = pulse.delayTicks();
            }
            int last = pulses.getLast().delayTicks();
            assertTrue(last >= 40, "event only lasted " + last + " ticks");
        }
    }

    @Test
    void gapsAreIrregularSoRollsOverlapInsteadOfMarching() {
        List<Integer> gaps = new ArrayList<>();
        List<ThunderPulse> pulses = plan(0xabcdef).pulses();
        for (int index = 1; index < pulses.size(); index++) {
            gaps.add(pulses.get(index).delayTicks() - pulses.get(index - 1).delayTicks());
        }
        assertTrue(Set.copyOf(gaps).size() >= 4, "gaps repeat, the roll would sound mechanical: " + gaps);
        // Clips run for seconds; a gap shorter than that means two rolls are audible at once.
        assertTrue(gaps.stream().anyMatch(gap -> gap < 20), "no overlapping rolls in " + gaps);
    }

    @Test
    void rollsArriveFromDifferentBearingsAroundTheListener() {
        Set<Integer> octants = new java.util.HashSet<>();
        for (ThunderPulse pulse : plan(0x77).pulses()) {
            double bearing = Math.atan2(pulse.position().z() - LISTENER.z(), pulse.position().x() - LISTENER.x());
            octants.add((int) Math.floor((bearing + Math.PI) / (Math.PI / 4)));
        }
        assertTrue(octants.size() >= 3, "the roll never moved across the sky: " + octants);
    }

    @Test
    void distantGrumbleIsQuietAndLowWhileTheOpeningIsLoud() {
        List<ThunderPulse> pulses = plan(0x5150).pulses();
        ThunderPulse far = pulses.stream()
            .filter(pulse -> pulse.profile() == ThunderProfile.ROLL_FAR)
            .findFirst().orElseThrow();
        assertTrue(far.gain() < 0.45f, "distant grumble too loud: " + far.gain());
        assertTrue(far.impact() < 0.2f, "distant grumble should barely move the camera");
        assertTrue(far.position().distanceTo(LISTENER) > 150, "grumble should come from far away");
    }

    @Test
    void differentSeedsProduceDifferentEventsAndTheSameSeedRepeats() {
        assertEquals(plan(7).pulses(), plan(7).pulses());
        assertNotEquals(plan(7).pulses(), plan(8).pulses());
    }

    @Test
    void tickingReleasesEveryPulseExactlyOnceThenFinishes() {
        GiantRollingThunderEffect effect = plan(0xc0ffee);
        int expected = effect.totalPulses();
        List<ThunderPulse> released = new ArrayList<>();
        for (int tick = 0; tick <= GiantRollingThunderEffect.MAX_DURATION_TICKS + 5; tick++) {
            effect.tick(released::add, cue -> { });
        }
        assertEquals(expected, released.size());
        assertEquals(0, effect.remainingPulses());
        assertTrue(effect.finished());
    }

    @Test
    void channelsAreCountedPerSecondNotPerEvent() {
        // "50" means fifty strokes every second for as long as the roll lasts, not fifty in total.
        GiantRollingThunderEffect effect = GiantRollingThunderEffect.plan(1, LISTENER, ORIGIN, 1f, 200, 50);
        assertEquals(50, effect.flashRate());
        assertEquals(500, effect.totalBolts(), "ten seconds at fifty a second");

        GiantRollingThunderEffect shorter = GiantRollingThunderEffect.plan(1, LISTENER, ORIGIN, 1f, 100, 50);
        assertEquals(250, shorter.totalBolts(), "half the time, half the strokes, same rate");
    }

    @Test
    void theRateHoldsAcrossEverySecondOfTheEvent() {
        GiantRollingThunderEffect effect = GiantRollingThunderEffect.plan(3, LISTENER, ORIGIN, 1f, 200, 40);
        int[] perSecond = new int[10];
        for (DistantBoltCue cue : effect.bolts()) {
            perSecond[Math.min(9, cue.delayTicks() / 20)]++;
        }
        for (int second = 0; second < perSecond.length; second++) {
            assertTrue(perSecond[second] >= 25 && perSecond[second] <= 55,
                "second " + second + " fired " + perSecond[second] + " strokes");
        }
    }

    @Test
    void channelsHangVerticallyAndLeanAsTheyDescend() {
        for (DistantBoltCue cue : GiantRollingThunderEffect.plan(9, LISTENER, ORIGIN, 1f, 160, 30).bolts()) {
            assertTrue(cue.height() > 60, "channel is not tall enough to read as vertical: " + cue.height());
            assertTrue(cue.lean() > 4, "a perfectly plumb channel looks like a fence post");
            // Lean has to stay well under the drop, otherwise the stroke reads as horizontal.
            assertTrue(cue.lean() < cue.height() * 0.75,
                "channel is too close to horizontal: lean " + cue.lean() + " over " + cue.height());
        }
    }

    @Test
    void bigStormsAreRareAndModestOnesAreCommon() {
        int total = 600;
        int large = 0;
        int huge = 0;
        List<Integer> rates = new ArrayList<>(total);
        for (long seed = 0; seed < total; seed++) {
            int rate = plan(seed).flashRate();
            rates.add(rate);
            if (rate > 60) large++;
            if (rate > 85) huge++;
        }
        rates.sort(Integer::compareTo);

        assertTrue(rates.get(total / 2) <= 35, "typical storm too dense: " + rates.get(total / 2));
        assertTrue(large > 0 && large < total / 4, "sixty-plus should be uncommon: " + large + "/" + total);
        assertTrue(huge > 0 && huge < total / 12, "sky-splitters were not rare: " + huge + "/" + total);
    }

    @Test
    void eventsRunBetweenFourAndFifteenSeconds() {
        for (long seed = 0; seed < 200; seed++) {
            int duration = plan(seed).durationTicks();
            assertTrue(duration >= GiantRollingThunderEffect.MIN_DURATION_TICKS
                && duration <= GiantRollingThunderEffect.MAX_DURATION_TICKS, "duration " + duration);
        }
    }

    @Test
    void theWallStaysOneTightFrontRatherThanScatteringAround() {
        // Spread the strokes over the whole sky and they stop reading as a storm front.
        GiantRollingThunderEffect effect = plan(0x1357);
        double minBearing = Double.MAX_VALUE;
        double maxBearing = -Double.MAX_VALUE;
        double minDistance = Double.MAX_VALUE;
        double maxDistance = 0;
        for (DistantBoltCue cue : effect.bolts()) {
            double dx = cue.top().x() - LISTENER.x();
            double dz = cue.top().z() - LISTENER.z();
            minBearing = Math.min(minBearing, Math.atan2(dz, dx));
            maxBearing = Math.max(maxBearing, Math.atan2(dz, dx));
            double distance = Math.hypot(dx, dz);
            minDistance = Math.min(minDistance, distance);
            maxDistance = Math.max(maxDistance, distance);
        }
        double arc = maxBearing - minBearing;
        assertTrue(arc < 2.6, "the front spans " + Math.toDegrees(arc) + " degrees of sky");
        assertTrue(maxDistance / minDistance < 1.6,
            "depth spread " + minDistance + ".." + maxDistance + " turns the wall into scattered points");
        assertTrue(effect.bolts().getLast().delayTicks() > effect.durationTicks() / 2, "nothing late");
    }

    @Test
    void theFrontIsPlacedInsideTheViewDistance() {
        for (double view : new double[] { 96, 160, 240, 512 }) {
            GiantRollingThunderEffect effect = GiantRollingThunderEffect.plan(
                4, LISTENER, ORIGIN, 1f, 120, 20, view);
            for (DistantBoltCue cue : effect.bolts()) {
                double distance = Math.hypot(cue.top().x() - LISTENER.x(), cue.top().z() - LISTENER.z());
                assertTrue(distance < view, "channel at " + distance + " is behind the fog at " + view);
            }
        }
    }

    @Test
    void aLongDenseEventStaysWithinItsGenerationBudget() {
        GiantRollingThunderEffect effect = GiantRollingThunderEffect.plan(
            11, LISTENER, ORIGIN, 1f, GiantRollingThunderEffect.MAX_DURATION_TICKS, 300);
        assertTrue(effect.totalBolts() <= 900, "generated " + effect.totalBolts() + " channels");
    }

    @Test
    void theEffectNeverLooksAtLightningGeometry() {
        // Planning takes a position, a seed and an intensity: no channel, no segments, no effect.
        GiantRollingThunderEffect effect = GiantRollingThunderEffect.plan(1, LISTENER, ORIGIN, 1f);
        assertTrue(effect.totalPulses() > 8);
    }
}
