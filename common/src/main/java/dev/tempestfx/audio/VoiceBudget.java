package dev.tempestfx.audio;

import java.util.Arrays;

/**
 * Sliding-window cap on how many clips Thunderhead may start.
 */
public final class VoiceBudget {
    private final int[] startsPerTick;
    private final int limit;
    private int cursor;
    private int started;

    public VoiceBudget(int windowTicks, int limit) {
        this.startsPerTick = new int[Math.max(1, windowTicks)];
        this.limit = Math.max(1, limit);
    }

    /** @return {@code true} when a clip may start now, consuming one slot. */
    public boolean claim() {
        if (started >= limit) return false;
        startsPerTick[cursor]++;
        started++;
        return true;
    }

    /** Advances the window. Call once per client tick. */
    public void tick() {
        cursor = (cursor + 1) % startsPerTick.length;
        started -= startsPerTick[cursor];
        startsPerTick[cursor] = 0;
    }

    public int started() { return started; }

    public void clear() {
        Arrays.fill(startsPerTick, 0);
        started = 0;
        cursor = 0;
    }
}
