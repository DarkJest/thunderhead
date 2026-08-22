package dev.tempestfx.storm;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;

/**
 * Decides when the storm produces a discharge that never reaches the ground, and what kind.
 *
 * <p>This is the piece that makes a storm a system rather than a series of unrelated bolts. Vanilla
 * only ever tells the client about strikes that land; everything a real storm does between and
 * inside its clouds - which is most of what it does - has no server-side counterpart at all, so it
 * is scheduled here, on the client, from the smoothed charge and the replicated world clock.
 *
 * <p>Consistency between players is deliberately not enforced. A ground strike must look the same
 * to everyone because the server applies damage for it; an intracloud pulse four hundred blocks away
 * is ambience, and paying for a packet to synchronise it would buy nothing a player could ever
 * notice.
 */
public final class LightningEventPlanner {
    /** Quietest storm: roughly one ambient event every forty seconds. */
    private static final float SLOWEST_INTERVAL_TICKS = 800;
    /**
     * Busiest storm: about one every ten seconds after the jitter, and that is the ceiling rather
     * than the norm.
     *
     * <p>Deliberately far slower than a real storm's intracloud rate. The point of sky activity is
     * that a distant storm looks alive between ground strikes, not that the sky flickers - and a
     * player watching a small patch of sky reads anything faster than this as strobing rather than
     * as weather. Players who want more turn {@code sky.activityRate} up.
     */
    private static final float FASTEST_INTERVAL_TICKS = 200;
    /** Closest an ambient discharge is placed, in blocks. Near enough is a ground strike's job. */
    private static final double MIN_DISTANCE = 90;
    /** How far off the storm's own bearing an event may sit, in radians. */
    private static final double BEARING_SPREAD = 1.05;
    /** Altitude spread around the cloud layer, in blocks. */
    private static final double ALTITUDE_SPREAD = 34;

    private int cooldown;
    private long sequence;

    /**
     * @return the discharge to raise this tick, or {@code null} on the great majority of ticks
     */
    public AmbientDischarge plan(StormElectricState state, StormSample sample, TempestConfig config,
                                 Vec3d camera) {
        if (!config.sky.skyActivity || config.sky.activityRate <= 0) return null;
        if (!state.active()) return null;
        if (cooldown > 0) { cooldown--; return null; }

        long seed = StrikeSeed.derive(state.stormSeed(), sequence++);
        DischargeType type = chooseType(seed, config);
        if (type == null) { cooldown = (int) SLOWEST_INTERVAL_TICKS; return null; }

        cooldown = nextInterval(state.activity(), config.sky.activityRate, seed);
        return place(type, seed, state, sample, camera);
    }

    /** Ticks until the next event, from the storm's charge and the player's pacing setting. */
    private static int nextInterval(float activity, float rate, long seed) {
        double base = FxMath.lerp(SLOWEST_INTERVAL_TICKS, FASTEST_INTERVAL_TICKS,
            FxMath.clamp(activity, 0, 1));
        double jittered = base * (0.55 + StrikeSeed.unit(seed, 0x91) * 0.9) / Math.max(0.05f, rate);
        return (int) Math.max(3, Math.round(jittered));
    }

    /**
     * Which archetype fires. The megaflash roll comes first and is deliberately a long shot: the
     * whole value of the event is that a player has not seen one before.
     */
    private static DischargeType chooseType(long seed, TempestConfig config) {
        boolean horizontal = config.sky.cloudToCloud;
        boolean internal = config.sky.intracloud;
        if (horizontal && StrikeSeed.unit(seed, 0xa5) < 1.0 / Math.max(1, config.sky.megaflashRarity)) {
            return DischargeType.MEGAFLASH;
        }
        if (internal && horizontal) {
            // Weighted toward the buried event on purpose. An intracloud pulse is a cloud brightening
            // from within; a cloud-to-cloud channel is a bare strand across the sky, and a strand is
            // the conspicuous one, so it stays the minority.
            return StrikeSeed.unit(seed, 0xb7) < 0.74 ? DischargeType.INTRACLOUD : DischargeType.CLOUD_TO_CLOUD;
        }
        if (internal) return DischargeType.INTRACLOUD;
        if (horizontal) return DischargeType.CLOUD_TO_CLOUD;
        return null;
    }

    /** Puts the discharge somewhere in the storm: out along its bearing, up at the cloud layer. */
    private static AmbientDischarge place(DischargeType type, long seed, StormElectricState state,
                                          StormSample sample, Vec3d camera) {
        double reach = Math.max(MIN_DISTANCE + 40, sample.viewDistance());
        double distance = Math.max(MIN_DISTANCE,
            reach * (0.45 + StrikeSeed.unit(seed, 0x21) * 0.5) * (type == DischargeType.MEGAFLASH ? 1.15 : 1));
        double bearing = state.bearing() + StrikeSeed.signed(seed, 0x22) * BEARING_SPREAD;
        double altitude = sample.cloudBaseY() + StrikeSeed.signed(seed, 0x23) * ALTITUDE_SPREAD;

        Vec3d centre = new Vec3d(
            camera.x() + Math.cos(bearing) * distance,
            altitude,
            camera.z() + Math.sin(bearing) * distance);

        double span = span(type, seed);
        // Channels run roughly along the front rather than at the player, which is what makes them
        // read as travelling through the storm instead of toward the camera.
        double heading = state.bearing() + Math.PI / 2 + StrikeSeed.signed(seed, 0x24) * 0.6;
        Vec3d half = new Vec3d(Math.cos(heading) * span * 0.5,
            StrikeSeed.signed(seed, 0x25) * span * 0.05,
            Math.sin(heading) * span * 0.5);

        float energy = (float) (0.55 + state.activity() * 0.55 + StrikeSeed.unit(seed, 0x26) * 0.3);
        return new AmbientDischarge(type, centre.subtract(half), centre.add(half), energy, seed);
    }

    /** How far a channel of this archetype reaches, in blocks. */
    private static double span(DischargeType type, long seed) {
        double roll = StrikeSeed.unit(seed, 0x27);
        return switch (type) {
            case INTRACLOUD -> 26 + roll * 44;
            case CLOUD_TO_CLOUD -> 110 + roll * 190;
            case MEGAFLASH -> 480 + roll * 520;
            default -> 90 + roll * 60;
        };
    }

    /** Drops the schedule when the player leaves the level. */
    public void clear() { cooldown = 0; }
}
