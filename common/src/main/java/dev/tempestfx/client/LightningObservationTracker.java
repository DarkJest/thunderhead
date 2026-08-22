package dev.tempestfx.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Guards against handling the same bolt twice.
 */
public final class LightningObservationTracker {
    private static final int RETENTION_TICKS = 80;
    /** Bound so a hostile or broken server cannot grow the map without limit. */
    private static final int MAX_TRACKED = 512;

    private final Map<Integer, Integer> seen = new HashMap<>();

    public boolean firstObservation(int entityId) {
        if (seen.size() >= MAX_TRACKED) seen.clear();
        return seen.putIfAbsent(entityId, 0) == null;
    }

    public void tick() {
        seen.replaceAll((id, age) -> age + 1);
        seen.entrySet().removeIf(entry -> entry.getValue() > RETENTION_TICKS);
    }

    public void clear() { seen.clear(); }
}
