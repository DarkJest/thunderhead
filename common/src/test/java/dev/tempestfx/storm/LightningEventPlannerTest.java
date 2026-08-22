package dev.tempestfx.storm;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightningEventPlannerTest {
    private static final Vec3d CAMERA = new Vec3d(0, 70, 0);

    private static StormSample storming(long gameTime) {
        return new StormSample(true, 1f, 1f, gameTime, 192, 256);
    }

    /** Runs a charged storm for a while and collects everything it raised. */
    private static List<AmbientDischarge> run(TempestConfig config, int ticks) {
        StormElectricState state = new StormElectricState();
        LightningEventPlanner planner = new LightningEventPlanner();
        List<AmbientDischarge> raised = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            StormSample sample = storming(tick);
            state.update(sample);
            AmbientDischarge discharge = planner.plan(state, sample, config, CAMERA);
            if (discharge != null) raised.add(discharge);
        }
        return raised;
    }

    @Test
    void aChargedStormKeepsProducingActivity() {
        List<AmbientDischarge> raised = run(new TempestConfig().validate(), 12000);
        assertTrue(raised.size() > 20, "a ten-minute thunderstorm raised only " + raised.size() + " events");
    }

    @Test
    void aStormIsAliveWithoutStrobing() {
        // The complaint this bound exists for is "it flashes constantly". At full charge and the
        // default pace, ambient activity has to stay several seconds apart.
        List<AmbientDischarge> raised = run(new TempestConfig().validate(), 12000);
        double secondsBetween = 12000 / 20.0 / raised.size();
        assertTrue(secondsBetween > 4, "one ambient event every " + secondsBetween + " seconds is strobing");
    }

    @Test
    void bareChannelsAcrossTheSkyStayTheMinority() {
        List<AmbientDischarge> raised = run(new TempestConfig().validate(), 60000);
        long horizontal = raised.stream()
            .filter(discharge -> discharge.type() == DischargeType.CLOUD_TO_CLOUD).count();
        assertTrue(horizontal * 2 < raised.size(),
            horizontal + " of " + raised.size() + " events were bare channels");
    }

    @Test
    void raisingThePaceSettingProducesMoreActivity() {
        TempestConfig faster = new TempestConfig().validate();
        faster.sky.activityRate = 3f;
        assertTrue(run(faster, 12000).size() > run(new TempestConfig().validate(), 12000).size());
    }

    @Test
    void nothingHappensBeforeTheStormHasCharged() {
        StormElectricState state = new StormElectricState();
        LightningEventPlanner planner = new LightningEventPlanner();
        TempestConfig config = new TempestConfig().validate();
        StormSample sample = storming(0);
        state.update(sample);
        assertNull(planner.plan(state, sample, config, CAMERA), "a storm must ramp up, not switch on");
    }

    @Test
    void aClearSkyProducesNothingAtAll() {
        StormElectricState state = new StormElectricState();
        LightningEventPlanner planner = new LightningEventPlanner();
        TempestConfig config = new TempestConfig().validate();
        for (int tick = 0; tick < 2000; tick++) {
            StormSample sample = StormSample.calm(tick, 192, 256);
            state.update(sample);
            assertNull(planner.plan(state, sample, config, CAMERA));
        }
    }

    @Test
    void theMasterSwitchAndThePaceSettingBothStopIt() {
        TempestConfig off = new TempestConfig().validate();
        off.sky.skyActivity = false;
        assertEquals(0, run(off, 2000).size());

        TempestConfig still = new TempestConfig().validate();
        still.sky.activityRate = 0f;
        assertEquals(0, run(still, 2000).size());
    }

    @Test
    void switchingOffAnArchetypeRemovesItAndOnlyIt() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.intracloud = false;
        Set<DischargeType> seen = EnumSet.noneOf(DischargeType.class);
        for (AmbientDischarge discharge : run(config, 6000)) seen.add(discharge.type());
        assertTrue(!seen.contains(DischargeType.INTRACLOUD), "intracloud was switched off");
        assertTrue(seen.contains(DischargeType.CLOUD_TO_CLOUD), "cloud-to-cloud should still fire");
    }

    @Test
    void megaflashesAreRareEnoughToStayMemorable() {
        List<AmbientDischarge> raised = run(new TempestConfig().validate(), 60000);
        long mega = raised.stream().filter(discharge -> discharge.type() == DischargeType.MEGAFLASH).count();
        assertTrue(mega * 50 < raised.size(),
            mega + " megaflashes out of " + raised.size() + " events is not rare");
    }

    @Test
    void aMegaflashCanBeMadeCommonForTesting() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.megaflashRarity = 1;
        List<AmbientDischarge> raised = run(config, 3000);
        assertTrue(raised.stream().allMatch(discharge -> discharge.type() == DischargeType.MEGAFLASH));
    }

    @Test
    void everyEventIsPlacedInTheSkyAndOutInTheStorm() {
        for (AmbientDischarge discharge : run(new TempestConfig().validate(), 6000)) {
            assertTrue(discharge.origin().finite() && discharge.target().finite());
            assertTrue(discharge.span() > 10, "a channel with no length: " + discharge.span());
            double distance = CAMERA.distanceTo(discharge.midpoint());
            assertTrue(distance > 60, "an ambient discharge landed on top of the player: " + distance);
            assertTrue(discharge.origin().y() > 100, "ambient discharges belong in the cloud layer");
        }
    }

    @Test
    void spansMatchTheArchetype() {
        for (AmbientDischarge discharge : run(new TempestConfig().validate(), 20000)) {
            switch (discharge.type()) {
                case INTRACLOUD -> assertTrue(discharge.span() < 90, "intracloud span " + discharge.span());
                case CLOUD_TO_CLOUD -> assertTrue(discharge.span() >= 100 && discharge.span() < 340,
                    "cloud-to-cloud span " + discharge.span());
                case MEGAFLASH -> assertTrue(discharge.span() > 450, "megaflash span " + discharge.span());
                default -> throw new AssertionError("ground strikes are not planned here: " + discharge.type());
            }
        }
    }
}
