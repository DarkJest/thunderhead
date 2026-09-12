package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.lightning.FlashTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Releases contact effects on tick boundaries from the same plan the renderer samples continuously. */
public final class FlashEventSystem {
    private static final int MAX_PENDING = 256;
    private final List<Pending> pending = new ArrayList<>();

    public void add(LightningStrikeFxEvent event, FlashTimeline timeline) {
        for (FlashTimeline.Pulse pulse : timeline.pulses()) {
            if (pending.size() == MAX_PENDING) break;
            // Preserve origin, surface, API style and SILENT/particle filters for every impulse.
            var stroke = new LightningStrikeFxEvent(event.position(), event.seed(),
                event.intensity() * pulse.strength(), event.environment(), event.target(),
                event.stroke() + pulse.index(), event.options());
            pending.add(new Pending((int) Math.ceil(pulse.atTicks()), stroke));
        }
    }

    public void tick(Consumer<LightningStrikeFxEvent> consumer) {
        // Stable ordering matters when several pulses become due on the same tick.
        List<LightningStrikeFxEvent> due = new ArrayList<>();
        for (int i = 0; i < pending.size();) {
            Pending entry = pending.get(i);
            if (entry.ticks <= 1) { due.add(entry.event); pending.remove(i); }
            else { pending.set(i, new Pending(entry.ticks - 1, entry.event)); i++; }
        }
        due.forEach(consumer);
    }

    public void clear() { pending.clear(); }
    public int pendingCount() { return pending.size(); }
    private record Pending(int ticks, LightningStrikeFxEvent event) {}
}
