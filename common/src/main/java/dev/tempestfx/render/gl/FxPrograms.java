package dev.tempestfx.render.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tempestfx.TempestFx;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * The mod's own programs, compiled once and owned outright.
 *
 * <p>All or nothing: if any one of them fails to compile the whole set is discarded and the renderer
 * falls back to Minecraft's shader objects, because a pass drawn with the wrong program is worse than
 * a pass drawn the old way. Compilation is attempted lazily on the first frame that needs it and
 * retried after {@link #reload()}, which the client calls when it changes level, so a failure caused
 * by a resource pack being swapped in is not permanent.
 */
public final class FxPrograms implements AutoCloseable {
    /** Which program a pass is shaped by. */
    public enum Kind {
        /** Analytic channel cross-section; no texture. */
        BOLT("tempest_bolt", DefaultVertexFormat.POSITION_TEX_COLOR),
        /** Density curve over an alpha mask. */
        PARTICLE("tempest_particle", DefaultVertexFormat.POSITION_TEX_COLOR),
        /** Procedural pressure ring. */
        SHOCKWAVE("tempest_shockwave", DefaultVertexFormat.POSITION_TEX_COLOR),
        /** Curl-warped puff. */
        SMOKE("tempest_smoke", DefaultVertexFormat.POSITION_TEX_COLOR),
        /** Untextured fragments. */
        SOLID("tempest_solid", DefaultVertexFormat.POSITION_COLOR),
        /** The fullscreen composite. */
        COMPOSITE("tempest_composite", DefaultVertexFormat.POSITION_TEX_COLOR),
        /** Bright pass and first downsample of the effect attachment. Optional. */
        BLOOM_EXTRACT("tempest_bloom_extract", "tempest_bloom", DefaultVertexFormat.POSITION_TEX_COLOR, true),
        /** One axis of the gaussian, run twice. Optional. */
        BLOOM_BLUR("tempest_bloom_blur", "tempest_bloom", DefaultVertexFormat.POSITION_TEX_COLOR, true),
        /** Radial smear of the bloom, away from the channel. Optional. */
        BLOOM_SHAFTS("tempest_bloom_shafts", "tempest_bloom", DefaultVertexFormat.POSITION_TEX_COLOR, true);

        private final String shader;
        private final String vertexShader;
        private final VertexFormat format;
        private final boolean optional;

        Kind(String shader, VertexFormat format) {
            this(shader, shader, format, false);
        }

        Kind(String shader, String vertexShader, VertexFormat format, boolean optional) {
            this.shader = shader;
            this.vertexShader = vertexShader;
            this.format = format;
            this.optional = optional;
        }

        public VertexFormat format() {
            return format;
        }

        /**
         * Whether the mod can draw without this one.
         *
         * <p>The programs that shape geometry are all-or-nothing: a pass drawn with the wrong program
         * is worse than a pass drawn the old way. The post passes are not — bloom and light shafts are
         * enhancements on top of a complete image, so one that will not compile on some driver costs
         * exactly itself instead of dragging the whole native path down with it.
         */
        public boolean optional() {
            return optional;
        }
    }

    private final Map<Kind, FxProgram> programs = new EnumMap<>(Kind.class);
    private boolean enabled = true;
    private boolean failed;

    /**
     * Turned off by {@code compatibility.customShaders}, which means "do not use the mod's own
     * shaders" whichever way they are loaded.
     */
    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (!enabled) close();
    }

    /** @return the program, or {@code null} when the set is unavailable */
    public FxProgram get(Kind kind) {
        if (!ensureCompiled()) return null;
        return programs.get(kind);
    }

    /** Whether the mod can draw with its own programs at all. */
    public boolean available() {
        return ensureCompiled();
    }

    /** Allows another compilation attempt; called on level change. */
    public void reload() {
        if (!failed) return;
        failed = false;
    }

    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) return;
        programs.values().forEach(FxProgram::close);
        programs.clear();
    }

    private static FxProgram compile(Minecraft minecraft, Kind kind) throws java.io.IOException {
        List<String> attributes = kind.format().getElementAttributeNames();
        return FxProgram.compile(minecraft.getResourceManager(), kind.shader, kind.vertexShader, attributes);
    }

    private boolean ensureCompiled() {
        if (!enabled) return false;
        if (!programs.isEmpty()) return true;
        if (failed || !RenderSystem.isOnRenderThread()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) return false;
        try {
            for (Kind kind : Kind.values()) {
                if (kind.optional()) continue;
                programs.put(kind, compile(minecraft, kind));
            }
            TempestFx.log().info("Compiled {} effect programs", programs.size());
        } catch (Exception failure) {
            failed = true;
            close();
            TempestFx.log().warn("Effect programs unavailable; drawing through Minecraft's shaders instead",
                failure);
            return false;
        }
        // Separately, and survivably: losing these costs the enhancement and nothing else.
        try {
            int before = programs.size();
            for (Kind kind : Kind.values()) {
                if (kind.optional()) programs.put(kind, compile(minecraft, kind));
            }
            TempestFx.log().info("Compiled {} post-processing programs; bloom and light shafts available",
                programs.size() - before);
        } catch (Exception failure) {
            TempestFx.log().warn("Post-processing programs unavailable; bloom and light shafts are off",
                failure);
        }
        return true;
    }
}
