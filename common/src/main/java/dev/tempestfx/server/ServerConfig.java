package dev.tempestfx.server;

import dev.tempestfx.math.FxMath;

/**
 * Server-side gameplay configuration, kept in its own file because it changes what the game
 * <em>does</em>, not what it looks like.
 */
public final class ServerConfig {
    public NearMiss nearMiss = new NearMiss();
    public BallLightning ballLightning = new BallLightning();

    public static final class NearMiss {
        /** Damage entities that were close to a strike but outside vanilla's own damage box. */
        public boolean enabled = true;
        /** Outer radius of the effect, in blocks. Vanilla already covers the inner 3. */
        public float radius = 9f;
        /** Damage at the edge of vanilla's box, falling to zero at {@link #radius}. */
        public float maxDamage = 5f;
        /** Seconds of ignition for targets very close to the strike; 0 disables ignition. */
        public float igniteSeconds = 2f;
        /** Fraction of {@link #radius} within which ignition can happen. */
        public float igniteFraction = 0.45f;
        /** Also apply to non-player mobs. */
        public boolean affectMobs = true;
    }

    public static final class BallLightning {
        /** Allow strikes to leave ball lightning behind. */
        public boolean enabled = true;
        /** Probability per strike, 0..1. Real ball lightning is rare, and so is this. */
        public float chancePerStrike = 0.05f;
        /** Only spawn when the strike is at least this far from any player, to avoid instant hits. */
        public float minimumSpawnDistance = 3f;
        public float minRadius = 0.35f;
        public float maxRadius = 0.75f;
        public float minSeconds = 4f;
        public float maxSeconds = 11f;
        /** Damage dealt when it touches something; 0 makes it purely decorative. */
        public float damage = 6f;
        public boolean contactDamage = true;
        public float contactRadius = 0.7f;
        public int contactCooldownTicks = 10;
        public float igniteSeconds = 3f;
        /** Scorch the grass block under the burst. Also requires the mobGriefing game rule. */
        public boolean scorchGround = true;

        public float contactDamage() { return damage; }
    }

    public ServerConfig validate() {
        if (nearMiss == null) nearMiss = new NearMiss();
        if (ballLightning == null) ballLightning = new BallLightning();

        nearMiss.radius = FxMath.clamp(nearMiss.radius, 3.1f, 48f);
        nearMiss.maxDamage = FxMath.clamp(nearMiss.maxDamage, 0f, 40f);
        nearMiss.igniteSeconds = FxMath.clamp(nearMiss.igniteSeconds, 0f, 30f);
        nearMiss.igniteFraction = FxMath.clamp(nearMiss.igniteFraction, 0f, 1f);

        ballLightning.chancePerStrike = FxMath.clamp(ballLightning.chancePerStrike, 0f, 1f);
        ballLightning.minimumSpawnDistance = FxMath.clamp(ballLightning.minimumSpawnDistance, 0f, 64f);
        ballLightning.minRadius = FxMath.clamp(ballLightning.minRadius, 0.1f, 3f);
        ballLightning.maxRadius = FxMath.clamp(ballLightning.maxRadius, ballLightning.minRadius, 3f);
        ballLightning.minSeconds = FxMath.clamp(ballLightning.minSeconds, 0.5f, 120f);
        ballLightning.maxSeconds = FxMath.clamp(ballLightning.maxSeconds, ballLightning.minSeconds, 120f);
        ballLightning.damage = FxMath.clamp(ballLightning.damage, 0f, 40f);
        ballLightning.contactRadius = FxMath.clamp(ballLightning.contactRadius, 0.1f, 6f);
        ballLightning.contactCooldownTicks = FxMath.clamp(ballLightning.contactCooldownTicks, 1, 200);
        ballLightning.igniteSeconds = FxMath.clamp(ballLightning.igniteSeconds, 0f, 30f);
        return this;
    }
}
