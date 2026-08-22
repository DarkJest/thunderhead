package dev.tempestfx.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;

/**
 * Reversible capture preset for the development showcase commands.
 */
public final class ShowcaseCameraController {
    private boolean enabled;
    private boolean previousHideGui;
    private boolean previousSmoothCamera;
    private boolean previousBobView;
    private float previousFlyingSpeed = 0.05f;
    private CameraType previousCameraType = CameraType.FIRST_PERSON;
    private GameType previousGameType;
    private float flyingSpeed = 0.08f;

    public boolean enable(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.options == null) return false;
        if (!enabled) {
            previousHideGui = minecraft.options.hideGui;
            previousSmoothCamera = minecraft.options.smoothCamera;
            previousBobView = minecraft.options.bobView().get();
            previousCameraType = minecraft.options.getCameraType();
            previousFlyingSpeed = minecraft.player.getAbilities().getFlyingSpeed();
            previousGameType = minecraft.gameMode == null ? null : minecraft.gameMode.getPlayerMode();
        }

        enabled = true;
        minecraft.options.hideGui = true;
        minecraft.options.smoothCamera = true;
        minecraft.options.bobView().set(false);
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.player.connection.sendCommand("gamemode spectator");
        applyFlyingSpeed(minecraft);
        return true;
    }

    public void disable(Minecraft minecraft) { disable(minecraft, true); }

    public void disable(Minecraft minecraft, boolean restoreGameType) {
        if (!enabled) return;
        enabled = false;

        if (minecraft.options != null) {
            minecraft.options.hideGui = previousHideGui;
            minecraft.options.smoothCamera = previousSmoothCamera;
            minecraft.options.bobView().set(previousBobView);
            minecraft.options.setCameraType(previousCameraType);
        }
        if (minecraft.player != null) {
            minecraft.player.getAbilities().setFlyingSpeed(previousFlyingSpeed);
            if (restoreGameType && previousGameType != null) {
                minecraft.player.connection.sendCommand("gamemode " + previousGameType.getName());
            }
        }
        previousGameType = null;
    }

    public void tick(Minecraft minecraft) {
        if (enabled) applyFlyingSpeed(minecraft);
    }

    public void setFlyingSpeed(Minecraft minecraft, float speed) {
        flyingSpeed = Math.max(0.01f, Math.min(0.5f, speed));
        if (enabled) applyFlyingSpeed(minecraft);
    }

    public boolean enabled() { return enabled; }

    public float flyingSpeed() { return flyingSpeed; }

    private void applyFlyingSpeed(Minecraft minecraft) {
        if (minecraft.player != null) minecraft.player.getAbilities().setFlyingSpeed(flyingSpeed);
    }
}
