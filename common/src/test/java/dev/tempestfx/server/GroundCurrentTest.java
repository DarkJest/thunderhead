package dev.tempestfx.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundCurrentTest {
    @Test void gapsAirborneAndRangeStopCurrent() {
        assertEquals(0, GroundCurrent.damage(4,9,5,0,true));
        assertEquals(0, GroundCurrent.damage(4,9,5,1,false));
        assertEquals(0, GroundCurrent.damage(10,9,5,1,true));
        assertTrue(GroundCurrent.damage(4,9,5,1.2f,true)>GroundCurrent.damage(4,9,5,.2f,true));
        assertTrue(GroundCurrent.damage(3.1,9,5,100,true)<=5);
    }
    @Test void sideFlashRequiresConductorAndLineOfSight() {
        assertEquals(0, GroundCurrent.sideFlash(4,6,5,true,false));
        assertEquals(0, GroundCurrent.sideFlash(4,6,5,false,true));
        assertTrue(GroundCurrent.sideFlash(4,6,5,true,true)>0);
    }
}
