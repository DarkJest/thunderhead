package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.FlashTimeline;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayDeque;
import java.util.function.Consumer;

/** Owns birth ordering: advance old flashes, then accept new ones at age zero. */
public final class FlashSimulation {
    private static final int MAX_ARRIVALS = 256;
    private final ArrayDeque<LightningStrikeFxEvent> arrivals = new ArrayDeque<>();
    private final FlashEventSystem contacts = new FlashEventSystem();
    private final EffectManager effects;

    public FlashSimulation(EffectManager effects) { this.effects = effects; }

    public void enqueue(LightningStrikeFxEvent event) {
        if (arrivals.size() == MAX_ARRIVALS) arrivals.removeFirst();
        arrivals.addLast(event);
    }

    public void tick(Vec3d camera, TempestConfig config, Consumer<LightningStrikeFxEvent> contact,
                     Consumer<LightningStrikeFxEvent> accepted) {
        effects.tick();
        contacts.tick(contact);
        // Callbacks can enqueue more work; never drain recursively within the same tick.
        int count = arrivals.size();
        for (int i = 0; i < count; i++) {
            var event = arrivals.removeFirst();
            if (!config.general.enabled) continue;
            var timeline = FlashTimeline.plan(event.seed(), event.primary() ? config.effectiveReturnStrokes() : 0,
                config.realistic(), event.kind());
            effects.onFlash(event, camera, config, timeline);
            contacts.add(event, timeline);
            accepted.accept(event);
        }
    }

    public void clear() { arrivals.clear(); contacts.clear(); effects.clear(); }
}
