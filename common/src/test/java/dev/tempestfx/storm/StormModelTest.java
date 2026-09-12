package dev.tempestfx.storm;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StormModelTest {
    static StormEvent event(long id, long start) { return new StormEvent(id, id, start, "minecraft:overworld", 2, new Vec3d(0, 192, 0), new Vec3d(80, 180, 0), 1, 3); }
    @Test void deterministicCellsDischargeAndDecayWithoutGroundOptIn() {
        var a = new StormCell(42, 0, new Vec3d(0,192,0)); var b = new StormCell(42,0,new Vec3d(0,192,0));
        int events = 0;
        for (int t = 0; t < 4000; t++) {
            var x = a.tick(t, 1, false, .12); var y = b.tick(t, 1, false, .12);
            assertEquals(x, y);
            if (x != null) { events++; assertFalse(x.kind().contactsGround()); }
        }
        assertTrue(events > 5 && events < 30); assertTrue(a.expired(4000));
    }
    @Test void inboxDeduplicatesRejectsWrongWorldAndPreservesElapsedAge() {
        var inbox = new StormInbox(); var ages = new ArrayList<Integer>();
        assertTrue(inbox.accept(event(1, 105), "minecraft:overworld", 100));
        assertFalse(inbox.accept(event(1, 105), "minecraft:overworld", 100));
        assertFalse(inbox.accept(event(2, 105), "minecraft:the_nether", 100));
        inbox.tick(104, (e,age)->fail());
        inbox.tick(108, (e,age)->ages.add(age));
        assertEquals(java.util.List.of(3), ages);
        assertFalse(inbox.accept(event(3, -100), "minecraft:overworld", 100));
        for(int i=0;i<1000;i++) inbox.accept(event(10+i,105),"minecraft:overworld",100);
        assertTrue(inbox.pendingCount()<=128); inbox.clear(); assertEquals(0,inbox.pendingCount());
    }
    @Test void malformedNetworkParametersAreRejected() {
        assertThrows(IllegalArgumentException.class,()-> new StormEvent(1,1,0,"../bad",2,Vec3d.ZERO,new Vec3d(1,1,1),1,3));
        assertThrows(IllegalArgumentException.class,()-> new StormEvent(1,1,0,"minecraft:overworld",99,Vec3d.ZERO,new Vec3d(1,1,1),1,3));
    }
}
