package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.FxMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Residual electrical discharge on entities that were near a strike.
 */
public final class EntityDischargeSystem {
    private static final int MAX_TRACKED = 16;
    private static final int BASE_TICKS = 70;

    private final List<EntityDischarge> discharges = new ArrayList<>();
    private final List<EntityDischarge> view = Collections.unmodifiableList(discharges);

    /**
     * @param nearby entities the client resolved inside the configured radius; may be empty
     * @return number of targets that newly started arcing, so the caller can react (for example
     *         by playing the arc sound once)
     */
    public int onStrike(LightningStrikeFxEvent event, TempestConfig config, List<DischargeTarget> nearby) {
        if (!config.impact.entityDischarge || config.impact.entityDischargeRadius <= 0) return 0;
        int started = 0;
        for (DischargeTarget target : nearby) {
            double distance = target.position().distanceTo(event.position());
            float proximity = (float) FxMath.distanceFalloff(distance, 1.0, config.impact.entityDischargeRadius);
            if (proximity <= 0.02f) continue;
            EntityDischarge discharge = find(target.entityId());
            if (discharge == null) {
                if (discharges.size() >= MAX_TRACKED) continue;
                discharge = new EntityDischarge(target.entityId(), target.player(), event.seed());
                discharges.add(discharge);
                started++;
            }
            discharge.reinforce(target, proximity * event.intensity(), Math.round(BASE_TICKS * (0.5f + proximity)));
        }
        return started;
    }

    public void tick(TempestConfig config, DischargeTarget.Lookup lookup, long gameTime) {
        for (int index = discharges.size() - 1; index >= 0; index--) {
            EntityDischarge discharge = discharges.get(index);
            DischargeTarget target = lookup.resolve(discharge.entityId());
            if (target == null) {
                discharges.remove(index);
                continue;
            }
            discharge.tick(target, config.impact.entityDischargeMinSpeed, gameTime);
            if (discharge.expired()) discharges.remove(index);
        }
    }

    public List<EntityDischarge> discharges() { return view; }

    public int activeCount() { return discharges.size(); }

    public void clear() { discharges.clear(); }

    private EntityDischarge find(int entityId) {
        for (EntityDischarge discharge : discharges) {
            if (discharge.entityId() == entityId) return discharge;
        }
        return null;
    }
}
