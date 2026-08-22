package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.api.StrikeTarget;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Leaves a scorched ash mark where a player was struck directly.
 *
 * <p>Only a strike whose {@link StrikeTarget} is a player produces a mark. The target is resolved
 * from replicated positions at strike time, so the mark appears in the same place for everyone, and
 * it is purely decorative: no block is placed or changed.
 */
public final class AshImprintSystem {
    private static final int MAX_IMPRINTS = 12;

    private final List<AshImprint> imprints = new ArrayList<>();
    private final List<AshImprint> view = Collections.unmodifiableList(imprints);

    /** @return the created imprint, or {@code null} when this strike does not leave one. */
    public AshImprint onStrike(LightningStrikeFxEvent event, TempestConfig config) {
        if (!config.impact.ashImprint || !event.directPlayerHit()) return null;
        StrikeTarget target = event.target();
        double surfaceY = event.environment().surfaceY(target.position().y());
        Vec3d anchor = new Vec3d(target.position().x(), surfaceY, target.position().z());
        float radius = Math.max(0.75f, target.width() * 1.35f);
        int lifetime = Math.round(config.impact.ashImprintSeconds * 20f);

        if (imprints.size() >= MAX_IMPRINTS) imprints.removeFirst();
        AshImprint imprint = new AshImprint(anchor, radius, StrikeSeed.derive(event.seed(), 0xa54), lifetime);
        imprints.add(imprint);
        return imprint;
    }

    public void tick() {
        for (int index = imprints.size() - 1; index >= 0; index--) {
            AshImprint imprint = imprints.get(index);
            imprint.tick();
            if (!imprint.alive()) imprints.remove(index);
        }
    }

    public List<AshImprint> imprints() { return view; }

    public int activeCount() { return imprints.size(); }

    public void clear() { imprints.clear(); }
}
