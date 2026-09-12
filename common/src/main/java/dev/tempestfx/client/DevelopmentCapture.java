package dev.tempestfx.client;

import dev.tempestfx.TempestFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in, finite integration run. Use only with a disposable singleplayer QA save. */
final class DevelopmentCapture {
    private final boolean enabled = Boolean.getBoolean("tempestfx.capture");
    private int ticks;
    private int capturedTick = -1;
    private int burstFrames;
    private final String run = Long.toString(System.currentTimeMillis());

    void tick(Minecraft minecraft, TempestFxClient client) {
        if (!enabled || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        ticks++;
        if (ticks == 30) {
            minecraft.options.pauseOnLostFocus = false;
            minecraft.options.framerateLimit().set(Integer.getInteger("tempestfx.captureFps", 60));
            minecraft.player.connection.sendCommand("gamemode spectator");
            minecraft.player.connection.sendCommand("tp @s 152 90 173 0 -15");
            minecraft.player.connection.sendCommand("time set midnight");
            minecraft.player.connection.sendCommand("weather clear");
            client.config().general.debug = true;
        }
        if (ticks == 120 || ticks == 180 || ticks == 240) {
            burstFrames = 0;
            client.debugStrike(100, "auto", 12345L);
            TempestFx.log().info("QA strike run={} tick={} seed=12345", run, ticks);
        }
        if (ticks == 400) {
            TempestFx.log().info("QA capture complete run={}; stopping disposable test client", run);
            minecraft.stop();
        }
    }

    void frame(Minecraft minecraft, String compositorStatus) {
        if (!enabled || ticks < 119 || ticks > 250) return;
        boolean burst = (ticks >= 120 && ticks <= 125) || (ticks >= 180 && ticks <= 185)
            || (ticks >= 240 && ticks <= 245);
        if (!burst && (ticks == capturedTick || ticks != 119)) return;
        if (burst && burstFrames >= 36) return;
        capturedTick = ticks;
        int frame = burst ? burstFrames++ : 0;
        Screenshot.grab(minecraft.gameDirectory, "qa-" + run + "-" + ticks + "-" + frame + ".png",
            minecraft.getMainRenderTarget(), result -> TempestFx.log().info("QA screenshot: {}", result.getString()));
        TempestFx.log().info("QA frame tick={} compositor={} fps={}", ticks, compositorStatus, minecraft.getFps());
    }
}
