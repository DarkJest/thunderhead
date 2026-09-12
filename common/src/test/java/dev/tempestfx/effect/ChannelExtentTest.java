package dev.tempestfx.effect;
import dev.tempestfx.api.*;
import dev.tempestfx.config.*;
import dev.tempestfx.lightning.*;
import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChannelExtentTest {
    @Test void distantEndpointDoesNotCullNearbyPortion() {
        var config=new TempestConfig();config.general.profile=RealismProfile.REALISTIC;
        var manager=new EffectManager(new LightningEffectFactory(new MidpointDisplacementStrategy()));
        var event=new LightningStrikeFxEvent(new Vec3d(550,200,0),42,1,LightningEnvironment.land(0,false),StrikeTarget.none(),0,
            StrikeOptions.builder().origin(new Vec3d(250,200,0)).kind(LightningKind.INTERCLOUD).build());
        manager.onStrike(event,new Vec3d(0,200,0),config);
        assertNotNull(manager.latest());assertEquals(1,manager.lightning().size());
        manager.clear();
        var far=new LightningStrikeFxEvent(new Vec3d(2000,200,0),42,1,LightningEnvironment.land(0,false),StrikeTarget.none(),0,
            StrikeOptions.builder().origin(new Vec3d(1700,200,0)).kind(LightningKind.INTERCLOUD).build());
        manager.onStrike(far,new Vec3d(0,200,0),config);
        assertNull(manager.latest());assertTrue(manager.lightning().isEmpty());
    }
}
