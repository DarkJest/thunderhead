package dev.tempestfx.effect;

import dev.tempestfx.api.LightningEnvironment;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.Vec3d;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityDischargeSystemTest {
    private static final int ENTITY_ID = 7;

    @Test
    void movingTargetKeepsArcingWhileAStillOneShedsCharge() {
        TempestConfig config = new TempestConfig().validate();
        EntityDischargeSystem moving = new EntityDischargeSystem();
        EntityDischargeSystem still = new EntityDischargeSystem();
        moving.onStrike(strike(), config, List.of(target(0)));
        still.onStrike(strike(), config, List.of(target(0)));

        for (int tick = 1; tick <= 20; tick++) {
            int step = tick;
            moving.tick(config, id -> target(step * 0.2), tick);
            still.tick(config, id -> stationary(), tick);
        }

        assertEquals(1, moving.activeCount(), "a moving target must keep arcing");
        assertEquals(0, still.activeCount(), "a stationary target must lose the charge quickly");
    }

    @Test
    void targetsOutsideTheRadiusAreNeverCharged() {
        TempestConfig config = new TempestConfig();
        config.impact.entityDischargeRadius = 4;
        config.validate();
        EntityDischargeSystem system = new EntityDischargeSystem();
        system.onStrike(strike(), config, List.of(farTarget()));
        assertEquals(0, system.activeCount());
    }

    @Test
    void disablingTheFeatureStopsEverything() {
        TempestConfig config = new TempestConfig();
        config.impact.entityDischarge = false;
        config.validate();
        EntityDischargeSystem system = new EntityDischargeSystem();
        assertEquals(0, system.onStrike(strike(), config, List.of(target(0))));
        assertEquals(0, system.activeCount());
    }

    @Test
    void vanishedEntitiesAreDroppedInsteadOfLeaking() {
        TempestConfig config = new TempestConfig().validate();
        EntityDischargeSystem system = new EntityDischargeSystem();
        system.onStrike(strike(), config, List.of(target(0)));
        assertEquals(1, system.activeCount());
        system.tick(config, id -> null, 1);
        assertEquals(0, system.activeCount());
    }

    @Test
    void chargeIsProportionalToProximityAndStaysNormalised() {
        TempestConfig config = new TempestConfig().validate();
        EntityDischargeSystem near = new EntityDischargeSystem();
        EntityDischargeSystem far = new EntityDischargeSystem();
        near.onStrike(strike(), config, List.of(at(1.0)));
        far.onStrike(strike(), config, List.of(at(8.0)));

        float nearCharge = near.discharges().getFirst().charge();
        float farCharge = far.discharges().getFirst().charge();
        assertTrue(nearCharge > farCharge, nearCharge + " should exceed " + farCharge);
        assertTrue(nearCharge <= 1f && farCharge >= 0f);
    }

    private static LightningStrikeFxEvent strike() {
        return new LightningStrikeFxEvent(Vec3d.ZERO, 0x1357, 1, LightningEnvironment.land(0x777777, true));
    }

    /** Target one block from the strike that moved {@code offset} blocks since the previous tick. */
    private static DischargeTarget target(double offset) {
        return new DischargeTarget(ENTITY_ID, true, new Vec3d(1 + offset, 0, 0), new Vec3d(1 + offset - 0.2, 0, 0),
            0.6f, 1.8f);
    }

    private static DischargeTarget stationary() {
        return new DischargeTarget(ENTITY_ID, true, new Vec3d(1, 0, 0), new Vec3d(1, 0, 0), 0.6f, 1.8f);
    }

    private static DischargeTarget at(double distance) {
        return new DischargeTarget(ENTITY_ID, true, new Vec3d(distance, 0, 0), new Vec3d(distance - 0.2, 0, 0),
            0.6f, 1.8f);
    }

    private static DischargeTarget farTarget() {
        return new DischargeTarget(ENTITY_ID, true, new Vec3d(40, 0, 0), new Vec3d(39.8, 0, 0), 0.6f, 1.8f);
    }
}
