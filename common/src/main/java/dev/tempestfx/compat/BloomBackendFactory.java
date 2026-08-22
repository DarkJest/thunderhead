package dev.tempestfx.compat;

import dev.tempestfx.config.BloomMode;

public final class BloomBackendFactory {
    private BloomBackendFactory(){}
    public static BloomBackend create(BloomMode mode,RenderCompatibilityMode environment){
        return switch(mode){
            case OFF -> new DisabledBloomBackend();
            case COMPATIBILITY -> new SafeBloomBackend();
            case NATIVE -> environment==RenderCompatibilityMode.VANILLA?new SafeBloomBackend():new DisabledBloomBackend();
            case AUTO -> environment==RenderCompatibilityMode.VANILLA?new SafeBloomBackend():new DisabledBloomBackend();
        };
    }
}
