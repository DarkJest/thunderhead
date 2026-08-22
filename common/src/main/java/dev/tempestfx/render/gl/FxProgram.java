package dev.tempestfx.render.gl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

/**
 * A GLSL program the mod compiles, owns and binds itself.
 *
 * <p>Deliberately not a {@code ShaderInstance}. Minecraft's shader object is the hook every shader
 * loader reaches through: a pack that is compositing the world has to decide what to do about a
 * program it does not recognise, and what it decides is not the mod's business to predict. Iris, for
 * instance, disables colour and depth writes around an unknown {@code ShaderInstance} and rebinds its
 * own framebuffer inside {@code apply()} — entirely reasonable from its side, and fatal to a mod that
 * wanted to draw into a target of its own.
 *
 * <p>Compiling the same GLSL directly removes the question. There is no shader object to recognise,
 * nothing to override, and the mod's passes look like any other raw GL draw. It is also the reason the
 * effect can look identical in every pipeline: the program that shapes a lightning channel is always
 * the one that was written for it.
 *
 * <p>Uniforms are set by name and cached by location. A name that does not exist in the program is
 * silently ignored, which keeps the call sites free of null checks.
 */
public final class FxProgram implements AutoCloseable {
    private static final int LOG_LENGTH = 32768;

    private final String name;
    private final int id;
    private final Map<String, Integer> locations = new HashMap<>();
    private final FloatBuffer matrixScratch = BufferUtils.createFloatBuffer(16);

    private FxProgram(String name, int id) {
        this.name = name;
        this.id = id;
    }

    /**
     * Compiles {@code shaders/core/<name>.vsh} and {@code .fsh} from the {@code minecraft} namespace,
     * the same files the fallback path loads through Minecraft's own loader.
     *
     * @param attributes vertex attribute names in vertex-format order, bound to locations 0, 1, 2 …
     *     exactly as Minecraft binds them, so the mod's meshes upload unchanged
     */
    public static FxProgram compile(ResourceProvider resources, String name, List<String> attributes)
        throws IOException {
        return compile(resources, name, name, attributes);
    }

    /**
     * As above, but with the vertex stage taken from another file.
     *
     * <p>The post passes all draw the same clip-space fullscreen quad and differ only in their
     * fragment stage, so they share one vertex shader rather than carrying three identical copies.
     */
    public static FxProgram compile(ResourceProvider resources, String name, String vertexName,
                                    List<String> attributes) throws IOException {
        RenderSystem.assertOnRenderThread();
        int vertex = compileStage(resources, vertexName, ".vsh", GL20.GL_VERTEX_SHADER);
        int fragment = 0;
        int program = 0;
        try {
            fragment = compileStage(resources, name, ".fsh", GL20.GL_FRAGMENT_SHADER);
            program = GlStateManager.glCreateProgram();
            if (program == 0) throw new IOException("Could not create a program object for " + name);
            GlStateManager.glAttachShader(program, vertex);
            GlStateManager.glAttachShader(program, fragment);
            for (int index = 0; index < attributes.size(); index++) {
                GlStateManager._glBindAttribLocation(program, index, attributes.get(index));
            }
            GlStateManager.glLinkProgram(program);
            if (GlStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                throw new IOException("Could not link " + name + ": "
                    + GlStateManager.glGetProgramInfoLog(program, LOG_LENGTH));
            }
            FxProgram compiled = new FxProgram(name, program);
            program = 0;
            return compiled;
        } finally {
            // The program keeps its own copy once linked; the stage objects are only needed to link.
            GlStateManager.glDeleteShader(vertex);
            if (fragment != 0) GlStateManager.glDeleteShader(fragment);
            if (program != 0) GlStateManager.glDeleteProgram(program);
        }
    }

    public int id() {
        return id;
    }

    /**
     * Makes this the active program.
     *
     * <p>Through {@link GlStateManager} rather than raw GL on purpose: it is the call the game and any
     * pipeline built on it watch for, so anything keyed to "the program changed" stays correct.
     * Minecraft unbinds its own program after every draw, so this leaves nothing stale behind.
     */
    public void bind() {
        GlStateManager._glUseProgram(id);
    }

    /** Restores the state the game expects between draws: no program bound. */
    public static void unbind() {
        GlStateManager._glUseProgram(0);
    }

    public void setMatrix(String uniform, Matrix4f value) {
        int location = location(uniform);
        if (location < 0) return;
        matrixScratch.clear();
        value.get(matrixScratch);
        matrixScratch.position(0).limit(16);
        GlStateManager._glUniformMatrix4(location, false, matrixScratch);
    }

    public void setVector4(String uniform, float x, float y, float z, float w) {
        int location = location(uniform);
        if (location < 0) return;
        GL20.glUniform4f(location, x, y, z, w);
    }

    /** Points a sampler at a texture unit. */
    public void setSampler(String uniform, int unit) {
        int location = location(uniform);
        if (location < 0) return;
        GlStateManager._glUniform1i(location, unit);
    }

    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) return;
        GlStateManager.glDeleteProgram(id);
        locations.clear();
    }

    @Override
    public String toString() {
        return name + "#" + id;
    }

    private int location(String uniform) {
        return locations.computeIfAbsent(uniform, key -> GlStateManager._glGetUniformLocation(id, key));
    }

    private static int compileStage(ResourceProvider resources, String name, String extension, int type)
        throws IOException {
        ResourceLocation location = ResourceLocation.withDefaultNamespace("shaders/core/" + name + extension);
        String source;
        try (var reader = resources.openAsReader(location)) {
            source = reader.lines().reduce(new StringBuilder(),
                (builder, line) -> builder.append(line).append('\n'), StringBuilder::append).toString();
        }
        int shader = GlStateManager.glCreateShader(type);
        if (shader == 0) throw new IOException("Could not create a shader object for " + location);
        try {
            GlStateManager.glShaderSource(shader, List.of(source));
            GlStateManager.glCompileShader(shader);
            if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
                throw new IOException("Could not compile " + location + ": "
                    + GlStateManager.glGetShaderInfoLog(shader, LOG_LENGTH));
            }
            return shader;
        } catch (IOException | RuntimeException failure) {
            GlStateManager.glDeleteShader(shader);
            throw failure;
        }
    }
}
