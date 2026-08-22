package dev.tempestfx.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tempestfx.TempestFx;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and rewrites {@code config/tempestfx-server.json}.
 */
public final class ServerConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "tempestfx-server.json";

    private ServerConfig config = new ServerConfig().validate();

    public ServerConfig load(Path configDirectory) {
        Path path = configDirectory.resolve(FILE_NAME);
        ServerConfig loaded;
        try {
            loaded = Files.exists(path)
                ? GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ServerConfig.class)
                : new ServerConfig();
            if (loaded == null) loaded = new ServerConfig();
        } catch (RuntimeException | IOException failure) {
            loaded = new ServerConfig();
            TempestFx.log().warn("Could not read server config; defaults are active", failure);
        }
        config = loaded.validate();
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            TempestFx.log().warn("Could not write server config; running from memory", failure);
        }
        return config;
    }

    public ServerConfig get() { return config; }
}
