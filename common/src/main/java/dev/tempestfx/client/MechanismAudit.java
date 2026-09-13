package dev.tempestfx.client;

import dev.tempestfx.TempestFx;
import dev.tempestfx.api.LightningKind;
import dev.tempestfx.config.RealismProfile;
import dev.tempestfx.config.TempestConfig;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.sounds.SoundSource;

/** Explicit dev-only, isolated feature audit; screenshots are taken after HUD rendering. */
final class MechanismAudit {
    private static final List<String> CASES = List.of("realistic_land", "realistic_water", "positive_ground", "intracloud", "intercloud",
        "cinematic_land", "cinematic_water", "screen_flash", "camera_impulse", "camera_realistic", "sound_delay", "entity_discharge", "ash_imprint",
        "ball_realistic", "ball_cinematic", "roll_realistic", "roll_cinematic", "reduced_flashing", "sky_flash", "surface_light", "fallback_programs", "disabled",
        "imprint_particles_off", "ball_particles_off", "settings_realistic", "settings_cinematic");
    private final boolean enabled = "mechanisms".equals(System.getProperty("tempestfx.captureScene"));
    private final String run = Long.toString(System.currentTimeMillis());
    private final List<Map<String, Object>> results = new ArrayList<>();
    private final Map<String, Double> maximum = new LinkedHashMap<>();
    private int ticks, phase = -1, local, lastCapture = -1;
    private String name = "setup";
    private boolean earlySound;
    private int menuFrames;
    private long pausedTick = -1;
    boolean enabled() { return enabled; }

