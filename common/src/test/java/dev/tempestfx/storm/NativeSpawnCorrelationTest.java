package dev.tempestfx.storm;
import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativeSpawnCorrelationTest {
    @Test void apiEventsWithSameSeedHaveIndependentIdentity() {
        var inbox=new StormInbox(); var nativeIds=new NativeSpawnCorrelation();
        for(int id=1;id<=2;id++) {
            var event=new StormEvent(id,42,100,"minecraft:overworld",2,new Vec3d(0,192,0),new Vec3d(80,180,0),1,3);
            assertFalse(nativeIds.resolved(event.nativeEntityId(),100));
            assertTrue(inbox.accept(event,event.dimension(),100));
            nativeIds.claim(event.nativeEntityId(),100);
        }
        assertEquals(2,inbox.pendingCount());
    }
    @Test void eitherPacketOrEntityCanWinButOnlyOnceAndRecordsExpire() {
        var ids=new NativeSpawnCorrelation();
        assertTrue(ids.claim(7,100)); assertFalse(ids.claim(7,101));
        assertTrue(ids.claim(8,100)); assertFalse(ids.claim(8,102));
        assertTrue(ids.claim(7,301));
        ids.clear(); assertTrue(ids.claim(7,302));
    }
}
