package dev.tempestfx.client;

import dev.tempestfx.TempestFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in, finite integration run. Use only with a disposable singleplayer QA save. */
final class DevelopmentCapture {
    private final MechanismAudit audit = new MechanismAudit();
    void finalFrame(Minecraft minecraft, TempestFxClient client) { audit.frame(minecraft, client); }
    private final boolean enabled = Boolean.getBoolean("tempestfx.capture");
    private final boolean surfaces = "surface".equals(System.getProperty("tempestfx.captureScene"));
    private final boolean storm = "storm".equals(System.getProperty("tempestfx.captureScene"));
    private final boolean multiplayer = Boolean.getBoolean("tempestfx.multiplayerCapture");
    private final boolean stress = "stress".equals(System.getProperty("tempestfx.captureScene"));
    private int ticks;
    private int capturedTick = -1;
    private int burstFrames;
    private final String run = Long.toString(System.currentTimeMillis());

    void tick(Minecraft minecraft, TempestFxClient client) {
        if (audit.enabled()) { audit.tick(minecraft, client); return; }
        if (!enabled || minecraft.player == null || (!multiplayer && minecraft.getSingleplayerServer() == null)) return;
        ticks++;
        if (stress && ticks >= 120 && ticks < 2400 && ticks % 20 == 0) client.stress(20);
        if (ticks % 200 == 0) TempestFx.log().info("QA metrics tick={} {}", ticks, client.diagnostics());
        if (ticks == 30) {
            minecraft.options.pauseOnLostFocus = false;
            minecraft.options.framerateLimit().set(Integer.getInteger("tempestfx.captureFps", 60));
            minecraft.player.connection.sendCommand("gamemode spectator");
            minecraft.player.connection.sendCommand(surfaces ? "tp @s 152 76 173 0 10" : "tp @s 152 90 173 0 -15");
            minecraft.player.connection.sendCommand("time set midnight");
            if (!multiplayer) minecraft.player.connection.sendCommand(storm ? "weather thunder" : "weather clear");
            if (surfaces) {
                // Explicit opt-in disposable arena: ground plane and a wall for visible depth tests.
                minecraft.player.connection.sendCommand("fill 136 70 184 168 70 224 minecraft:stone");
                minecraft.player.connection.sendCommand("fill 144 71 206 160 79 206 minecraft:stone");
            }
            client.config().general.debug = true;
        }
        if (ticks == 120 || ticks == 180 || ticks == 240 || ticks == 300) {
            burstFrames = 0;
            var kind = dev.tempestfx.api.LightningKind.values()[(ticks - 120) / 60];
            if (multiplayer) { /* Receive server events only. */ }
            else if (storm) minecraft.player.connection.sendCommand("thunderstorm strike 152 71 197");
            else if (surfaces) client.debugStrike(24, "auto", 12345L);
            else client.debugTypedStrike(kind, 12345L);
            TempestFx.log().info("QA strike run={} tick={} seed=12345", run, ticks);
            if (surfaces && !client.config().compatibility.customShaders) {
                minecraft.player.connection.sendCommand("summon minecraft:lightning_bolt 152 71 197");
            }
        }
        if (ticks == (stress ? 2500 : multiplayer || storm ? 800 : 400)) {
            TempestFx.log().info("QA capture complete run={}; stopping disposable test client", run);
            // Disconnecting the integrated server from inside END_CLIENT_TICK can block its
            // shutdown handshake. Let Minecraft's normal stop path tear it down outside the tick.
            if (minecraft.getSingleplayerServer() == null) minecraft.disconnect();
            minecraft.stop();
        }
    }

    void frame(Minecraft minecraft, String compositorStatus) {
        if (audit.enabled()) return;
        if (!enabled || stress || ticks < 119 || ticks > 310) return;
        boolean burst = (ticks >= 120 && ticks <= 125) || (ticks >= 180 && ticks <= 185)
            || (ticks >= 240 && ticks <= 245) || (ticks >= 300 && ticks <= 305);
        if (!burst && (ticks == capturedTick || ticks != 119)) return;
        if (burst && burstFrames >= 36) return;
        capturedTick = ticks;
        int frame = burst ? burstFrames++ : 0;
        Screenshot.grab(minecraft.gameDirectory, "qa-" + run + "-" + ticks + "-" + frame + ".png",
            minecraft.getMainRenderTarget(), result -> TempestFx.log().info("QA screenshot: {}", result.getString()));
        TempestFx.log().info("QA frame tick={} compositor={} fps={}", ticks, compositorStatus, minecraft.getFps());
    }
}
