package dev.tempestfx.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.TempestFx;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.effect.ActiveLightningEffect;
import dev.tempestfx.effect.LightningLook;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.render.gl.FxStateGuard;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Opt-in recognized POSITION_COLOR lightning material: the pack controls bloom and exposure. */
public final class PackChannelRenderer implements AutoCloseable {
    private ByteBufferBuilder storage;
    private MultiBufferSource.BufferSource buffers;
    private final FxStateGuard guard = new FxStateGuard();
    private boolean failed;
    private RenderType channelType;
    private boolean typeResolved;

    public boolean draw(WorldFxRenderer.Scene scene, PoseStack stack, Vec3d camera, float partialTick, TempestConfig config) {
        if (failed) return false;
        RenderType type = channelType();
        if (type == null) return false;
        if (storage == null) {
            storage = new ByteBufferBuilder(512 * 1024);
            buffers = MultiBufferSource.immediate(storage);
        }
        guard.capture(false);
        stack.pushPose();
        stack.translate(-camera.x(), -camera.y(), -camera.z());
        try {
            VertexConsumer color = new ColorOnly(buffers.getBuffer(type));
            emit(scene.lightning(), stack, color, camera, partialTick, config);
            emit(scene.distantBolts(), stack, color, camera, partialTick, config);
            buffers.endBatch(type);
            return true;
        } catch (RuntimeException failure) {
            failed = true;
            TempestFx.log().warn("Pack-native channel pass failed; returning to isolated channels", failure);
            return false;
        } finally {
            stack.popPose();
            TempestRenderTypes.restoreRenderState();
            guard.restore();
        }
    }

    private RenderType channelType() {
        if (typeResolved) return channelType;
        typeResolved = true;
        try {
            // Iris wraps vanilla's lightning type to set the pack's lightning entity material ID
            // and restore it after drawing. Plain RenderType.lightning bypasses that wrapper.
            Object type = Class.forName("net.irisshaders.iris.pathways.LightningHandler")
                .getField("IRIS_LIGHTNING").get(null);
            if (type instanceof RenderType renderType
                && renderType.format().equals(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR)) {
                channelType = renderType;
                TempestFx.log().info("Pack-native lightning adapter: Iris POSITION_COLOR material wrapper");
            }
        } catch (ReflectiveOperationException | LinkageError failure) {
            TempestFx.log().info("Pack-native lightning adapter unavailable; keeping isolated channels");
        }
        return channelType;
    }

    private void emit(List<ActiveLightningEffect> effects, PoseStack stack, VertexConsumer consumer,
                      Vec3d camera, float partialTick, TempestConfig config) {
        float projectionY = Math.abs(RenderSystem.getProjectionMatrix().m11());
        int height = Minecraft.getInstance().getMainRenderTarget().height;
        for (var effect : effects) {
            var look = LightningLook.resolve(config, effect.event().style());
            var style = effect.event().style();
            int rgb = style != null && style.hasCoreColor() ? style.coreColor() : 0xf7fcff;
            for (var segment : effect.segments()) {
                if (!effect.segmentVisible(segment, partialTick)) continue;
                float power = effect.segmentBrightness(segment, partialTick, config.general.reducedFlashing)
                    * (float) segment.intensity();
                if (power <= .002f) continue;
                double floor = segment.start().distanceTo(camera) * 1.4 / Math.max(1, projectionY * height);
                double start = Math.max(floor, segment.startWidth() * look.thickness());
                double end = Math.max(floor, segment.endWidth() * look.thickness());
                RibbonRenderer.renderRibbon(stack.last(), consumer,
                    segment.start().x(), segment.start().y(), segment.start().z(),
                    segment.end().x(), segment.end().y(), segment.end().z(),
                    camera.x(), camera.y(), camera.z(), start, end,
                    ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, Math.min(1, power));
            }
        }
    }

    @Override public void close() {
        if (buffers != null && !failed) buffers.endBatch();
        buffers = null;
        if (storage != null) { storage.close(); storage = null; }
        failed = false;
        typeResolved = false;
        channelType = null;
    }

    private record ColorOnly(VertexConsumer delegate) implements VertexConsumer {
        public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
