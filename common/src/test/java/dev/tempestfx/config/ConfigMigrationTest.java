package dev.tempestfx.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigMigrationTest {
    @TempDir Path directory;
    @Test void newInstallUsesRealisticButLegacyKeepsCinematic() throws Exception {
        assertEquals(RealismProfile.REALISTIC, new ConfigManager(directory).load().general.profile);
        Files.writeString(directory.resolve("tempestfx.json"), "{\"lightning\":{\"thickness\":1.7}}");
        var legacy = new ConfigManager(directory).load();
        assertEquals(RealismProfile.CINEMATIC, legacy.general.profile);
        assertEquals(1.7f, legacy.lightning.thickness);
    }
    @Test void accessibilityRoundTripRetainsRequestedSettings() {
        var manager = new ConfigManager(directory);
        var config = manager.load();
        config.general.reducedFlashing = true;
        config.lightning.returnStrokes = 4;
        manager.saveQuietly();
        var loaded = new ConfigManager(directory).load();
        assertEquals(4, loaded.lightning.returnStrokes);
        assertEquals(0, loaded.effectiveReturnStrokes());
        loaded.general.reducedFlashing = false;
        assertEquals(4, loaded.effectiveReturnStrokes());
    }
}
