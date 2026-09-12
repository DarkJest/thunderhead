package dev.tempestfx.api;

/** A discharge's physical category. Vanilla entities are ground flashes; cloud flashes are explicit. */
public enum LightningKind {
    NEGATIVE_GROUND(true), POSITIVE_GROUND(true), INTRACLOUD(false), INTERCLOUD(false);
    private final boolean ground;
    LightningKind(boolean ground) { this.ground = ground; }
    public boolean contactsGround() { return ground; }
}
