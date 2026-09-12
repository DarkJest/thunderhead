package dev.tempestfx.audio;

import dev.tempestfx.lightning.LightningGeometry;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/** Bounded sampling along the actual trunk. Independent of rendered brightness and frame rate. */
public final class ChannelAcoustics {
    public record Source(Vec3d position, float weight) {}
    private ChannelAcoustics() {}
    public static List<Source> sources(LightningGeometry geometry) {
        if (geometry.branches().isEmpty()) return List.of();
        var segments = geometry.branches().getFirst().segments();
        double length = segments.stream().mapToDouble(s -> s.length()).sum();
        if (length <= 0) return List.of();
        int count = Math.min(8, Math.max(2, (int) Math.ceil(length / 24)));
        List<Source> result = new ArrayList<>(count);
        int index = 0; double before = 0;
        for (int i = 0; i < count; i++) {
            double at = length * (i + .5) / count;
            while (index < segments.size() - 1 && before + segments.get(index).length() < at) {
                before += segments.get(index++).length();
            }
            var segment = segments.get(index);
            result.add(new Source(segment.start().lerp(segment.end(), (at - before) / segment.length()), 1f / count));
        }
        return List.copyOf(result);
    }
}
