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
        COMPOSITE("tempest_composite", DefaultVertexFormat.POSITION_TEX_COLOR);

        private final String shader;
        private final VertexFormat format;

        Kind(String shader, VertexFormat format) {
            this.shader = shader;
            this.format = format;
        }

        public VertexFormat format() {
            return format;
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

    private boolean ensureCompiled() {
        if (!enabled) return false;
        if (!programs.isEmpty()) return true;
        if (failed || !RenderSystem.isOnRenderThread()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) return false;
        try {
            for (Kind kind : Kind.values()) {
                List<String> attributes = kind.format().getElementAttributeNames();
                programs.put(kind, FxProgram.compile(minecraft.getResourceManager(), kind.shader, attributes));
            }
            TempestFx.log().info("Compiled {} effect programs", programs.size());
            return true;
        } catch (Exception failure) {
            failed = true;
            close();
            TempestFx.log().warn("Effect programs unavailable; drawing through Minecraft's shaders instead",
                failure);
            return false;
        }
    }
}
