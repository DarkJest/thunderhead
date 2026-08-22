# Thunderhead API

Thunderhead lets other mods raise their own lightning and react to lightning it draws. Three methods,
no packets, no registration ceremony.

- [Read this first](#read-this-first)
- [Adding the dependency](#adding-the-dependency)
- [Raising a strike](#raising-a-strike)
- [Styling a strike](#styling-a-strike)
- [Thunder](#thunder)
- [Particles](#particles)
- [Rolling thunder on its own](#rolling-thunder-on-its-own)
- [Ball lightning](#ball-lightning)
- [Listening for strikes](#listening-for-strikes)
- [Multiplayer](#multiplayer)
- [What is stable](#what-is-stable)

## Read this first

**The API is client-side.** A strike you raise is drawn on the client that raised it and nowhere
else — there is no packet behind it. Call it on a server and nothing happens, anywhere. See
[Multiplayer](#multiplayer) for the two ways to make one strike visible to everybody.

**Thunderhead may not be installed.** Every method degrades to a no-op instead of throwing, so a
missing mod cannot crash yours — but only if you compile against it correctly, which means
`compileOnly`, not `implementation`.

**Effects are visual.** The API applies no damage, moves no blocks and changes no gameplay. If you
want a strike to hurt something, hurt it yourself on the server and raise the visual on the clients.

## Adding the dependency

You do not need to run a Maven. The store you publish on exposes one.

**CurseForge** — where Thunderhead is published today:

```gradle
repositories {
    maven { url = "https://cursemaven.com" }
}

dependencies {
    compileOnly "curse.maven:thunderhead-<projectId>:<fileId>"
}
```

Both numbers are on the CurseForge page: the project id in the sidebar, the file id in the URL of
the file you want.

**Modrinth** — nicer coordinates, but Thunderhead is not on Modrinth yet, so this does not resolve
today. It will work unchanged once it is published there:

```gradle
repositories { maven { url = "https://api.modrinth.com/maven" } }
dependencies { compileOnly "maven.modrinth:thunderhead:1.1.0" }
```

`compileOnly`, deliberately. Your mod compiles against Thunderhead and runs with or without it. Then
declare it as an *optional* dependency so the loader does not demand it:

```json
// fabric.mod.json
"suggests": { "tempestfx": "*" }
```

```toml
# neoforge.mods.toml
[[dependencies.yourmod]]
modId = "tempestfx"
type = "optional"
versionRange = "[1.1.0,)"
ordering = "AFTER"
side = "CLIENT"
```

> The mod id is `tempestfx`, not `thunderhead`. Thunderhead is the name on the store; the id has
> been `tempestfx` since the first release and will not change, because renaming it would break
> every config file and resource path in existing installs.

Guard your calls so a missing mod is not a `NoClassDefFoundError`:

```java
public final class ThunderheadCompat {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("tempestfx");

    public static void strike(Vec3d where, long seed) {
        if (!LOADED) return;         // keep the API types out of the caller's frames
        Impl.strike(where, seed);
    }

    private static final class Impl {   // only loaded once LOADED is true
        static void strike(Vec3d where, long seed) { /* real call */ }
    }
}
```

## Raising a strike

```java
import dev.tempestfx.api.LightningEffect;
import dev.tempestfx.api.TempestFxApi;
import dev.tempestfx.math.Vec3d;

TempestFxApi.triggerLightning(LightningEffect.builder()
    .position(new Vec3d(x, y, z))   // where the channel terminates
    .seed(seed)                     // same seed, same bolt, every time
    .intensity(1.0f)                // brightness, clamped to 4
    .build());
```

Returns `true` if the effect was accepted, `false` if the client is not up yet. Safe from any
thread: an off-thread call is parked and delivered on the next client tick.

`position` is where the bolt *lands*, not where it starts — the channel is generated upward from it
into the cloud base. Drop it onto the ground yourself if you want it to terminate on terrain rather
than in mid-air.

`seed` decides the channel shape, the flicker, the thunder profile and the particle scatter. The
same seed always produces the same bolt. Pass something meaningful and reproducible, not a random
number, unless you specifically want a different bolt on every client.

### Angle and length

By default the channel hangs from the cloud base with a lean taken from the seed. Pass an origin and
you decide both outright:

```java
TempestFxApi.triggerLightning(LightningEffect.builder()
    .position(groundPoint)                        // where it terminates
    .origin(groundPoint.add(40, 60, -25))         // where it comes from
    .seed(seed)
    .build());
```

The geometry runs between the two points whatever their relation, so this is not limited to
"steeper" or "shallower": a channel can lean hard, run almost flat between two towers, or rise from
the ground upward. Displacement is scaled by the channel that actually exists, so a short slanted
bolt does not wander like a tall one.

Leave `origin` out for a normal cloud-to-ground strike.

### Discharge type

A flash is one of five things, and which one decides its geometry, its timeline, its colour, how far
it lights the cloud around it and how hard it thunders:

```java
import dev.tempestfx.api.DischargeType;

TempestFxApi.triggerLightning(LightningEffect.builder()
    .position(groundPoint)
    .type(DischargeType.POSITIVE_CLOUD_TO_GROUND)   // the rare superbolt
    .seed(seed)
    .build());
```

| Type | What you get |
| --- | --- |
| `NEGATIVE_CLOUD_TO_GROUND` | the ordinary strike: heavy branching, several return strokes |
| `POSITIVE_CLOUD_TO_GROUND` | a superbolt: wide, violet, barely branched, one dominant stroke that holds |
| `CLOUD_TO_CLOUD` | a horizontal channel through the storm; never reaches the ground |
| `INTRACLOUD` | buried in cloud: almost no exposed channel, the cloud pulses instead |
| `MEGAFLASH` | kilometre-scale, propagating for over a second |

The aerial types expect an `origin` and a `position` that are both up at cloud height; give one a
ground `position` and you get a very strange bolt, which is on you.

Leave `type` unset and Thunderhead chooses from the seed, the way it does for a vanilla bolt. That
is what an integration wanting "a lightning bolt" should do — the player's superbolt rate then
applies as they configured it.

`LightningStrikeFxEvent.dischargeType()` reports what a strike turned out to be, and never returns
`null`.

### Optional extras

| Builder method | What it does |
| --- | --- |
| `.origin(Vec3d)` | where the channel starts — fixes its angle and length |
| `.type(DischargeType)` | which archetype to draw — see above |
| `.environment(LightningEnvironment)` | ground colour, wetness, water, foliage — drives which particles are emitted |
| `.target(StrikeTarget)` | mark the strike as landing on an entity; a player target leaves the ash scar |
| `.style(LightningStyle)` | see below |

## Styling a strike

```java
import dev.tempestfx.api.LightningStyle;

TempestFxApi.triggerLightning(LightningEffect.builder()
    .position(pos)
    .seed(seed)
    .style(LightningStyle.builder()
        .thickness(1.8f)      // fatter channel
        .branchiness(1.4f)    // more forks
        .scale(0.7f)          // shorter reach into the sky
        .coldTint(0.3f)       // warmer, closer to white
        .build())
    .build());
```

| Field | Range | Neutral |
| --- | --- | --- |
| `thickness` | 0.25 – 4 | 1 |
| `branchiness` | 0 – 3 | 1 |
| `scale` | 0.25 – 3 | 1 |
| `coldTint` | 0 – 2 | 1 |
| `coreColor` | `0xRRGGBB` | automatic (near-white) |
| `glowColor` | `0xRRGGBB` | automatic (from `coldTint`) |

`coreColor` is the conducting core, `glowColor` the halo and sheath around it; `.color(rgb)` sets
both. The sheath is drawn as the same hue, brightened, so a saturated colour whitens toward the
centre instead of shifting hue. Anything outside 24-bit RGB means "not specified" rather than an
error.

```java
.style(LightningStyle.builder().coreColor(0xFFF0B0).glowColor(0xFF5A1E).build())   // furnace orange
.style(LightningStyle.builder().color(0x7FFF6A).build())                           // sickly green
```

**A styled strike is drawn to that style, independent of the player's lightning settings.** The
player's thickness, branch count, scale and cold tint govern the strikes Thunderhead raises itself —
vanilla bolts, return strokes, the distant storm front. They are not consulted for an effect you
asked for. Two players with opposite settings see your set piece the same way.

Values are relative to Thunderhead's stock look, so `1` everywhere is exactly a normal bolt.

**Leave the style out entirely** if you just want a bolt somewhere and would rather it match whatever
the player has configured. That is the default, and it is the right choice for most integrations —
reserve an explicit style for effects whose appearance is part of your content.

Out-of-range and non-finite values are clamped, not rejected: a bad number from an integration
should not kill the storm.

**Accessibility is not stylable.** Nothing in `LightningStyle` can raise brightness, change the
flicker or reach the reduced-flashing mode, and no style is consulted when those are applied. That
is deliberate — those settings exist for people who need them, and an integration must not be able
to route around them, by accident or otherwise. The same goes for gameplay: a style is paint.

## Thunder

```java
.thunder(new ThunderOptions(ThunderVoice.CLOSE_HEAVY, 1.5f, 0))   // clip, volume, delay in ticks
.thunder(ThunderOptions.of(ThunderVoice.DISTANT_THUNDER))         // clip only, rest automatic
.thunder(ThunderOptions.silent())                                 // seen and not heard
```

| Field | Meaning |
| --- | --- |
| `voice` | `AUTO` picks by distance the way the mod does; `SILENT` plays nothing; otherwise one named clip |
| `volume` | factor on the strike's own loudness, `0..2` |
| `delayTicks` | `-1` for distance ÷ speed of sound, which is what the mod does; otherwise your own timing, up to 400 |

A named voice is deliberately **one** layer. The automatic path stacks a crack, a body and a tail to
build a strike that sounds right at that range; asking for "the distant one" and getting three clips
is not what anyone means.

Two things you cannot override, on purpose: the player's master thunder volume, which is applied
after yours, and their "realistic sound delay" setting — if they turned delay off, your
`delayTicks` is ignored and the sound arrives with the flash. You are asking for a timing, not for
the right to overrule an audio preference.

## Particles

```java
.particles(ParticleFamily.SPARKS, ParticleFamily.MICRO_ARCS)   // electricity, nothing else
```

Families: `SPARKS`, `MICRO_ARCS`, `SMOKE`, `STEAM`, `DUST`, `DEBRIS`, `ASH`, `EMBERS`,
`WATER_SPRAY`. Leave it out for everything the ground calls for.

This is a **filter**. Asking for `WATER_SPRAY` on dry stone still produces nothing, because what the
ground is made of decides what can come off it, and the player's own particle toggles and budget
apply on top. A selection can narrow what is emitted; it can never widen it past what they allowed.

## Rolling thunder on its own

```java
TempestFxApi.triggerThunderRoll(new ThunderRoll(stormCentre, seed, 160, 60));
TempestFxApi.triggerThunderRoll(ThunderRoll.at(stormCentre, seed));   // let the seed decide
```

The real thing: a dozen independent thunder layers with their own bearing, delay, pitch and
envelope, and a horizon of distant forked channels behind them. Not a long clip.

| Parameter | Meaning |
| --- | --- |
| `position` | where the storm is — bearing and distance come off this |
| `seed` | the same seed rolls the same event |
| `durationTicks` | `0` to let the seed choose, otherwise up to 320 (16 seconds) |
| `flashesPerSecond` | `0` to let the seed choose, otherwise up to 100 distant channels a second |

It runs for seconds, cannot be stopped once started, and only one runs at a time. A set piece, not
something to raise per tick.

## Ball lightning

Ball lightning is a real entity, so it lives on the **server** side and has its own entry point:

```java
import dev.tempestfx.api.TempestFxServerApi;

TempestFxServerApi.spawnBallLightning(serverLevel, position, seed, 0.7f, 200);
```

Radius is clamped to 0.1–3 blocks, lifetime to 1–2400 ticks. Returns the entity, or `null` if the
entity type is not registered.

Call it from server-side code with a real `ServerLevel` — from a client thread it is a race, whatever
else is true. Contact damage, ground scorching and the rest follow the **server's** configuration,
not yours: a mod placing a sphere is placing the mod's sphere, and an admin who turned contact
damage off meant it.

This is the one part of the API that needs Thunderhead on the server. Everything else is client-side
and works when joining a vanilla server.

## Listening for strikes

```java
AutoCloseable handle = TempestFxApi.onStrike(event -> {
    if (event.directPlayerHit()) {
        // event.position(), event.seed(), event.intensity(),
        // event.environment(), event.target(), event.stroke()
    }
});
```

Fires for **every** strike Thunderhead draws: vanilla bolts, the return strokes of a flash, and
effects raised through this API.

- Runs on the client thread, so you can read client state directly.
- Do not block. It sits between the strike and the flash.
- Throwing is caught and logged, never propagated into the renderer — but a listener that throws on
  every strike will fill somebody's log with your stack trace.
- Register whenever you like, including during your own initialisation, long before Thunderhead's
  client starts. Listeners survive the client being torn down and rebuilt, so you do not need to
  re-register when the player leaves a world.
- `event.stroke()` is `0` for the stroke the server actually spawned and `1..n` for the visual
  return strokes of the same flash. Check it if you only want one callback per real bolt.

Close the handle to unsubscribe. A listener that lives for the whole session can ignore it.

## Multiplayer

Thunderhead sends no custom packets. Every client derives the same storm from data the server
already replicates — the bolt's spawn position and its entity id, hashed by `StrikeSeed`.

An API strike is drawn only on the client that raised it. Two ways to give everybody the same one:

1. **Send your own packet** and call `triggerLightning` in its client handler, with the same seed on
   every client.
2. **Derive it from replicated state.** If the thing causing the strike is already an entity or a
   block everyone can see, hash its position and id into the seed and raise the strike from a
   client-side event. No packet, and it stays in sync for players who join later.

Whichever you pick, use the same seed everywhere or players will see differently shaped bolts for
what is supposed to be one strike.

## What is stable

Everything in `dev.tempestfx.api` keeps its shape within a major version:

- `TempestFxApi` — `triggerLightning`, `triggerThunderRoll`, `onStrike`, `isAvailable`
- `TempestFxServerApi` — `spawnBallLightning`
- `LightningEffect`, `LightningStyle`, `StrikeOptions`, `LightningStrikeFxEvent`,
  `LightningEnvironment`, `StrikeTarget`, `ThunderOptions`, `ThunderVoice`, `ThunderRoll`,
  `ParticleFamily`, `DischargeType`
- `dev.tempestfx.math.Vec3d` and `StrikeSeed`, because the API takes and returns them

Everything else is implementation detail and may change in any release. That explicitly includes
`TempestFxApi.Internal`, which is the mod's own wiring: calling `install`, `uninstall` or
`fireStrike` from another mod will break effects for the player and is not supported.

Found a gap? Open an issue — the API is small on purpose, and it grows when somebody has an actual
integration that needs something.
