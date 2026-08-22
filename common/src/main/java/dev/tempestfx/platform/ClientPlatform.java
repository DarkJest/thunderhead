package dev.tempestfx.platform;

import dev.tempestfx.audio.ThunderProfile;
import dev.tempestfx.math.Vec3d;
import java.nio.file.Path;

public interface ClientPlatform {
    Path configDirectory();
    Vec3d cameraPosition();
    void playThunder(ThunderProfile profile, Vec3d position, float volume, float pitch);
}
