package dev.tempestfx.math;

public record Bounds3d(Vec3d min, Vec3d max) {
    public static Bounds3d empty() {
        return new Bounds3d(new Vec3d(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
            new Vec3d(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY));
    }

    public Bounds3d include(Vec3d point) {
        return new Bounds3d(
            new Vec3d(Math.min(min.x(), point.x()), Math.min(min.y(), point.y()), Math.min(min.z(), point.z())),
            new Vec3d(Math.max(max.x(), point.x()), Math.max(max.y(), point.y()), Math.max(max.z(), point.z())));
    }

    public boolean contains(Vec3d point) {
        return point.x() >= min.x() && point.x() <= max.x()
            && point.y() >= min.y() && point.y() <= max.y()
            && point.z() >= min.z() && point.z() <= max.z();
    }
}
