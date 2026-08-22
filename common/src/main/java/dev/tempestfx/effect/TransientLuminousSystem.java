package dev.tempestfx.effect;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.sky.LuminousProfile;
import dev.tempestfx.sky.LuminousProfiles;
import dev.tempestfx.sky.LuminousStructure;
import dev.tempestfx.sky.LuminousStructures;
import dev.tempestfx.sky.TransientLuminousEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns the sprites and jets currently in the sky above the storm.
 *
 * <p>These are consequences rather than events of their own: a sprite is triggered by a discharge
 * powerful enough to have produced one, and it is placed above <em>that</em> discharge. That is both
 * what really happens and what makes it readable - a player who has just watched a violet superbolt
 * has a reason to look up.
 *
 * <p>Two rules keep them worth seeing. They are rare, by a configurable chance on an already rare
 * parent event. And a sprite is only raised over a discharge far enough away to be looked <em>at</em>
 * rather than stood under: from directly beneath a storm the cloud deck is in the way, which is true
 * of the real thing and true of Minecraft's cloud layer as well.
 */
public final class TransientLuminousSystem {
    /** Closest parent discharge that may raise a sprite, in blocks. */
    private static final double MIN_SPRITE_PARENT_DISTANCE = 120;
    /** How far above the cloud layer a sprite sits, as a fraction of its own height. */
    private static final double SPRITE_ALTITUDE_RATIO = 1.35;
    /** A megaflash is already a once-in-hours event, so it gets far better odds than a superbolt. */
    private static final float MEGAFLASH_MULTIPLIER = 4f;
    /** Sideways drift of a sprite from the discharge that caused it, in blocks. */
    private static final double SPRITE_DRIFT = 55;

    private final List<ActiveLuminousEvent> active = new ArrayList<>();
    private final List<ActiveLuminousEvent> view = Collections.unmodifiableList(active);

    /**
     * Rolls for a sprite over a discharge that has just happened.
     *
     * @param position    where the parent discharge was
     * @param listener    where the player is, which decides whether a sprite could be seen at all
     * @param cloudBaseY  the cloud layer, which the sprite is placed well above
     * @return the event raised, or {@code null}, which is the overwhelmingly common answer
     */
    public ActiveLuminousEvent onPowerfulDischarge(DischargeType type, Vec3d position, Vec3d listener,
                                                   double cloudBaseY, long seed, float energy,
                                                   TempestConfig config) {
        if (!config.sky.redSprites || config.general.reducedFlashing) return null;
        if (type != DischargeType.POSITIVE_CLOUD_TO_GROUND && type != DischargeType.MEGAFLASH) return null;
        if (listener.distanceTo(position) < MIN_SPRITE_PARENT_DISTANCE) return null;

        float chance = config.sky.spriteChance
            * (type == DischargeType.MEGAFLASH ? MEGAFLASH_MULTIPLIER : 1f);
        if (StrikeSeed.unit(seed, 0x5b817e) >= chance) return null;

        LuminousProfile profile = LuminousProfiles.of(TransientLuminousEvent.RED_SPRITE);
        long spriteSeed = StrikeSeed.derive(seed, 0x5b817e);
        Vec3d anchor = new Vec3d(
            position.x() + StrikeSeed.signed(spriteSeed, 0x1) * SPRITE_DRIFT,
            cloudBaseY + profile.height() * SPRITE_ALTITUDE_RATIO,
            position.z() + StrikeSeed.signed(spriteSeed, 0x2) * SPRITE_DRIFT);
        return raise(profile, anchor, spriteSeed, Math.min(1.6f, 0.85f + energy * 0.3f),
            0.85 + StrikeSeed.unit(spriteSeed, 0x3) * 0.5, config);
    }

    /**
     * Rolls for a jet out of the top of an active cloud.
     *
     * @param cloudTop where the cone leaves the cloud
     */
    public ActiveLuminousEvent onCloudTopActivity(Vec3d cloudTop, long seed, float energy, TempestConfig config) {
        if (!config.sky.blueJets || config.general.reducedFlashing) return null;
        if (StrikeSeed.unit(seed, 0x81e7) >= config.sky.blueJetChance) return null;

        LuminousProfile profile = LuminousProfiles.of(TransientLuminousEvent.BLUE_JET);
        long jetSeed = StrikeSeed.derive(seed, 0x81e7);
        return raise(profile, cloudTop, jetSeed, Math.min(1.4f, 0.8f + energy * 0.25f),
            0.8 + StrikeSeed.unit(jetSeed, 0x4) * 0.55, config);
    }

    private ActiveLuminousEvent raise(LuminousProfile profile, Vec3d anchor, long seed, float energy,
                                      double scale, TempestConfig config) {
        int limit = config.sky.maxLuminousEvents;
        if (limit <= 0) return null;
        while (active.size() >= limit) active.removeFirst();

        LuminousStructure structure = LuminousStructures.create(profile, anchor, seed, scale);
        ActiveLuminousEvent event = new ActiveLuminousEvent(profile, structure, seed, energy);
        active.add(event);
        return event;
    }

    /** Raises one outright, bypassing every roll. For the debug command. */
    public ActiveLuminousEvent trigger(TransientLuminousEvent type, Vec3d anchor, long seed,
                                       TempestConfig config) {
        return raise(LuminousProfiles.of(type), anchor, seed, 1.2f, 1.0, config);
    }

    public void tick() {
        for (int index = active.size() - 1; index >= 0; index--) {
            ActiveLuminousEvent event = active.get(index);
            event.tick();
            if (!event.alive()) active.remove(index);
        }
    }

    public List<ActiveLuminousEvent> events() { return view; }

    public int activeCount() { return active.size(); }

    public void clear() { active.clear(); }
}
