package dev.tempestfx.effect;

import dev.tempestfx.lightning.LightningEnvelope;
import dev.tempestfx.lightning.LightningSegment;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.sky.LuminousProfile;
import dev.tempestfx.sky.LuminousStructure;

/**
 * Live state of one sprite or jet: fixed structure plus a continuous time envelope.
 *
 * <p>The same arrangement every bolt uses, and for the same reason - the structure is generated once
 * and only its brightness and visible extent change, as continuous functions of
 * {@code age + partialTick}, so nothing is rebuilt while it is on screen.
 */
public final class ActiveLuminousEvent {
    private final LuminousProfile profile;
    private final LuminousStructure structure;
    private final LightningEnvelope envelope;
    private final float energy;
    private int age;

    public ActiveLuminousEvent(LuminousProfile profile, LuminousStructure structure, long seed, float energy) {
        this.profile = profile;
        this.structure = structure;
        this.energy = energy;
        this.envelope = new LightningEnvelope(seed, profile.envelope());
    }

    public void tick() { age++; }

    public boolean alive() { return age < envelope.duration(); }

    /** Output now, before any per-segment or per-halo scaling. */
    public float brightness(float partialTick, boolean reducedFlashing) {
        return envelope.brightness(age + partialTick, false, reducedFlashing) * energy;
    }

    /**
     * How far the structure has developed, {@code 0..1}.
     *
     * <p>For a sprite this is over almost before it starts; for a jet it is the climb itself.
     */
    public float reveal(float partialTick) { return envelope.propagation(age + partialTick); }

    public boolean visible(LightningSegment segment, float partialTick) {
        return segment.alongStart() <= reveal(partialTick);
    }

    public boolean visible(LuminousStructure.LuminousGlow glow, float partialTick) {
        return glow.along() <= reveal(partialTick);
    }

    public LuminousProfile profile() { return profile; }

    public LuminousStructure structure() { return structure; }

    public Vec3d anchor() { return structure.anchor(); }

    public int age() { return age; }
}
