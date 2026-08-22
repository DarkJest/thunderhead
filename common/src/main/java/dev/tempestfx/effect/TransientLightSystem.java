package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Purely visual illumination around an impact.
 *
 * <p>No block light is written and no chunk is relit: the effect is an additive light pool drawn on
 * the surface, plus the sky-flash extension owned by {@link WorldFlashSystem}. Both disappear on
 * their own and leave the world exactly as they found it.
 */
public final class TransientLightSystem {
    private static final int LIFETIME_TICKS = 7;
    private static final int MAX_LIGHTS = 24;

    private final List<TransientPointLight> lights = new ArrayList<>();
    private final List<TransientPointLight> view = Collections.unmodifiableList(lights);

    public void onStrike(LightningStrikeFxEvent event, TempestConfig config) {
        if (!config.lighting.dynamicLighting || config.lighting.illuminationStrength <= 0) return;
        if (config.lighting.illuminationRadius <= 0) return;
        if (lights.size() >= MAX_LIGHTS) lights.removeFirst();
        double surfaceY = event.environment().surfaceY(event.position().y());
        Vec3d anchor = new Vec3d(event.position().x(), surfaceY, event.position().z());
        lights.add(new TransientPointLight(anchor, config.lighting.illuminationRadius,
            config.lighting.illuminationStrength * event.intensity()));
    }

    public void tick() {
        for (int index = lights.size() - 1; index >= 0; index--) {
            TransientPointLight next = lights.get(index).next();
            if (next.expired()) lights.remove(index); else lights.set(index, next);
        }
    }

    public List<TransientPointLight> lights() { return view; }

    public void clear() { lights.clear(); }

    public int activeCount() { return lights.size(); }

    /**
     * One decaying light pool.
     *
     * @param position     surface anchor
     * @param maximumRadius radius at full expansion, in blocks
     * @param maximumIntensity peak strength
     * @param age          age in ticks
     */
    public record TransientPointLight(Vec3d position, float maximumRadius, float maximumIntensity, int age) {
        public TransientPointLight(Vec3d position, float radius, float intensity) {
            this(position, radius, intensity, 0);
        }

        public boolean expired() { return age >= LIFETIME_TICKS; }

        public TransientPointLight next() {
            return new TransientPointLight(position, maximumRadius, maximumIntensity, age + 1);
        }

        /** Timeline: full at 0 ms, 80% at 50 ms, 25% at 120 ms, gone by 350 ms. */
        public float intensity(float partialTick) {
            float time = age + FxMath.clamp(partialTick, 0, 1);
            if (time >= LIFETIME_TICKS) return 0;
            return maximumIntensity * (float) Math.exp(-time * 0.85);
        }

        public float radius(float partialTick) {
            float time = age + FxMath.clamp(partialTick, 0, 1);
            return maximumRadius * (0.55f + 0.45f * (float) FxMath.clamp(time / 3.0, 0, 1));
        }
    }
}
