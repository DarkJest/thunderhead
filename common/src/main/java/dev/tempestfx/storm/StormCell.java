package dev.tempestfx.storm;

import dev.tempestfx.api.LightningKind;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;

/** Low-cost charge reservoir, drift and lifecycle; deliberately not a fluid/field simulation. */
public final class StormCell {
    private final long seed, born;
    private final Vec3d center;
    private double charge;
    private int flashes;
    public record Discharge(long seed, LightningKind kind, Vec3d origin, Vec3d target) {}
    public StormCell(long seed, long born, Vec3d center) { this.seed = seed; this.born = born; this.center = center; }
    public boolean expired(long tick) { return tick - born >= 3600; }
    public Discharge tick(long now, float activity, boolean groundEnabled, double rate) {
        if (expired(now) || activity <= 0) return null;
        double age = Math.max(0, now - born);
        double envelope = Math.min(1, age / 200) * Math.min(1, (3600 - age) / 600);
        charge = Math.min(2, charge + Math.max(0, activity) * envelope * rate / 20);
        if (charge < 1) return null;
        charge -= 1;
        long flashSeed = StrikeSeed.derive(seed, ++flashes);
        double x = center.x() + age * .015 + StrikeSeed.signed(flashSeed, 2) * 100;
        double z = center.z() + age * .008 + StrikeSeed.signed(flashSeed, 3) * 100;
        Vec3d source = new Vec3d(x, center.y(), z);
        double roll = StrikeSeed.unit(flashSeed, 4);
        LightningKind kind = groundEnabled && roll < .25 ? (roll < .025 ? LightningKind.POSITIVE_GROUND : LightningKind.NEGATIVE_GROUND)
            : roll < .85 ? LightningKind.INTRACLOUD : LightningKind.INTERCLOUD;
        double span = kind == LightningKind.INTERCLOUD ? 180 : 85;
        return new Discharge(flashSeed, kind, source, source.add(span, -15, StrikeSeed.signed(flashSeed, 5) * 50));
    }
}
