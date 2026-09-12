package dev.tempestfx.storm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiConsumer;

/** Bounded deduplication and late-packet handling, driven by synchronized world time. */
public final class StormInbox {
    private final LinkedHashMap<Long, Boolean> seen = new LinkedHashMap<>();
    private final List<StormEvent> pending = new ArrayList<>();
    public boolean accept(StormEvent event, String dimension, long now) {
        if (event.hello() || !event.dimension().equals(dimension) || event.startTick() < now - 160 || event.startTick() > now + 40 || seen.containsKey(event.id())) return false;
        seen.put(event.id(), true);
        while (seen.size() > 512) seen.remove(seen.keySet().iterator().next());
        if (pending.size() == 128) pending.removeFirst();
        pending.add(event); return true;
    }
    public void tick(long now, BiConsumer<StormEvent, Integer> consumer) {
        for (int i = 0; i < pending.size();) {
            var event = pending.get(i);
            if (event.startTick() > now) { i++; continue; }
            pending.remove(i);
            long age = now - event.startTick();
            if (age <= 160) consumer.accept(event, (int) age);
        }
    }
    public int pendingCount() { return pending.size(); }
    public void clear() { pending.clear(); seen.clear(); }
}
