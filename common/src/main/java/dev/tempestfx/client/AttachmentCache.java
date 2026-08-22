package dev.tempestfx.client;

import dev.tempestfx.strike.StrikeAttachment;
import java.util.ArrayList;
import java.util.List;

/**
 * Carries an attachment from the moment a strike is published to the moment it is drawn.
 *
 * <p>It cannot ride on the event: {@code LightningStrikeFxEvent} is public API and an attachment is
 * an internal rendering decision, and it cannot be a single field either, because the event bus may
 * hold a strike raised off the client thread until the next tick and two can be in flight at once.
 *
 * <p>So: a handful of entries keyed by strike seed, oldest evicted first. Bounded by construction,
 * with nothing to clean up on a timer — an entry that is never claimed is simply pushed out by the
 * next few strikes.
 */
final class AttachmentCache {
    /** Deep enough for a multi-stroke flash and a burst of debug strikes at once. */
    private static final int CAPACITY = 12;

    private final List<Entry> entries = new ArrayList<>(CAPACITY);

    void put(long seed, StrikeAttachment attachment) {
        while (entries.size() >= CAPACITY) entries.removeFirst();
        entries.add(new Entry(seed, attachment));
    }

    /** @return the attachment for this strike, or {@code null} if it had none */
    StrikeAttachment get(long seed) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            if (entries.get(index).seed == seed) return entries.get(index).attachment;
        }
        return null;
    }

    void clear() { entries.clear(); }

    private record Entry(long seed, StrikeAttachment attachment) {}
}
