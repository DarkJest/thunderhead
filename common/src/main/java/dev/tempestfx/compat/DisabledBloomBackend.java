package dev.tempestfx.compat;

public final class DisabledBloomBackend implements BloomBackend {
    @Override public boolean isAvailable(){return false;}
    @Override public void begin(){}
    @Override public void end(){}
}
