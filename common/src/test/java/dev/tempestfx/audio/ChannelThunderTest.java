package dev.tempestfx.audio;
import dev.tempestfx.api.*;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.*;
import dev.tempestfx.math.*;
import dev.tempestfx.platform.ClientPlatform;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChannelThunderTest {
    @Test void lateSinglePulseKeepsFutureLegacyThunderExactlyOnce() {
        var ear=new Ear();
        var system=new ThunderSystem(ear,(distance,seed,intensity)->List.of(new ThunderLayer(ThunderProfile.CLOSE_CRACK,0,1,1)));
        var cfg=new TempestConfig();cfg.audio.channelThunder=false;
        system.onPastContacts(event(),FlashTimeline.plan(42,0,true),2,ear.at,cfg);
        for(int i=0;i<18;i++)system.tick(cfg);
        assertEquals(0,ear.plays);
        for(int i=0;i<20;i++)system.tick(cfg);
        assertEquals(1,ear.plays);
    }
    static class Ear implements ClientPlatform {
        Vec3d at = new Vec3d(343, 0, 0); int plays; List<Vec3d> positions = new ArrayList<>();
        public Path configDirectory() { return Path.of("."); }
        public Vec3d cameraPosition() { return at; }
        public void playThunder(ThunderProfile p, Vec3d v, float volume, float pitch) { plays++; positions.add(v); }
    }
    static LightningGeometry geometry() {
        return new MidpointDisplacementStrategy().generate(new LightningBolt(new Vec3d(0, 120, 0), Vec3d.ZERO, 42, 1, LightningGenerationConfig.high()));
    }
    static LightningStrikeFxEvent event() { return new LightningStrikeFxEvent(Vec3d.ZERO, 42, 1, LightningEnvironment.land(0, false)); }
    @Test void movingListenerChangesWaveArrivalAndSourcesAreSpatiallyDistributed() {
        var ear = new Ear(); var system = new ThunderSystem(ear); var cfg = new TempestConfig();
        var timeline = FlashTimeline.plan(42, 0, true);
        system.onChannelFlash(event(), geometry(), timeline, cfg);
        for (int i = 0; i < 10; i++) system.tick(cfg);
        assertEquals(0, ear.plays);
        ear.at = Vec3d.ZERO;
        system.tick(cfg);
        assertTrue(ear.plays > 0, "nearby wave should already have reached the moved listener");
        for (int i = 0; i < 20; i++) system.tick(cfg);
        assertTrue(new HashSet<>(ear.positions).size() > 1);
    }
    @Test void latePacketsDoNotReplayPastThunderAndMixedQueuesStayBounded() {
        var ear = new Ear(); var system = new ThunderSystem(ear); var cfg = new TempestConfig();
        system.onChannelFlash(event(), geometry(), FlashTimeline.plan(42, 0, true), cfg, 100);
        assertEquals(0, system.pendingCount());
        for (int i = 0; i < 100; i++) {
            system.onChannelFlash(event(), geometry(), FlashTimeline.plan(42, 4, true), cfg);
            system.onStrike(event(), ear.at, cfg);
        }
        assertTrue(system.pendingCount() <= 192);
        system.clear(); assertEquals(0, system.pendingCount());
    }
}
