# Thunderhead 2.0 API

There are three separate operations: a **local client visual**, a **synchronized server visual**, and a **real server strike**. Only the last changes gameplay. Compile against the matching Thunderhead loader jar as a compile-only dependency; do not shade the mod into another jar.

The mod ID/resource namespace remains `tempestfx`. Guard optional integrations with your loader's mod-presence check before loading classes that reference this API. `isAvailable()` describes an initialized client, not whether an absent Java class can be linked.

## Local client visuals

```java
TempestFxApi.triggerLightning(LightningEffect.builder()
    .position(new Vec3d(100, 70, 100))
    .origin(new Vec3d(80, 192, 110))
    .seed(12345L)
    .kind(LightningKind.NEGATIVE_GROUND)
    .intensity(1f)
    .build());
```

This affects only the local client and sends no gameplay packet. The boolean result means submitted for processing; world availability, settings and bounded budgets may prevent display. Calls before the client is installed return false. Off-thread lightning calls are queued for the client thread.

Kinds: `NEGATIVE_GROUND`, `POSITIVE_GROUND`, `INTRACLOUD`, `INTERCLOUD`. For cloud flashes, supply both cloud endpoints. Cloud events do not generate ground aftermath. A seed controls geometry, not event identity: repeated calls with the same seed remain separate events.

Optional builder controls:

- `style(LightningStyle)` supplies thickness, branchiness, scale and explicit core/glow colors. `LightningStyle.builder().color(0xff8844).build()` is a colored channel.
- `thunder(ThunderOptions)` selects a voice, volume multiplier and an optional fixed delay. `ThunderOptions.silent()` suppresses thunder.
- `particles(ParticleFamily...)` restricts impact families.
- `environment(LightningEnvironment)` and `target(StrikeTarget)` supply explicit environmental/target context.

Accessibility overrides presentation. In 2.0 return strokes reuse the original geometry, source, target and overrides. An API-only visual channel may be unavailable under a shader pack if the mod's own GLSL programs fail; true vanilla lightning entities retain their vanilla renderer in that situation.

`TempestFxApi.triggerThunderRoll(ThunderRoll)` is a local cinematic audio effect. It does not create server gameplay. Realistic mode does not turn these audio events into decorative distant bolt walls.

## Observing strikes

```java
AutoCloseable subscription = TempestFxApi.onStrike(event -> {
    // event.seed(), position(), origin(), kind(), stroke(), intensity(), options()
});
```

Close the handle to unsubscribe. Keep callbacks short. Exceptions from an integration listener are logged and contained. Primary accepted events and future rendered return contacts are reported; old contact effects omitted after a late network arrival are not replayed. Reduced flashing may omit repeat contacts or suppress additional visual flashes while still reporting accepted primary events.

## Synchronized server visuals

Call on the **server thread**:

```java
long eventId = TempestFxServerApi.flash(level, LightningKind.INTRACLOUD,
    new Vec3d(0, 192, 0), new Vec3d(100, 180, 20), 12345L);
```

This broadcasts a visual-only event through the optional storm protocol. It returns the event ID, or -1 when the bounded publication budget rejects it. Only clients that support the protocol and are within the configured subscription radius receive it. The method does not apply damage, start fires, or load target chunks. Invalid kinds/coordinates are rejected.

Distinct IDs remain distinct even when seed and endpoints repeat. Re-sending the same event ID is deduplicated. Native entity correlation is a separate expiring entity-ID mechanism; it does not treat an API geometry seed as an identity.

The packet carries the version, ID, seed, native entity correlation when applicable, start tick, dimension, kind, endpoints, intensity and repeat cap. Late clients seek the existing event and schedule only acoustic arrivals that have not passed them. Wrong-dimension, stale and far-future events are ignored. Presentation can still differ with local quality/accessibility settings.

## Real server strikes

```java
LightningBolt bolt = TempestFxServerApi.strike(level, targetBlockPos);
```

Call on the server thread. An unloaded target returns null. A successful call creates a real vanilla entity with normal lightning gameplay and server-configured Thunderhead additions. This is not a cosmetic API. Ordinary vanilla lightning remains responsible for its direct damage, ignition, rods, copper and conversions.

`spawnBallLightning(level, position, seed, radius, lifetime)` remains available on the server thread. It explicitly creates an experimental entity; passive spawning from natural strikes is independently controlled by server configuration.

## Compatibility contract

The stable surface is `dev.tempestfx.api`: client entry points, builders/value types, `LightningKind`, and the server entry points above. Classes outside this package and `TempestFxApi.Internal` are implementation details. The previous four-argument `StrikeOptions` constructor remains available and defaults to negative ground lightning.

Protocol/model version 2 uses the `tempestfx:storm_v2` channel. A future incompatible wire/model change must use a new version/channel. No client-to-server damage or storm-control payload is registered. Clients without the protocol continue using replicated vanilla entities; they do not receive the additional synchronized cloud simulation.
