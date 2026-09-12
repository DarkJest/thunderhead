package dev.tempestfx.platform;

import dev.tempestfx.audio.ThunderProfile;
import dev.tempestfx.math.Vec3d;
import java.nio.file.Path;

public interface ClientPlatform {
    Path configDirectory();
    Vec3d cameraPosition();
    void playThunder(ThunderProfile profile, Vec3d position, float volume, float pitch);
    /** Local shelter attenuation at arrival. Implementations must not load chunks. */
    default float thunderTransmission(Vec3d source) { return 1f; }
}
