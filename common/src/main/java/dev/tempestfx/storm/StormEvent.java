package dev.tempestfx.storm;

import dev.tempestfx.api.LightningKind;
import dev.tempestfx.math.Vec3d;

/** Immutable wire contract; zero-energy HELLO carries no lightning and only announces protocol support. */
public record StormEvent(long id, long seed, long startTick, String dimension, int kind,
                         Vec3d origin, Vec3d target, float intensity, int returns, int nativeEntityId) {
    public static final int VERSION = 2;
    public StormEvent(long id, long seed, long startTick, String dimension, int kind, Vec3d origin, Vec3d target, float intensity, int returns) {
        this(id, seed, startTick, dimension, kind, origin, target, intensity, returns, -1);
    }
    public StormEvent {
        if (nativeEntityId < -1) throw new IllegalArgumentException("Invalid native entity correlation");
        if (dimension == null || dimension.length() > 128 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid dimension");
        if (kind < -1 || kind >= LightningKind.values().length || returns < 0 || returns > 4
            || !Float.isFinite(intensity) || intensity < 0 || intensity > 4 || (kind >= 0 && intensity == 0))
            throw new IllegalArgumentException("Invalid storm parameters");
        if (origin == null || target == null || !valid(origin) || !valid(target)) throw new IllegalArgumentException("Invalid storm position");
        if (kind >= 0 && origin.distanceTo(target) < .01) throw new IllegalArgumentException("Empty channel");
    }
    public static boolean valid(Vec3d p) { return p.finite() && Math.abs(p.x()) <= 30_000_000 && Math.abs(p.z()) <= 30_000_000 && Math.abs(p.y()) <= 2048; }
    public boolean hello() { return kind == -1; }
    public LightningKind lightningKind() { return LightningKind.values()[kind]; }
}
