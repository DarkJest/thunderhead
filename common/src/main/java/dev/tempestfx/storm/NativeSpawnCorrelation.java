package dev.tempestfx.storm;

import java.util.LinkedHashMap;

/** Only native entity IDs correlate packet/entity representations; geometry seeds are never identities. */
public final class NativeSpawnCorrelation {
    private final LinkedHashMap<Integer, Long> resolved = new LinkedHashMap<>();
    public boolean resolved(int entityId, long now) {
        resolved.values().removeIf(until -> until < now);
        return entityId >= 0 && resolved.containsKey(entityId);
    }
    public boolean claim(int entityId, long now) {
        if (entityId < 0) return true;
        if (resolved(entityId, now)) return false;
        resolved.put(entityId, now + 200);
        while (resolved.size() > 512) resolved.remove(resolved.keySet().iterator().next());
        return true;
    }
    public void clear() { resolved.clear(); }
}
