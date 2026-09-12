package dev.tempestfx.render.composite;

import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.effect.ActiveLightningEffect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** View-space, bounded surface illumination from the same flash exposure used by the channel. */
public record SceneLightField(List<Light> lights, Matrix4f projection, Matrix4f inverseProjection, float radius) {
    public static final SceneLightField NONE = new SceneLightField(List.of(), new Matrix4f(), new Matrix4f(), 0);
    public record Light(float x, float y, float z, float power) {}

    public SceneLightField {
        lights = List.copyOf(lights);
        if (!Float.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Invalid light radius");
        if (lights.size() > 4) throw new IllegalArgumentException("At most four surface light samples");
        projection = new Matrix4f(projection);
        inverseProjection = new Matrix4f(inverseProjection);
    }

    public boolean active() { return !lights.isEmpty(); }

    public static SceneLightField from(List<ActiveLightningEffect> effects, Matrix4f view, Matrix4f projection,
                                      float partialTick, TempestConfig config) {
        if (!config.lighting.dynamicLighting || !config.lighting.surfaceLighting || config.lighting.illuminationRadius <= 0) return NONE;
        List<Light> candidates = new ArrayList<>();
        for (var effect : effects) {
            float power = effect.brightness(partialTick, false, config.general.reducedFlashing)
                * config.lighting.illuminationStrength;
            if (config.general.reducedFlashing) power *= .15f;
            if (power < .002f) continue;
            var trunk = effect.geometry().branches().getFirst().segments();
            // Samples distributed along the channel, not all concentrated at the impact.
            for (int index = 0; index < 3; index++) {
                var segment = trunk.get(Math.min(trunk.size() - 1, trunk.size() * (index * 4 + 1) / 10));
                var world = segment.start().lerp(segment.end(), .5);
                var position = view.transformPosition(new Vector3f((float) world.x(), (float) world.y(), (float) world.z()));
                candidates.add(new Light(position.x, position.y, position.z, power / 3));
            }
        }
        candidates.sort(Comparator.comparingDouble(light ->
            -(double) light.power() / (64 + light.x() * light.x() + light.y() * light.y() + light.z() * light.z())));
        if (candidates.isEmpty()) return NONE;
        return new SceneLightField(candidates.subList(0, Math.min(4, candidates.size())), projection,
            new Matrix4f(projection).invert(), config.lighting.illuminationRadius);
    }
}
