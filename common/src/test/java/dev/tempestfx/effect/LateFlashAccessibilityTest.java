package dev.tempestfx.effect;
import dev.tempestfx.api.*;
import dev.tempestfx.config.*;
import dev.tempestfx.lightning.*;
import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LateFlashAccessibilityTest {
    @Test void lateArrivalDoesNotReplayExpiredChannelOrContacts() {
        var manager=new EffectManager(new LightningEffectFactory(new MidpointDisplacementStrategy()));
        var sim=new FlashSimulation(manager); var cfg=new TempestConfig();
        sim.enqueue(new LightningStrikeFxEvent(Vec3d.ZERO,42,1,LightningEnvironment.land(0,false)),100,3);
        sim.tick(Vec3d.ZERO,cfg,e->fail("expired contact"),e->assertEquals(100,manager.latest().age()));
        assertTrue(manager.lightning().isEmpty());
        for(int i=0;i<10;i++)sim.tick(Vec3d.ZERO,cfg,e->fail("expired contact"),e->{});
    }
    @Test void reducedModeLimitsWholeStormInsteadOfOnlyIndividualFlashes() {
        var manager=new EffectManager(new LightningEffectFactory(new MidpointDisplacementStrategy()));
        var sim=new FlashSimulation(manager); var cfg=new TempestConfig(); cfg.general.reducedFlashing=true;
        int[] observed={0};
        for(int i=0;i<100;i++)sim.enqueue(new LightningStrikeFxEvent(Vec3d.ZERO,i,1,LightningEnvironment.land(0,false)));
        sim.tick(Vec3d.ZERO,cfg,e->{},e->observed[0]++);
        assertEquals(1,manager.lightning().size()); assertEquals(100,observed[0],"API still observes every event");
    }
}
