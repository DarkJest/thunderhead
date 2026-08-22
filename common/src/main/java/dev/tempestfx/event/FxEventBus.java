package dev.tempestfx.event;

import dev.tempestfx.TempestFx;
import dev.tempestfx.api.LightningStrikeFxEvent;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fan-out for strike events.
 *
 * <p>Subsystems mutate render and simulation state, so listeners are only ever invoked from the
 * client thread. {@link #publish} may be called from any thread: off-thread events are parked in a
 * queue and delivered by {@link #drain()} at the start of the next client tick. A failing listener
 * is isolated so one broken subsystem cannot silence the rest of the storm.
 */
public final class FxEventBus {
    /** Bound on the cross-thread queue; a runaway producer drops events instead of exhausting memory. */
    private static final int MAX_QUEUED = 256;

    private final List<Consumer<LightningStrikeFxEvent>> strikeListeners = new CopyOnWriteArrayList<>();
    private final Queue<LightningStrikeFxEvent> pending = new ConcurrentLinkedQueue<>();
    private volatile Thread clientThread;

    /** Records the thread that owns simulation state; events raised elsewhere are deferred to it. */
    public void bindToCurrentThread() { clientThread = Thread.currentThread(); }

    public AutoCloseable subscribeStrike(Consumer<LightningStrikeFxEvent> listener) {
        strikeListeners.add(listener);
        return () -> strikeListeners.remove(listener);
    }

    public void publish(LightningStrikeFxEvent event) {
        Thread owner = clientThread;
        if (owner == null || owner == Thread.currentThread()) {
            dispatch(event);
        } else if (pending.size() < MAX_QUEUED) {
            pending.add(event);
        }
    }

    /** Delivers events published from other threads. Call once per client tick. */
    public void drain() {
        LightningStrikeFxEvent event;
        while ((event = pending.poll()) != null) dispatch(event);
    }

    public void clearPending() { pending.clear(); }

    private void dispatch(LightningStrikeFxEvent event) {
        for (Consumer<LightningStrikeFxEvent> listener : strikeListeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException failure) {
                TempestFx.log().error("Strike listener failed", failure);
            }
        }
    }
}
