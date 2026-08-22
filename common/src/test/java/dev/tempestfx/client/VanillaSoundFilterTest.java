package dev.tempestfx.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VanillaSoundFilterTest {
    @Test void suppressesOnlyVanillaLightningPairWhenEnabled(){
        assertTrue(VanillaSoundFilter.shouldSuppress("minecraft:entity.lightning_bolt.thunder",true));
        assertTrue(VanillaSoundFilter.shouldSuppress("minecraft:entity.lightning_bolt.impact",true));
        assertFalse(VanillaSoundFilter.shouldSuppress("minecraft:weather.rain",true));
        assertFalse(VanillaSoundFilter.shouldSuppress("minecraft:entity.lightning_bolt.thunder",false));
    }
}
