package dev.tempestfx.effect;

import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns the coronas on the lightning rods around the player.
 *
 * <p>The set of rods comes from a scan the client runs occasionally; this only decides how brightly
 * each one is glowing and keeps their arcs alive. Rods that survive a rescan keep their state, so a
 * corona does not restart every time the scan runs.
 */
public final class RodCoronaSystem {
    /** Ceiling on rods lit at once. A field of them still costs a handful of tiny ribbons each. */
    private static final int MAX_RODS = 16;
    /** Rods within this distance of a previous one are treated as the same rod across rescans. */
    private static final double SAME_ROD = 0.01;

    private final List<RodCorona> coronas = new ArrayList<>();
    private final List<RodCorona> view = Collections.unmodifiableList(coronas);

    /**
     * Replaces the known rods with what the latest scan found, preserving the state of any that were
     * already glowing.
     */
    public void refresh(List<Vec3d> tips, TempestConfig config) {
        if (!config.impact.rodCorona) {
            coronas.clear();
            return;
        }
        List<RodCorona> next = new ArrayList<>(Math.min(MAX_RODS, tips.size()));
        for (Vec3d tip : tips) {
            if (next.size() >= MAX_RODS) break;
            RodCorona existing = find(tip);
            next.add(existing != null ? existing
                : new RodCorona(tip, StrikeSeed.of(tip.x(), tip.y(), tip.z(), 0x0d)));
        }
        coronas.clear();
        coronas.addAll(next);
    }

    private RodCorona find(Vec3d tip) {
        for (RodCorona corona : coronas) {
            if (corona.tip().distanceSquaredTo(tip) < SAME_ROD) return corona;
        }
        return null;
    }

    /** @param stormCharge the storm's electrical activity, which is the only thing driving this */
    public void tick(float stormCharge, TempestConfig config) {
        if (!config.impact.rodCorona) {
            if (!coronas.isEmpty()) coronas.clear();
            return;
        }
        for (RodCorona corona : coronas) corona.tick(stormCharge);
    }

    public List<RodCorona> coronas() { return view; }

    /** How many are actually glowing, for the debug overlay. */
    public int activeCount() {
        int count = 0;
        for (RodCorona corona : coronas) if (corona.visible()) count++;
        return count;
    }

    public void clear() { coronas.clear(); }
}
