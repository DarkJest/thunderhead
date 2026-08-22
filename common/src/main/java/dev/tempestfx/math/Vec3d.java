package dev.tempestfx.math;

public record Vec3d(double x, double y, double z) {
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);
    public static final Vec3d UP = new Vec3d(0, 1, 0);

    public Vec3d add(Vec3d other) { return new Vec3d(x + other.x, y + other.y, z + other.z); }
    public Vec3d add(double dx, double dy, double dz) { return new Vec3d(x + dx, y + dy, z + dz); }
    public Vec3d subtract(Vec3d other) { return new Vec3d(x - other.x, y - other.y, z - other.z); }
    public Vec3d scale(double factor) { return new Vec3d(x * factor, y * factor, z * factor); }
    public double dot(Vec3d other) { return x * other.x + y * other.y + z * other.z; }

    public Vec3d cross(Vec3d other) {
        return new Vec3d(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    public double lengthSquared() { return dot(this); }
    public double length() { return Math.sqrt(lengthSquared()); }
    public double distanceTo(Vec3d other) { return Math.sqrt(distanceSquaredTo(other)); }

    public double distanceSquaredTo(Vec3d other) {
        double dx = x - other.x, dy = y - other.y, dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public Vec3d normalize() { double length = length(); return length < 1.0e-9 ? ZERO : scale(1.0 / length); }
    public Vec3d lerp(Vec3d end, double t) { return scale(1.0 - t).add(end.scale(t)); }
    public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
}
