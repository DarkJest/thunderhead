package dev.tempestfx.effect;
import dev.tempestfx.audio.DistantBoltCue;
import dev.tempestfx.config.*;
import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DistantBoltIntensityTest {
    @Test void intensityIsNotSquaredAndRealisticStillAcceptsExplicitRollCues() {
        var config = new TempestConfig(); config.general.profile=RealismProfile.REALISTIC;
        var system = new DistantBoltSystem();
        var full = system.onCue(new DistantBoltCue(0,new Vec3d(0,200,0),Vec3d.ZERO,1,42),config);
        var half = system.onCue(new DistantBoltCue(0,new Vec3d(0,200,0),Vec3d.ZERO,.5f,42),config);
        assertNotNull(half); assertEquals(full.geometry(),half.geometry());
        assertEquals(full.brightness(2,false,false)*.5f,half.brightness(2,false,false),1e-6);
        config.general.reducedFlashing=true;
        assertNull(system.onCue(new DistantBoltCue(0,new Vec3d(0,200,0),Vec3d.ZERO,1,42),config));
    }
}
