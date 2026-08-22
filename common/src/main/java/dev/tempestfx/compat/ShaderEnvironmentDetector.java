package dev.tempestfx.compat;

import java.lang.reflect.Method;

/**
 * Finds out which rendering pipeline is installed, and whether a shader pack is rendering right now.
 *
 * <p>The two questions are different. Iris being present says nothing about whether the player has a
 * pack enabled: with shaders off, Iris renders through vanilla and the mod's own core shaders are
 * fine. With a pack on, the world goes into Iris's G-buffers and is composited afterwards, and
 * geometry drawn by a program the pack does not know about has no auxiliary attachments for the
 * composite stage to work with.
 *
 * <p>Asked through Iris's own stable {@code v0} API, reflectively, so nothing links against it. When
 * the API cannot be reached the answer is "assume a pack is active", which costs the custom shaders
 * and keeps the effect visible - the safe direction to fail in.
 */
public final class ShaderEnvironmentDetector {
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String OPTIFINE_CONFIG = "net.optifine.Config";

    private Method irisInUse;
    private Object irisApi;
    private Method optifineShaders;
    private boolean probed;

    public RenderCompatibilityMode detect() {
        if (present("net.irisshaders.iris.Iris") || present("net.coderbot.iris.Iris")) {
            return RenderCompatibilityMode.IRIS;
        }
        if (present(OPTIFINE_CONFIG)) return RenderCompatibilityMode.OPTIFINE;
        return RenderCompatibilityMode.VANILLA;
    }

    /**
     * Whether a third-party shader pack is compositing the world at this moment.
     *
     * <p>Cheap enough to call every frame: one cached reflective call, or a field read when no
     * third-party pipeline is installed at all.
     */
    public boolean shaderPackActive(RenderCompatibilityMode mode) {
        return switch (mode) {
            case VANILLA -> false;
            case IRIS -> irisPackActive();
            case OPTIFINE -> optifineShadersActive();
            case UNKNOWN_SHADER_PIPELINE -> true;
            case AUTO -> false;
        };
    }

    private boolean irisPackActive() {
        resolve();
        if (irisApi == null || irisInUse == null) return true;
        try {
            return (Boolean) irisInUse.invoke(irisApi);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return true;
        }
    }

    private boolean optifineShadersActive() {
        resolve();
        if (optifineShaders == null) return true;
        try {
            return (Boolean) optifineShaders.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return true;
        }
    }

    private void resolve() {
        if (probed) return;
        probed = true;
        try {
            Class<?> api = Class.forName(IRIS_API);
            irisApi = api.getMethod("getInstance").invoke(null);
            irisInUse = api.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            irisApi = null;
            irisInUse = null;
        }
        try {
            optifineShaders = Class.forName(OPTIFINE_CONFIG).getMethod("isShaders");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            optifineShaders = null;
        }
    }

    private boolean present(String name) {
        try {
            Class.forName(name, false, getClass().getClassLoader());
            return true;
        } catch (LinkageError | ClassNotFoundException ignored) {
            return false;
        }
    }
}
