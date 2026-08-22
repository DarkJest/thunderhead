package dev.tempestfx.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tempestfx.TempestFx;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and rewrites {@code config/tempestfx.json}.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private TempestConfig config = new TempestConfig().validate();

    public ConfigManager(Path configDirectory) { path = configDirectory.resolve("tempestfx.json"); }

    public TempestConfig load() {
        TempestConfig loaded;
        try {
            loaded = Files.exists(path)
                ? GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), TempestConfig.class)
                : new TempestConfig();
            if (loaded == null) loaded = new TempestConfig();
        } catch (RuntimeException | IOException failure) {
            loaded = new TempestConfig();
            TempestFx.log().warn("Could not read config; defaults are active", failure);
        }
        config = loaded.validate();
        try {
            save();
        } catch (IOException failure) {
            TempestFx.log().warn("Could not write config; running from memory", failure);
        }
        return config;
    }

    public void save() throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
    }

    /**
     * Writes the current values, treating failure the way loading does.
     */
    public void saveQuietly() {
        try {
            save();
        } catch (IOException failure) {
            TempestFx.log().warn("Could not write config; the change is live but not saved", failure);
        }
    }

    public TempestConfig get() { return config; }
}
