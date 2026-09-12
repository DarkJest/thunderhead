package dev.tempestfx.effect;

import dev.tempestfx.api.*;
import dev.tempestfx.lightning.FlashTimeline;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlashEventSystemTest {
    @Test void contactKeepsIdentityAndOverridesAndFiresOnce() {
        var options = StrikeOptions.builder().origin(new Vec3d(0, 180, 0)).build();
        var event = new LightningStrikeFxEvent(new Vec3d(0, 64, 0), 12345, 1,
            LightningEnvironment.land(0x888888, true), StrikeTarget.none(), 0, options);
        var plan = FlashTimeline.plan(event.seed(), 4, true);
        var system = new FlashEventSystem();
        var contacts = new ArrayList<LightningStrikeFxEvent>();
        system.add(event, plan);
        for (int tick = 0; tick < 40; tick++) system.tick(contacts::add);
        assertEquals(plan.pulses().size(), contacts.size());
        for (int i = 0; i < contacts.size(); i++) {
            var contact = contacts.get(i);
            assertEquals(event.position(), contact.position());
            assertEquals(event.seed(), contact.seed());
            assertSame(options, contact.options());
            assertEquals(i, contact.stroke());
        }
        assertEquals(0, system.pendingCount());
    }

    @Test void overloadAndClearAreBounded() {
        var system = new FlashEventSystem();
        var event = new LightningStrikeFxEvent(Vec3d.ZERO, 7, 1, LightningEnvironment.land(0, false));
        for (int i = 0; i < 1000; i++) system.add(event, FlashTimeline.plan(i, 4, true));
        assertTrue(system.pendingCount() <= 256);
        system.clear();
        system.tick(e -> fail("cleared event fired"));
    }
}
