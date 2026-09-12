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
    private record Arrival(LightningStrikeFxEvent event, int age, int serverReturns) {}
    private final ArrayDeque<Arrival> arrivals = new ArrayDeque<>();
    private final FlashEventSystem contacts = new FlashEventSystem();
    private final EffectManager effects;
    private int accessibleCooldown;

    public FlashSimulation(EffectManager effects) { this.effects = effects; }

    public void enqueue(LightningStrikeFxEvent event) {
        enqueue(event, 0, -1);
    }
    public void enqueue(LightningStrikeFxEvent event, int age, int serverReturns) {
        if (arrivals.size() == MAX_ARRIVALS) arrivals.removeFirst();
        arrivals.addLast(new Arrival(event, Math.max(0, age), serverReturns));
    }

    public void tick(Vec3d camera, TempestConfig config, Consumer<LightningStrikeFxEvent> contact,
                     Consumer<LightningStrikeFxEvent> accepted) {
        effects.tick();
        contacts.tick(contact);
        if (accessibleCooldown > 0) accessibleCooldown--;
        // Callbacks can enqueue more work; never drain recursively within the same tick.
        int count = arrivals.size();
        for (int i = 0; i < count; i++) {
            var arrival = arrivals.removeFirst();
            var event = arrival.event();
            if (!config.general.enabled) continue;
            int returns = arrival.serverReturns() < 0 ? config.effectiveReturnStrokes()
                : config.general.reducedFlashing ? 0 : Math.min(arrival.serverReturns(), config.lightning.returnStrokes);
            var timeline = FlashTimeline.plan(event.seed(), event.primary() ? returns : 0,
                arrival.serverReturns() >= 0 || config.realistic(), event.kind());
            boolean show = !config.general.reducedFlashing || accessibleCooldown == 0;
            effects.onFlash(event, camera, config, timeline, arrival.age(), show);
            if (show) {
                contacts.add(event, timeline, arrival.age());
                if (config.general.reducedFlashing) accessibleCooldown = 20;
            }
            accepted.accept(event);
        }
    }

    public void clear() { arrivals.clear(); contacts.clear(); effects.clear(); accessibleCooldown = 0; }
}
