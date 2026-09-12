package dev.tempestfx.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NearMissIntersectionTest {
    @Test void excludesBoundaryStraddlingPlayerAndWideMob() {
        assertTrue(NearMissDamage.intersectsVanillaBox(2.8, 0, -.3, 3.4, 1.8, .3));
        assertTrue(NearMissDamage.intersectsVanillaBox(2, 0, -1, 6, 3, 1));
        assertTrue(NearMissDamage.intersectsVanillaBox(-.3, -4, -.3, .3, -2.2, .3));
    }
    @Test void merelyTouchingVanillaBoxIsNotIntersection() {
        assertFalse(NearMissDamage.intersectsVanillaBox(3, 0, -.3, 3.6, 1.8, .3));
        assertFalse(NearMissDamage.intersectsVanillaBox(-.3, 9, -.3, .3, 10.8, .3));
    }
}
