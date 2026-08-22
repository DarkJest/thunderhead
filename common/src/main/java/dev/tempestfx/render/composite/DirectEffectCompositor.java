package dev.tempestfx.render.composite;

/**
 * No isolation at all: the effect is drawn straight into whatever framebuffer the game has bound.
 *
 * <p>This is the behaviour the mod had before there was a compositor, kept as a first-class
 * implementation rather than a flag. It is what {@code compatibility.effectCompositor = false}
 * selects, and it is the shape every other implementation degrades into, so the world pass only ever
 * has to handle one contract.
 */
public final class DirectEffectCompositor implements EffectCompositor {
    @Override
    public boolean beginWorldPass() {
        return false;
    }

    @Override
    public void endWorldPass() {
    }

    @Override
    public void composite(DistortionField distortion, LightShaftField shafts) {
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public void tick(boolean busy) {
    }

    @Override
    public void close() {
    }
}
