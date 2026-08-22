package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.effect.EntityDischarge;
import dev.tempestfx.math.Vec3d;
import java.util.List;

/**
 * Draws the arcs crawling over a charged entity.
 */
public final class EntityDischargeRenderer {
    private static final double CORE_WIDTH = 0.012;
    private static final double SHEATH_WIDTH = 0.05;

    public void render(List<EntityDischarge> discharges, PoseStack.Pose pose, VertexConsumer consumer,
                       Vec3d camera, float partialTick) {
        for (int index = 0; index < discharges.size(); index++) {
            renderOne(discharges.get(index), pose, consumer, camera, partialTick);
        }
    }

    private void renderOne(EntityDischarge discharge, PoseStack.Pose pose, VertexConsumer consumer,
                           Vec3d camera, float partialTick) {
        float charge = discharge.charge(partialTick);
        if (charge <= 0.02f) return;
        Vec3d anchor = discharge.anchor(partialTick);
        double[] points = discharge.arcPoints();

        // Arcs fade in from the tips as the charge drops, so the effect thins out rather than blinking.
        int visibleArcs = Math.max(1, Math.round(EntityDischarge.ARCS * Math.min(1f, charge * 1.3f)));
        for (int arc = 0; arc < visibleArcs; arc++) {
            int base = arc * EntityDischarge.POINTS_PER_ARC * 3;
            for (int point = 0; point < EntityDischarge.POINTS_PER_ARC - 1; point++) {
                int a = base + point * 3;
                int b = a + 3;
                double sx = anchor.x() + points[a], sy = anchor.y() + points[a + 1], sz = anchor.z() + points[a + 2];
                double ex = anchor.x() + points[b], ey = anchor.y() + points[b + 1], ez = anchor.z() + points[b + 2];
                RibbonRenderer.renderRibbon(pose, consumer, sx, sy, sz, ex, ey, ez,
                    camera.x(), camera.y(), camera.z(),
                    SHEATH_WIDTH * charge, SHEATH_WIDTH * charge,
                    0.42f, 0.62f, 1f, charge * 0.32f);
                RibbonRenderer.renderRibbon(pose, consumer, sx, sy, sz, ex, ey, ez,
                    camera.x(), camera.y(), camera.z(),
                    CORE_WIDTH, CORE_WIDTH,
                    0.94f, 0.98f, 1f, charge);
            }
        }
    }
}
