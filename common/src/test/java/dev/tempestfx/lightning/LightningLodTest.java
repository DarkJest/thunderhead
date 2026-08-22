package dev.tempestfx.lightning;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightningLodTest {
    @Test void selectsDocumentedDistanceBands() {
        assertEquals(LightningLod.FULL,LightningLod.forDistance(31.99));
        assertEquals(LightningLod.MEDIUM,LightningLod.forDistance(32));
        assertEquals(LightningLod.DISTANT,LightningLod.forDistance(96));
        assertEquals(LightningLod.ATMOSPHERIC,LightningLod.forDistance(256));
    }
}
