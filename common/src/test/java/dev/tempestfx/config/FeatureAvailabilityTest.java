package dev.tempestfx.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeatureAvailabilityTest {
    @Test void profileRestrictionsAreVisibleWithoutErasingPreferences() {
        var config = new TempestConfig(); config.general.profile = RealismProfile.REALISTIC;
        assertTrue(config.impact.shockwave);
        assertEquals("option.tempestfx.requires_cinematic", FeatureAvailability.reason("shockwave", config));
        assertNull(FeatureAvailability.reason("distant_bolts", config), "explicit roll visuals work in Realistic");
        config.general.profile = RealismProfile.CINEMATIC;
        assertNull(FeatureAvailability.reason("shockwave", config));
        assertEquals("option.tempestfx.requires_realistic", FeatureAvailability.reason("surface_lighting", config));
        assertTrue(config.impact.shockwave);
    }
    @Test void effectAndAccessibilityDependenciesAreExplained() {
        var config = new TempestConfig();
        config.impact.shockwave = false;
        assertEquals("option.tempestfx.requires_shockwave", FeatureAvailability.reason("air_distortion", config));
        config.general.reducedFlashing = true;
        assertEquals("option.tempestfx.reduced_override", FeatureAvailability.reason("distant_bolts", config));
    }
}
