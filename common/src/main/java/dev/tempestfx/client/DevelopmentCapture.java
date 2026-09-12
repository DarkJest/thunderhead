package dev.tempestfx.client;

import dev.tempestfx.TempestFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in, finite integration run. Use only with a disposable singleplayer QA save. */
final class DevelopmentCapture {
    private final boolean enabled = Boolean.getBoolean("tempestfx.capture");
    private int ticks;
    private int capturedTick = -1;
    private final String run = Long.toString(System.currentTimeMillis());

    void tick(Minecraft minecraft, TempestFxClient client) {
        if (!enabled || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        ticks++;
        if (ticks == 30) {
            minecraft.options.pauseOnLostFocus = false;
            minecraft.player.connection.sendCommand("gamemode spectator");
            minecraft.player.connection.sendCommand("tp @s 152 90 173 0 -15");
            minecraft.player.connection.sendCommand("time set midnight");
            minecraft.player.connection.sendCommand("weather clear");
            client.config().general.debug = true;
        }
        if (ticks == 120 || ticks == 180 || ticks == 240) {
            client.debugStrike(100, "auto", 12345L);
            TempestFx.log().info("QA strike run={} tick={} seed=12345", run, ticks);
        }
        if (ticks == 400) {
            TempestFx.log().info("QA capture complete run={}; stopping disposable test client", run);
            minecraft.stop();
        }
    }

    void frame(Minecraft minecraft, String compositorStatus) {
        if (!enabled || ticks == capturedTick || ticks < 119 || ticks > 250) return;
        if (!((ticks >= 119 && ticks <= 131) || (ticks >= 180 && ticks <= 191)
            || (ticks >= 240 && ticks <= 250))) return;
        capturedTick = ticks;
        Screenshot.grab(minecraft.gameDirectory, "qa-" + run + "-" + ticks + ".png",
            minecraft.getMainRenderTarget(), result -> TempestFx.log().info("QA screenshot: {}", result.getString()));
        TempestFx.log().info("QA frame tick={} compositor={} fps={}", ticks, compositorStatus, minecraft.getFps());
    }
}
