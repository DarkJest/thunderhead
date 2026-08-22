package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.effect.RodCorona;
import dev.tempestfx.math.Vec3d;
import java.util.List;

/**
 * Draws the corona crawling off a charged lightning rod.
 *
 * <p>Into the same two batches everything else uses — the filaments with the electricity, the point
 * glow with the rest of the glow — so this adds no draw call, no GL state and nothing the compositor
 * has to know about, and it looks the same under a shader pack as without one.
 */
public final class RodCoronaRenderer {
    private static final double CORE_WIDTH = 0.008;
    private static final double SHEATH_WIDTH = 0.03;
    /** The corona is violet-white: it is positive charge streaming off a point. */
    private static final float[] SHEATH = { 0.62f, 0.55f, 1f };
    private static final float CUTOFF = 0.02f;

    /** The filaments, into the additive electricity pass. */
    public void renderArcs(List<RodCorona> coronas, PoseStack.Pose pose, VertexConsumer consumer,
                           Vec3d camera, float partialTick) {
        for (RodCorona corona : coronas) {
            float charge = corona.charge(partialTick);
            if (charge <= CUTOFF) continue;
            Vec3d tip = corona.tip();
            double[] points = corona.arcPoints();
            // Filaments come and go with the charge, so a rod thins out rather than blinking off.
            int visible = Math.max(1, Math.round(RodCorona.ARCS * Math.min(1f, charge * 1.4f)));
            for (int arc = 0; arc < visible; arc++) {
                int base = arc * RodCorona.POINTS_PER_ARC * 3;
                for (int point = 0; point < RodCorona.POINTS_PER_ARC - 1; point++) {
                    int a = base + point * 3;
                    int b = a + 3;
                    double sx = tip.x() + points[a], sy = tip.y() + points[a + 1], sz = tip.z() + points[a + 2];
                    double ex = tip.x() + points[b], ey = tip.y() + points[b + 1], ez = tip.z() + points[b + 2];
                    RibbonRenderer.renderRibbon(pose, consumer, sx, sy, sz, ex, ey, ez,
                        camera.x(), camera.y(), camera.z(),
                        SHEATH_WIDTH * charge, SHEATH_WIDTH * charge,
                        SHEATH[0], SHEATH[1], SHEATH[2], charge * 0.3f);
                    RibbonRenderer.renderRibbon(pose, consumer, sx, sy, sz, ex, ey, ez,
                        camera.x(), camera.y(), camera.z(),
                        CORE_WIDTH, CORE_WIDTH,
                        0.9f, 0.92f, 1f, charge * 0.85f);
                }
            }
        }
    }

    /** A small point glow at the tip, into the glow pass. */
    public void renderGlow(List<RodCorona> coronas, PoseStack.Pose pose, VertexConsumer consumer,
                           Vec3d camera, float partialTick) {
        for (RodCorona corona : coronas) {
            float charge = corona.charge(partialTick);
            if (charge <= CUTOFF) continue;
            RibbonRenderer.cameraQuad(pose, consumer,
                corona.tip().x(), corona.tip().y(), corona.tip().z(),
                camera.x(), camera.y(), camera.z(), 0.16 + charge * 0.2,
                0.72f, 0.68f, 1f, Math.min(0.5f, charge * 0.34f));
        }
    }

    /** Whether any rod is lit, so the caller can skip the batch. */
    public static boolean any(List<RodCorona> coronas) {
        for (RodCorona corona : coronas) if (corona.visible()) return true;
        return false;
    }
}