    void tick(Minecraft mc, TempestFxClient client) {
        if (!enabled || mc.player == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        if (ticks < 40) return;
        int next = (ticks - 40) / 120;
        local = (ticks - 40) % 120;
        if (next >= CASES.size()) {
            writeReport(mc);
            TempestFx.log().info("MECHANISM AUDIT COMPLETE run={} failures={}", run, results.stream().filter(r -> !(Boolean) r.get("passed")).count());
            mc.stop();
            return;
        }
        if (phase != next) { phase = next; name = CASES.get(phase); setup(mc, client); }
        if (local == 20) trigger(mc, client);
        if (local == 25) mc.gui.getChat().clearMessages(true);
        if (local >= 20) {
            client.mechanismCounters().forEach((key, value) -> maximum.merge(key, value, Math::max));
            if (name.equals("sound_delay") && local < 35 && client.mechanismCounters().get("voices") > 0) earlySound = true;
        }
        if (local == 110) {
            boolean passed = passed();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("case", name); row.put("passed", passed); row.put("max", new LinkedHashMap<>(maximum)); row.put("earlySound", earlySound);
            results.add(row);
            TempestFx.log().info("MECHANISM {} passed={} max={} earlySound={}", name, passed, maximum, earlySound);
        }
    }

    private void setup(Minecraft mc, TempestFxClient client) {
        maximum.clear(); earlySound = false; menuFrames = 0; pausedTick = -1;
        var c = client.config(); var fresh = new TempestConfig();
        c.general = fresh.general; c.lightning = fresh.lightning; c.impact = fresh.impact; c.lighting = fresh.lighting;
        c.camera = fresh.camera; c.audio = fresh.audio; c.performance = fresh.performance; c.compatibility = fresh.compatibility;
        boolean cinematic = name.startsWith("cinematic") || name.endsWith("cinematic")
            || Set.of("screen_flash", "camera_impulse", "entity_discharge", "ash_imprint", "imprint_particles_off").contains(name);
        c.general.profile = cinematic ? RealismProfile.CINEMATIC : RealismProfile.REALISTIC;
        c.camera.screenFlash = false; c.camera.cameraImpulse = false; c.lighting.worldFlash = false; c.audio.giantRoll = false;
        c.impact.entityDischarge = name.equals("entity_discharge");
        c.impact.ashImprint = name.equals("ash_imprint") || name.equals("imprint_particles_off");
        if (name.equals("screen_flash")) c.camera.screenFlash = true;
        if (name.startsWith("camera_")) { c.camera.cameraImpulse = true; c.camera.impulseStrength = 1; }
        if (name.equals("reduced_flashing")) { c.general.reducedFlashing = true; c.camera.screenFlash = true; c.camera.cameraImpulse = true; c.lighting.worldFlash = true; }
        if (name.equals("sky_flash")) { c.lighting.worldFlash = true; c.lighting.surfaceLighting = false; c.lighting.dynamicLighting = false; }
        if (name.equals("surface_light")) { c.lighting.illuminationRadius = 64; c.lighting.illuminationStrength = 3; }
        if (Set.of("entity_discharge", "ash_imprint", "screen_flash", "camera_impulse").contains(name)) {
            c.impact.shockwave = false; c.lighting.dynamicLighting = false;
            c.impact.sparks = false; c.impact.smoke = false; c.impact.debris = false;
            c.impact.ash = name.equals("ash_imprint");
        }
        if (name.equals("fallback_programs")) c.compatibility.customShaders = false;
        if (name.endsWith("particles_off")) { c.impact.sparks=false; c.impact.smoke=false; c.impact.debris=false; c.impact.ash=false; c.impact.shockwave=false; }
        if (name.equals("disabled")) c.general.enabled = false;
        c.validate(); client.resetAuditState();
        mc.options.pauseOnLostFocus = false; mc.options.hideGui = false; mc.options.framerateLimit().set(60);
        mc.getSoundManager().stop(null, SoundSource.WEATHER);
        command(mc, "gamemode spectator"); command(mc, "weather clear"); command(mc, "gamerule doFireTick false");
        command(mc, "kill @e[tag=mechanism_audit]"); command(mc, "kill @e[type=tempestfx:ball_lightning]");
        command(mc, "fill 136 70 184 168 70 224 minecraft:stone");
        command(mc, "fill 136 71 184 168 90 224 minecraft:air");
        command(mc, "fill 144 71 206 160 79 206 minecraft:stone");
        if (name.contains("water")) command(mc, "fill 144 70 188 160 70 204 minecraft:water");
        String view = name.contains("cloud") ? "152 100 173 0 -35" : name.startsWith("roll") ? "152 90 173 0 -15"
            : name.equals("ash_imprint") || name.equals("imprint_particles_off") ? "152 71 193 0 80"
            : name.startsWith("camera") || name.startsWith("ball") || name.equals("entity_discharge") ? "152 73 191 0 10"
            : name.equals("sound_delay") ? "152 76 100 0 -10" : "152 76 173 0 10";
        command(mc, "tp @s " + view);
        command(mc, name.equals("ash_imprint") ? "time set noon" : "time set midnight");
        TempestFx.log().info("MECHANISM BEGIN run={} case={}", run, name);
    }

    private void trigger(Minecraft mc, TempestFxClient client) {
        mc.gui.getChat().clearMessages(true);
        switch (name) {
            case "roll_realistic", "roll_cinematic" -> client.debugThunderRoll(4, 30);
            case "ball_realistic", "ball_cinematic", "ball_particles_off" -> client.summonBallLightning();
            case "ash_imprint", "imprint_particles_off" -> client.debugDirectHit();
            case "settings_realistic", "settings_cinematic" -> client.openSettings(null);
            case "entity_discharge" -> {
                command(mc, "summon minecraft:cow 152 74 197 {Tags:[\"mechanism_audit\"],Motion:[0.0d,-0.1d,0.0d]}");
                client.debugStrikeAt(152, 71, 197, 12345L);
            }
            case "intracloud" -> client.debugTypedStrike(LightningKind.INTRACLOUD, 12345);
            case "intercloud" -> client.debugTypedStrike(LightningKind.INTERCLOUD, 12345);
            case "positive_ground" -> client.debugTypedStrike(LightningKind.POSITIVE_GROUND, 12345);
            case "fallback_programs", "disabled" -> command(mc, "summon minecraft:lightning_bolt 152 71 197");
            case "reduced_flashing" -> { client.stress(20); client.debugThunderRoll(4,30); command(mc, "summon minecraft:lightning_bolt 152 71 197"); }
            case "sound_delay" -> client.debugStrikeAt(152,71,443,12345L);
            default -> client.debugStrike(name.startsWith("camera") ? 6 : 24, name.contains("water") ? "water" : "land", 12345L);
        }
    }

    void frame(Minecraft mc, TempestFxClient client) {
        if (enabled && name.startsWith("settings_") && mc.screen instanceof TempestOptionsScreen settings) {
            if (mc.isPaused()) {
                if (pausedTick < 0) pausedTick = client.auditSimulationTicks();
                else if (pausedTick != client.auditSimulationTicks()) maximum.put("advancedWhilePaused", 1.0);
                maximum.put("pauseObserved", 1.0);
            }
            if (++menuFrames == 3) {
                Screenshot.grab(mc.gameDirectory, "mechanism-"+run+"-"+name+".png", mc.getMainRenderTarget(), result -> {});
                maximum.put("settings", 1.0); maximum.put("unavailable", (double) settings.unavailableCount());
            }
            if (menuFrames >= 8) mc.setScreen(null);
            return;
        }
        if (!enabled || phase < 0 || phase >= CASES.size() || ticks == lastCapture || mc.level == null) return;
        if (local != 23 && local != 28 && local != 40 && local != 70) return;
        lastCapture = ticks;
        Screenshot.grab(mc.gameDirectory, "mechanism-"+run+"-"+name+"-"+local+".png", mc.getMainRenderTarget(), result -> {});
    }

    private boolean any(String key) { return maximum.getOrDefault(key, 0.0) > 0; }
    private boolean passed() {
        return switch (name) {
            case "roll_realistic", "roll_cinematic" -> any("sky") && any("voices");
            case "ball_realistic", "ball_cinematic" -> maximum.getOrDefault("sphereDraws", 0.0) == 1;
            case "ash_imprint" -> any("imprints");
            case "entity_discharge" -> any("discharges");
            case "screen_flash" -> any("screen");
            case "camera_impulse", "camera_realistic" -> any("camera");
            case "cinematic_land", "cinematic_water" -> any("shockwaves") && any("distortion") && (name.contains("water") ? any("WATER") : any("DEBRIS"));
            case "intracloud", "intercloud" -> any("bolts") && !any("shockwaves") && !any("DEBRIS");
            case "reduced_flashing" -> maximum.getOrDefault("bolts",0.0) <= 1 && !any("sky") && !any("skyFlash") && !any("camera");
            case "fallback_programs", "disabled" -> any("vanilla");
            case "surface_light" -> any("surfaceLight");
            case "sky_flash" -> any("skyFlash");
            case "sound_delay" -> !earlySound && any("voices");
            case "imprint_particles_off" -> any("imprints") && !any("SMOKE") && !any("ASH") && !any("EMBER");
            case "ball_particles_off" -> maximum.getOrDefault("sphereDraws",0.0) == 1 && !any("SPARK") && !any("MICRO_ARC") && !any("EMBER");
            case "settings_realistic", "settings_cinematic" -> any("settings") && any("unavailable") && any("pauseObserved") && !any("advancedWhilePaused");
            case "realistic_water" -> any("bolts") && any("WATER");
            default -> any("bolts") && any("voices");
        };
    }
    private void writeReport(Minecraft mc) {
        try { java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("mechanism-audit-"+run+".json"),
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results)); }
        catch (java.io.IOException failure) { TempestFx.log().error("Could not save mechanism audit", failure); }
    }
    private static void command(Minecraft mc, String command) { mc.player.connection.sendCommand(command); }
}
