package dev.tempestfx.api;

import dev.tempestfx.TempestFx;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Stable entry point for other mods: raise a strike, raise a rolling thunder event, watch for
 * strikes, ask whether the mod is running.
 *
 * <p>Everything in {@code dev.tempestfx.api} keeps its shape within a major version. Everything
 * outside it, including {@link Internal}, is implementation detail and may change in any release.
 */
public final class TempestFxApi {
    private static final AtomicReference<Consumer<LightningStrikeFxEvent>> DISPATCHER = new AtomicReference<>();
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    /**
     * Listeners survive the client being torn down and rebuilt.
     */
    private static final List<Consumer<LightningStrikeFxEvent>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicReference<Consumer<ThunderRoll>> ROLLS = new AtomicReference<>();

    private TempestFxApi() {}

    /**
     * Draws a strike at a position of your choosing.
     *
     * @return {@code true} when the effect was accepted for processing
     */
    public static boolean triggerLightning(LightningEffect effect) {
        Objects.requireNonNull(effect, "effect");
        Consumer<LightningStrikeFxEvent> dispatcher = DISPATCHER.get();
        if (dispatcher == null) {
            if (WARNED.compareAndSet(false, true)) {
                TempestFx.log().warn("triggerLightning called before the client started; ignoring.");
            }
            return false;
        }
        dispatcher.accept(new LightningStrikeFxEvent(effect.position(), effect.seed(), effect.intensity(),
            effect.environment(), effect.target(), 0, effect.options()));
        return true;
    }

    /**
     * Called for every strike Thunderhead draws - vanilla bolts, return strokes and effects raised
     * through this API alike.
     */
    public static AutoCloseable onStrike(Consumer<LightningStrikeFxEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    /**
     * Starts a rolling thunder event on its own, with no lightning in front of it.
     *
     * @return {@code true} when the event was started
     */
    public static boolean triggerThunderRoll(ThunderRoll roll) {
        Objects.requireNonNull(roll, "roll");
        Consumer<ThunderRoll> starter = ROLLS.get();
        if (starter == null) return false;
        starter.accept(roll);
        return true;
    }

    /** Whether the client is up and {@link #triggerLightning} will do anything. */
    public static boolean isAvailable() { return DISPATCHER.get() != null; }

    /**
     * Wiring used by Thunderhead's own bootstrap. Not part of the API.
     */
    public static final class Internal {
        private Internal() {}

        public static void install(Consumer<LightningStrikeFxEvent> dispatcher, Consumer<ThunderRoll> rolls) {
            DISPATCHER.set(Objects.requireNonNull(dispatcher, "dispatcher"));
            ROLLS.set(Objects.requireNonNull(rolls, "rolls"));
        }

        /** Detaches the API when the client shuts down, so a stale dispatcher cannot outlive the game. */
        public static void uninstall() {
            DISPATCHER.set(null);
            ROLLS.set(null);
        }

        /** Fans one strike out to the registered integrations, isolating each from the others. */
        public static void fireStrike(LightningStrikeFxEvent event) {
            for (Consumer<LightningStrikeFxEvent> listener : LISTENERS) {
                try {
                    listener.accept(event);
                } catch (RuntimeException failure) {
                    TempestFx.log().error("An external strike listener failed", failure);
                }
            }
        }
    }
}
