# Thunderhead architecture

## Module boundaries

```text
tempestfx
├── common     algorithms, API, config, simulation, rendering, gameplay, resources, tests
├── fabric     Fabric bootstrap: registries, server hooks, client hooks, commands
└── neoforge   NeoForge bootstrap: registries, server hooks, client hooks, commands
```

Both loader projects compile the same `common/src/main` source and resource directories against
official Mojang names. There is no runtime common jar and no copied implementation: the loader
modules only adapt registries, lifecycle events, config paths, camera access and spatial sound.

Each loader has two entry points, one that runs on both sides and one that only runs on a client.
The registry half has to exist on a dedicated server as well, otherwise ball lightning could not be
spawned or replicated.

Inside `common` the layering is:

```text
math       Vec3d, Bounds3d, FxMath, Noise, StrikeSeed        pure, no Minecraft types
lightning  geometry, generation config, envelope, sequence    pure, no Minecraft types
           discharge archetypes and their parameter profiles
storm      electrical state and the ambient event planner     pure, no Minecraft types
sky        sprite and jet morphology above the storm          pure, no Minecraft types
particle   particle model, pool-backed system, emitters       pure, no Minecraft types
effect     live simulation state for every visual subsystem   pure, no Minecraft types
audio      thunder scheduling and engine-accurate loudness    only ResourceLocation
config     user configuration and validation                  pure
api        the event, the builder, the public entry point     pure
entity     ball lightning entity and its motion model         motion model is pure
server     gameplay: near-miss damage, ball lightning spawns  Minecraft server
render     passes, programs, all vertex emission              Minecraft rendering
  .gl      compiled programs and the GPU state guard         raw OpenGL
  .composite  own framebuffer and the final composite        raw OpenGL
client     orchestration, ingest, mixin hooks                 Minecraft client
world      surface sampling                                   Minecraft world
mixin      four client hooks and one shared accessor          Minecraft internals
```

Everything above `entity` in that list is unit-testable without a game instance, which is why 244
tests cover geometry, envelopes, flash sequences, seeds, noise, audio maths, particle budgets,
discharge behaviour, imprint lifetime, ball lightning motion, near-miss damage, particle lighting,
storm charge, ambient scheduling, sprite and jet morphology, the public API and config validation.

## Client and server responsibilities

The split is deliberate and visible in the package layout.

**Client** draws and never decides anything. It reads server-replicated state — bolt positions,
entity positions, weather, the ball lightning entity — and produces geometry, particles, decals,
sound and screen effects. It applies no damage, changes no blocks, and sends no packets.

**Server** decides and never draws. It reacts to bolts vanilla already spawned by adding damage
outside vanilla's own damage box and, rarely, by spawning a ball lightning entity. It does not spawn
bolts, does not alter vanilla's damage, fire, lightning rods, copper or mob conversions, and does
nothing at all when its features are switched off.

Two details keep the additive damage genuinely additive. Vanilla's box is
`x±3, y-3..y+9, z±3` — a box, and not a symmetric one — so `NearMissDamage.insideVanillaBox` tests
that shape rather than a sphere approximating it; a target five blocks above a strike is outside a
3-block sphere and squarely inside vanilla's reach. And a bolt vanilla flagged `visualOnly` is
scenery: vanilla skips its entire damage block for it, which is how the skeleton horse trap flashes
harmlessly, so Thunderhead skips gameplay for it too. Clients still draw the flash.

The split is checked, not just intended: the transitive closure of both loader bootstraps is 18
classes, none of which references `net.minecraft.client` or `com.mojang.blaze3d`. A dedicated server
never loads a rendering class. The only mixin that is not client-side is a one-method accessor for
vanilla's `visualOnly` flag, which has a setter and no getter.

### Synchronisation without packets

There is no custom protocol. Consistency comes from deriving decisions rather than transmitting
them: `StrikeSeed.of(x, y, z, entityId)` hashes the replicated spawn position (quantised to 1/16
block) and the bolt's entity id into the single seed every subsystem branches from. Both values
arrive verbatim in the spawn packet and are known to the server, so the flash a player sees and the
damage the server applies come from the same number.

The entity id matters specifically: an earlier revision salted with the world time, but a client's
tick counter can drift between the periodic time packets, which would give two players slightly
different bolts for one strike.

`LightningBolt.seed` is explicitly not used — vanilla assigns it from the entity's client-side
random, so it differs per client and per rejoin.

Ball lightning sidesteps the question entirely by being a real entity: vanilla replication keeps its
position, lifetime and damage in agreement for free. Three things had to be replicated explicitly on
top of that, because vanilla has no opinion about them:

- **The visual seed.** Every wobble of the shell, every shed spark and the pulse of the radius comes
  out of one number. Rolled from the client's own random, two players standing side by side would
  watch two different spheres and neither would match the strike that produced it. It rides in
  `SynchedEntityData`, and a sphere summoned by hand rather than by a strike derives one from its
  replicated position and entity id instead.
- **The age.** `tickCount` restarts at zero when an entity enters a client's view, so a player who
  walks up to a sphere halfway through its life would watch it born again — full brightness, full
  lifetime, a fade arriving seconds late. The server restates its age once a second and each client
  holds the offset onto its own counter.
- **The motion between updates.** `Entity#lerpTo` snaps, which is right for a falling item and wrong
  for a slow sphere hovering at head height; the tracker sends a position every other tick and the
  snap reads as a stutter. `BallLightning` interpolates over the remaining steps the way living
  entities do.

Client-side decisions that could go two ways are resolved deterministically rather than by iteration
order, because the order a level lists its entities is not the same on every client: the struck
entity breaks exact ties on entity id, and the discharge effect sorts by distance before applying
its twelve-target cap instead of taking whichever twelve came first.

## Strike lifecycle

1. `ClientLevelMixin` catches `ClientLevel#addEntity` — the exact tick the server-spawned bolt
   appears. No per-tick entity scan is involved.
2. `StrikeIngest` derives the seed, samples the surface once through
   `LightningEnvironmentResolver`, and resolves the struck entity by proximity to the bolt's
   position (vanilla moves the bolt onto its target's block position, so this recovers the server's
   own decision).
3. The immutable `LightningStrikeFxEvent` is published to `FxEventBus`.
4. Independent subscribers react: bolt geometry and shockwave, budgeted particles, exposure flash,
   camera impulse, transient light, sky flash, entity discharges, ash imprint, thunder, and the
   return-stroke plan.

In parallel, on the server, `EntityJoinLevelEvent` / `ServerEntityEvents.ENTITY_LOAD` gives
`TempestFxServer` the same bolt, from which it applies near-miss damage and rolls for ball lightning.

`SoundEngineMixin` drops exactly `entity.lightning_bolt.thunder` and `entity.lightning_bolt.impact`
while custom thunder is enabled. Both are started client-side by `LightningBolt#tick`, so suppressing
them costs nothing in audible range. Rain and every unrelated weather sound are untouched.

External mods publish the same event through
`TempestFxApi.triggerLightning(LightningEffect.builder()...)`. The API is thread-safe: events raised
off the client thread are queued and delivered at the start of the next tick, because every subsystem
owns mutable simulation state.

## Multi-stroke flashes

What people call "a lightning bolt" is usually several discharges down the same ionised channel: a
bright first stroke, then two to four more at 30–90 ms intervals, each weaker and each landing within
a few metres of the first as the channel decays.

`StrikeSequence` plans that as a pure function of the primary seed, and `StrikeSequenceSystem`
releases the strokes on schedule. Only stroke 0 is ever expanded, which is what stops the expansion
from chaining forever. Because the plan comes from the seed, every client produces the same flash
with no server involvement, so the feature keeps working on a vanilla server.

Strokes are released as raw parameters rather than finished events: each one lands a couple of metres
away and therefore needs its own surface sample, which only the client can do.

## Discharge archetypes

A flash is one of five things, and which one it is decides everything about it:

```text
NEGATIVE_CLOUD_TO_GROUND   the ordinary strike; every scale below is 1 by definition
POSITIVE_CLOUD_TO_GROUND   the superbolt: wide, violet, barely branched, one dominant stroke
CLOUD_TO_CLOUD             horizontal, no ground contact, forks spreading flat
INTRACLOUD                 buried: almost no exposed channel, the cloud does the work
MEGAFLASH                  kilometre-scale, propagating for more than a second
```

`DischargeProfile` holds the numbers — channel width, fork probability, wander amplitude, fork
lean, exposed opacity, colour warmth, cloud glow, thunder impulse and the timeline — and
`DischargeProfiles` is the one table that maps an archetype to its profile. Adding an archetype is a
row there; nothing downstream branches on the type. A positive flash is therefore not
`normal × 1.5`: its channel is over twice as wide, carries roughly a fifth of the branching, holds
for thirteen ticks instead of eight with a single re-strike instead of three, and its halo is violet
rather than cyan. Those are five independent differences and any one of them is visible in a still
frame.

### Two constraints a profile may not violate

Both were learned the expensive way, and both are now regression-tested in `ChannelReadabilityTest`,
which measures them on real generated geometry rather than trusting the numbers.

**A segment must stay several times longer than the ribbon along it is wide.** `RibbonRenderer`
turns each segment into its own camera-facing quad; once the quads are as wide as they are long they
overlap at sharp angles instead of joining, and an additive pass renders that as torn lumps. So
widening a channel means removing a generation from it — which is what `generationsDelta` is for —
and a wide channel wanders *less* than a thin one, not more. The first cut of the positive profile
widened by 2.35 without touching the subdivision, dropped the ratio from 5.0 to 2.9, and looked
broken. `AerialChannelStrategy` had the same fault more severely: giving every leg the full
generation count chopped a 260-block channel into half-block segments, so it now subdivides by leg
length instead.

**A bare trunk shows its joints.** Two segments meeting at an angle do not share an edge — the outer
corner opens by `halfWidth × tan(kink / 2)`. An ordinary flash hides that among its forks. A positive
one has almost no forks, so the same seam is conspicuous, and the lever is `roughnessScale`: the late
generations set the kink angle, the early ones set the shape, so lowering the decay straightens the
channel at pixel scale without touching its silhouette. Measured at thirty blocks, the archetypes now
sit at 0.4 to 1.8 pixels of seam against the ordinary flash's 1.3.

The archetype is decided once, at ingest, by `DischargeSelector` from the same strike seed every
client already derives geometry and thunder from, and rides on the event in `StrikeOptions`. No
packet, no disagreement between players, and no subsystem rolling for it a second time.
`LightningEnvelope` takes the profile's `EnvelopeProfile` rather than the constants it used to own,
so duration, propagation, decay and re-strikes are properties of the archetype.

## The storm as a system

Vanilla only ever tells a client about lightning that lands. A real storm spends most of its life
discharging inside and between its own clouds, and none of that has a server-side counterpart at
all — so it is planned on the client:

```text
StormSample            what the level looks like this tick, with no Minecraft types attached
   ↓
StormElectricState     charge that builds over seconds and bleeds away over rather longer
   ↓
LightningEventPlanner  when something happens, which archetype, and where in the storm
   ↓
AmbientDischarge       an immutable description; no geometry, no sound, no light
   ↓
SkyDischargeSystem     the channel               CloudIlluminationSystem   the lit cloud
                                                 ThunderSystem             the sound
```

Consistency between players is deliberately not enforced here. A ground strike must look the same to
everyone because the server applies damage for it; an intracloud pulse four hundred blocks away is
ambience, and a packet to synchronise it would buy nothing a player could ever notice. The storm's
bearing is derived from the replicated world clock, so the fronts do at least face the same way.

`SkyDischargeSystem` is separate from `EffectManager` on purpose: an aerial discharge has no impact,
no target and no surface, and giving it its own bounded list keeps "is this one real" out of the
strike path and lets the two be budgeted independently. `AerialChannelStrategy` composes the same
displacement strategy the ground bolts use over a route of two to six legs, then remaps each
segment's `along` onto the whole run, so the leader crosses the sky once instead of restarting at
every joint — and the channel texture, fork weighting and width ladder stay in one place.

## Above the storm

Sprites and jets are not lightning and are not modelled as it. Lightning is a conducting channel in
dense air; a sprite is a glow discharge in air thin enough that a whole region lights at once, which
is why it has no trunk, no attachment and no thunder.

```text
LightningStrikeFxEvent (positive CG)   AmbientDischarge (megaflash)   AmbientDischarge (any)
                    ↓                            ↓                             ↓
              TransientLuminousSystem  — rolls, rarely, and places the result
                    ↓
              LuminousStructures  — procedural morphology, once, from a seed
                    ↓
              ActiveLuminousEvent  — structure + LightningEnvelope
                    ↓
              LuminousEventRenderer  — filaments into BOLT, halos into ATMOSPHERE
```

`LuminousStructures` deliberately does not use midpoint displacement. Subdivision discovers a
wandering channel, which is right for a bolt and wrong here: a sprite is a *curtain* of roughly
parallel columns with tendrils combed downward and outward, and a jet is a *cone* that splits as it
climbs. Both are described directly by their morphology, and the result is an order of magnitude
cheaper than a bolt — a couple of hundred segments against several thousand.

Three decisions carry the look. The colour runs *along* the structure rather than being flat, which
is the strongest single cue that it is not red lightning. There is no white core — two soft layers,
not the bolt's three — because a hot conducting centre is exactly what a glow discharge does not
have. And most of the apparent brightness comes from the diffuse halos rather than the filaments,
because that is what survives four hundred blocks of sky.

They are consequences rather than events of their own: a sprite is raised over the discharge that
caused it, and only when that discharge is far enough away to be looked at rather than stood under —
from directly beneath a storm the cloud deck is in the way, which is true of the real thing and of
Minecraft's cloud layer as well. `LightningEnvelope` supplies the timeline for both, so the sprite
that is present in under a tick and the jet that takes nine to climb come out of the same tested
maths as every bolt.

## Cloud illumination

`CloudLightSource` is a pulsing emissive region, not a light: nothing is relit, no block light is
written and no volume is marched. `CloudIlluminationRenderer` draws four warped billboards per
region through the curl-warped puff program, which is what makes a lit region a torn irregular
volume rather than a soft circle. The per-quad noise offset is smuggled in as a hair of variation in
the red and blue channels — the program already folds those into its noise lookup, and the shift is
far too small to see as colour — so the effect costs one additive pass, no extra attachment, no
read-back and no shader of its own. It works identically with and without a shader pack for the same
reason everything else does: it is drawn into the mod's own framebuffer.

## The strike lifecycle

A close strike used to be a channel that appeared. It is now a sequence, and each stage is a pure
function of time that the renderer samples rather than a state machine anything has to drive:

```text
leader descends in steps        propagation(t), stepped
        ↓
objects below throw streamers   Streamer.growth(leader)
        ↓
one connects: attachment        StrikeAttachment.point(), and the channel is built to end there
        ↓
return stroke climbs            returnStrokeBoost(along, t)
        ↓
the channel decays              brightness(t)
```

**Stepping is a deliberate dramatisation.** A real stepped leader completes in tens of milliseconds,
which at any frame rate is one or two frames, so the descent is given a couple of ticks and eleven
steps are spaced to be legible inside it. Each step advances over the first 45% of its slot and holds
for the rest; the pause is the feature, and a leader that eased between steps would just be a slower
smooth reveal. A positive flash steps more coarsely, six times, because its leader is less stepped.

**The return stroke is what gives the strike a direction.** Once the leader attaches, the current
actually flows the other way, and a bright front climbs the finished channel in about a third of the
time the descent took. Before it arrives a segment is only an ionised trail; after it passes the
whole channel is lit and simply decays. Stepping and the return stroke are coupled on purpose — an
aerial discharge has no ground contact, so it gets neither.

**Streamers decide where the bolt lands.** `StreamerScanner` reads the heightmap over a seven-block
radius once per strike, on the game thread, classifying each column top as rod, metal or terrain;
`AttachmentPlanner` weights them by height and conductivity, picks a winner — usually the strongest,
occasionally the runner-up, because a leader is already committed to an approach by then — and builds
every streamer's geometry. The channel is then generated to end at the point where the winner met it,
which is what makes a bolt terminate on a rod's tip rather than beside it. Ties break on position, not
on iteration order, because a level does not list blocks the same way on every client.

The whole thing is skipped for anything it cannot apply to: an aerial discharge has no ground, and a
strike beyond seventy blocks would be spending block lookups on a sub-pixel smudge.

Nothing here adds a draw call. Streamers go into the additive electricity batch the channel already
uses and the connection flash into the glow batch, so they reach the screen through the same
framebuffer and the same composite as everything else — and behave identically with a shader pack.

## Procedural geometry

`MidpointDisplacementStrategy` starts from a start/end pair and subdivides in place inside primitive
arrays. Each generation inserts a midpoint offset in the local orthonormal frame of its parent
segment; amplitude decays by `roughness` and every offset is additionally clamped to a fraction of
the segment it subdivides, so the channel cannot knot into itself.

Fork candidates are sampled with a fixed stride and weighted by
`(0.3 + 0.95·sin(π·t))·(0.6 + 0.6·t)`, which peaks in the middle and lower part of the channel where
real stepped leaders branch most. Each fork gets its own derived seed, angle, direction bias, jitter,
length, generation count and brightness decay. Short micro stubs texture the main channel without
adding recursion depth. A hard `maxSegments` budget bounds the whole tree.

Width follows an explicit ladder rather than the geometric `branchDecay` — trunk, then 52%, 30%,
18%, 11% of it per level of depth, with micro stubs two rungs below their parent. A decay tuned for
displacement and brightness does not produce a readable hierarchy, and the hierarchy is the point:
a thick trunk with distinctly thinner limbs and thinner twigs again reads as lightning, where
near-identical strands read as a tangle. Each channel additionally swells toward the ground
(0.82→1.10) while forks taper to a point.

A flash is not only the strand that reaches the ground. `growCanopy` adds near-horizontal channels
spreading from the cloud base — the intracloud half of a real discharge, most of which never lands.
Without them a bolt reads as a thread hanging out of an empty sky; with them it occupies real sky,
which is what makes the scale land. Channel height, lean and canopy reach all scale with
`lightning.scale` and `lightning.skySpread`, and displacement amplitude scales with channel height so
a taller bolt wanders proportionally rather than stretching into a wire.

Every segment carries `alongStart`/`alongEnd`, its normalised distance from the cloud. That is what
lets the renderer reveal the channel as the leader travels, without regenerating anything.

Generation happens once per strike. No random number is drawn while rendering.

## Ground alignment

Impact effects are anchored to the surface, not to the channel end, and the two are not always the
same. `LightningEnvironmentResolver` walks past anything without collision — tall grass, flowers,
torches — reports the real top face of whatever stops it, including partial blocks such as snow
layers and slabs, and finally clamps the result to the bolt's own termination height. Ground effects
therefore can never float above the channel that made them, and sparks leave the same surface the
dust and debris do.

## Time behaviour

`LightningEnvelope` is a pure function of `(seed, timeInTicks)`:

- `propagation` — leader position, full extent after 1.2 ticks
- `brightness` — exponential decay with one to three seeded re-strikes, plus continuous
  interpolated flicker so the channel keeps changing between game ticks at any frame rate
- `branchVisible` — per-segment deterministic mask sampled at 60 Hz, held on while the leader is
  still travelling so forks never blink during the strike itself

Reduced flashing replaces the whole thing with a single monotonic ramp and disables the multi-stroke
sequence outright.

## Rendering pipeline

Work is grouped by render type rather than by effect, so cost is a fixed number of draw calls no
matter how many bolts, particles or decals are alive:

| Pass | Blending | Contents |
| --- | --- | --- |
| `DECAL` / `DECAL_EMBER` | alpha / additive | ash imprint, cooling ember rim |
| `RIPPLE` | additive | expanding surface deformation, drawn by the shockwave shader |
| `CLOUD_LIGHT` | additive | cloud regions lit from the inside |
| `ATMOSPHERE` | additive | wide haze around a bright event |
| `FLASH` | additive | overexposed impact burst |
| `GLOW` | additive | impact flash column, transient light pools, emissive haloes |
| `BOLT` | additive | channels, forks, shockwave rings, sparks, micro arcs, spray, entity arcs |
| `PLASMA` | additive | ball lightning shell |
| `DEBRIS` | alpha | solid fragments |
| `SOFT` | alpha | dust, ash flakes |
| `SMOKE` | alpha | smoke, steam |

Every segment becomes a camera-facing quad, never a line primitive, and its `v` coordinate runs
across the ribbon width. The bundled `tempest_bolt` core shader turns that into a squared-parabola
cross-section, which is what makes a flat quad read as a volumetric channel and lets consecutive
segments join without a seam. `tempest_particle` applies a smoothstep density curve to the alpha
masks.

Six programs ship with the mod. `tempest_bolt` shapes every ribbon cross-section;
`tempest_particle` applies a density curve to the alpha masks; `tempest_shockwave` computes the
pressure ring procedurally — a gaussian leading edge, a trailing wake, and a curl-noise warp so the
front is never a perfect circle — and `tempest_smoke` warps the puff mask by the same curl map,
offsetting the lookup per particle using the tone the simulation randomised into the vertex colour,
so no two puffs sample the same noise. `tempest_solid` is flat colour for the debris fragments, and
`tempest_composite` belongs to the pass described under *Shader independence*: a fullscreen quad
emitted in clip space, declaring no matrices at all, so it needs nothing out of the global matrix
state and has nothing to restore.

They live in `assets/minecraft/shaders/core` under a `tempest_` prefix because vanilla resolves core
shader names in the `minecraft` namespace only; that makes the loading path identical on both
loaders. Every bundled mask stores its shape in the alpha channel with white RGB, and the two-sampler
programs bind that mask on `Sampler0` even though they compute their own shape — so Minecraft's stock
`position_tex_color` remains a correct fallback if the custom programs fail to load. `tempest_composite`
has no fallback and must not have one: no vanilla program can apply a premultiplied effect attachment
to the scene, so a failure to load it switches the compositor off rather than losing the effect.

Thunderhead owns a private `ByteBufferBuilder`, so a loader render callback cannot end an in-progress
effect batch. Each render type is acquired, filled and flushed before the next is requested, and
teardown restores depth writes, culling, blending and the blend function. The outer callback flushes
and restores in `finally`.

Ball lightning is a real entity, so it is offered to the client by the entity dispatcher rather than
by the effect manager, at a point in the frame the mod does not control. `BallLightningEntityRenderer`
therefore snapshots it into a `BallLightningDraw` and hands that to the world pass, where it is batched
by render type with everything else and composited the same way. If the world pass will not run — the
mod switched off — it draws the sphere itself through the vanilla entity buffer, because the entity
exists on the server either way and a summoned sphere must not be invisible. Both paths run the same
geometry code, parameterised by the origin to draw around.

## Shader independence

The mod owns its programs, its buffers, its GL state and its framebuffer, and applies the result to
the frame once the frame is finished. That is the whole compatibility story, and none of it names a
shader loader.

```text
world pass          own programs, own vertex buffers, own RGBA16F attachment
                    + the frame's own depth buffer, borrowed
                    rgb = premultiplied emissive colour, a = coverage after the depth test
   ↓
Minecraft, and any shader pack, finish the scene image
   ↓
composite pass      scene x (1 - coverage) + colour, in one fullscreen draw
```

Two things had to go, and both were points where the mod handed control to somebody else.

**The shader object.** Registering a core shader with the game means every shader loader has to decide
what to do about a program it does not recognise, and what it decides is not the mod's business to
predict — Iris, for one, disables colour and depth writes around an unknown `ShaderInstance` and
rebinds its own framebuffer inside `apply()`. Entirely reasonable from its side, and it silently
discards the geometry: this is precisely why lightning was invisible under a pack. `FxProgram`
compiles the same GLSL directly, so there is no shader object to recognise, override or skip, and the
mod's passes look like any other raw GL draw. `NativeFxBatchTarget` draws them through Minecraft's
`BufferBuilder` and `VertexBuffer` — plain buffer plumbing, no shader state — and sets blend, depth
and mask state with raw GL calls, because a pipeline that has locked the game's state cache would
otherwise swallow them.

**The framebuffer.** Drawing into whatever the game had bound means drawing into a pack's G-buffers,
to be tonemapped, exposed and possibly overwritten by its composite stages. The mod's own attachment
is written by nothing else and read by nothing else, and the composite lands after the pack has
finished with the scene.

Together they are what makes the effect look the same everywhere: the program that shapes a lightning
channel is always the one written for it, and the pixels it produces reach the screen unmodified.

Three properties make it a like-for-like replacement rather than a different effect.

**Depth comes from the frame, not from the mod.** An effect has to be occluded by terrain, water,
entities and particles, and under a pack that depth buffer belongs to the pack at an address no API
reports. `EffectRenderTarget` asks the driver instead — whatever depth attachment the bound
framebuffer has, it borrows for one pass, read-only, with depth writes off, re-queried every frame and
detached at the end. It is blind to which pipeline created it, needs no shader-pack API, and
reproduces exactly what a direct draw at the same hook would have been occluded by.

**The accumulation is algebraically the same image.** Additive layers contribute `colour × alpha` and
no coverage; translucent layers contribute premultiplied colour and accumulate coverage, which also
attenuates the light already underneath them. `scene × (1 − coverage) + colour` is then the "over"
operator the world pass would have applied one layer at a time. In vanilla the pixels do not change.

**Every failure degrades instead of breaking.** A program that would not compile drops the whole set
and falls back to Minecraft's shader objects and render types — the old path, with the old compromises
under a pack, which is why it is the fallback. No depth attachment to borrow, an incomplete
framebuffer, a world rendered at some other resolution, a pipeline where the composite hook is never
reached, or anything at all throwing: the world pass draws straight into the scene the way it did
before, and says so in the log once. `compatibility.effectCompositor = false` selects the direct path
deliberately, and `compatibility.customShaders = false` the vanilla programs.

Two seams carry all of it, and every renderer sits above both. `FxBatchTarget` decides what a pass is
drawn *with* — `NativeFxBatchTarget` for the mod's own programs, `VanillaFxBatchTarget` for the
fallback. `EffectCompositor` decides what it is drawn *into*. `WorldFxRenderer` and everything under
it emit geometry into a `VertexConsumer` and can tell neither. `FxStateGuard` is the only class that
touches GPU state, captured from the driver on the way in and restored on the way out, so the frame
the pass interrupted cannot tell either.

The debug overlay (`general.debug`) reports which of each is in force: `programs own | compositor
isolated` is the intended path.

## Air distortion

`AirDistortionSystem` measures the strongest active wavefront during the world pass, where the pose
stack still maps world coordinates into view space, and hands the compositor six floats: centre,
radius, strength, aspect, phase. The composite shader evaluates the field per pixel.

Analytic rather than a screen-sized distortion buffer, because a wavefront is a circle and six floats
describe it exactly — a vector texture would cost an attachment, a clear, geometry and the bandwidth
of reading it back, for a field with a closed form. It also means the refraction is one branch inside
a pass that was happening anyway rather than a pass of its own.

The scene is only copied on the frames where refraction is actually on screen, with a single
`glCopyTexSubImage2D` and no second framebuffer, and the shader emits only the *difference* the warp
makes: the untouched scene is already in the destination and the blend adds to it. With no wavefront
on screen the composite reads nothing and the effect costs one blended fullscreen quad.

## Particle architecture

`FxParticleSystem` owns a bounded active list and an `ObjectPool`. Emitters never construct
particles: they draw from an `FxParticleSink` that applies the budget, the per-family config toggle
and the global cap in one place, so a storm allocates nothing after warm-up. Particles store
position, velocity, acceleration, drag, gravity, rotation, scale, colour and alpha as primitives, and
keep the previous tick of everything the renderer interpolates.

`ImpactParticleSpawnStrategy` sizes families as fractions of the LOD budget rather than fixed counts,
so a distant strike thins out evenly instead of losing whole families, and emits the electrical
families first so a tight budget keeps what reads as lightning. Colours come from the surface map
colour, which is why modded blocks work without a block switch.

Two details keep the non-emissive families from reading as black balls. First, they are drawn
without the lightmap, so raw map colours look like holes punched in lit terrain: every unlit colour
is scaled by `LightningEnvironment.litScale()`, derived from the light level sampled once at the
impact, with a floor that keeps them visible at night. Second, smoke starts pale and *darkens* as it
cools — an impact plume is pulverised surface and steam lit by the flash that made it, not charcoal.

Shape is handled the same way. Every soft particle carries an `aspect` ratio so the billboard is not
square, and the smoke mask is a torn multi-lobed puff rather than a radial gradient; a radial
gradient is what makes every particle in a cloud the same soft circle.

## Audio

`ThunderSystem` schedules `distance / 343` seconds of propagation delay, then adds each layer's own
musical offset. `DistanceThunderSoundStrategy` builds a cue rather than picking one clip: a near
strike is crack + sub-bass thump + a delayed rolling tail, a far one is a soft transient followed by a
long roll.

The engine part matters as much as the physics. Minecraft derives the OpenAL attenuation range from
the volume argument (`max(volume, 1) × 16` blocks) and clamps source gain to 1, so a clip played at
"volume 1.0" is inaudible past 16 blocks — which is why vanilla passes 10000 for its own thunder.
`ThunderMath.spatialVolume` instead solves for the argument that reproduces a chosen perceived
loudness at the listener's distance, keeping both the direction cue and a believable falloff. The
round trip is unit-tested from 0 to 500 blocks.

### Rolling thunder

`GiantRollingThunderEffect` is a separate effect, not a property of a bolt. A strike hands
`ThunderRollSystem` a position, a seed and an intensity; from that moment the roll runs on its own
timeline and never touches channel geometry, segment lists or the effect manager. Disabling the
visual bolt would not change a note of it.

It is assembled from six component layers rather than played as one long recording. A stretched clip
always sounds identical and always arrives from one direction; evenly spaced copies of one hit sound
mechanical. The planner instead scatters a dozen or so `ThunderPulse`s across roughly 5–10 seconds —
CRACK, BOOM, two or three overlapping low walls, four to seven irregular secondary rolls, distant
grumble, long tail — each with its own bearing around the listener, distance, layer, pitch, gain and
impact value. Gaps are irregular and often shorter than the clips themselves, so rolls overlap and
beat against each other, and successive pulses arrive from different parts of the sky.

`DistantBoltSystem` is the visible half, and the sky is counted separately from the audio — as a
*rate*. A roll needs a dozen sounds to feel enormous, but the horizon behind it carries 15 to 100
strokes every second for as long as the event runs, drawn from a fourth-power distribution so a
typical storm sits around twenty a second and the hundred-a-second sky-splitters are rare.

They are real channels, not lit patches of cloud: each cue is fed through the same
`MidpointDisplacementStrategy` the main bolt uses, with a shorter tree, heavy forking (82% branch
probability, three levels deep, plus micro-branches) and no canopy, then rendered by the same
three-layer `LightningRenderer`. The reference is a photograph of a storm front, which is mostly
branches rather than bare strands, so the LOD pass is deliberately skipped here — it exists to thin
distant detail, which is the one thing these must keep. Each hangs out of the cloud base and leans
12–45% of its height as it descends, because a wall of perfectly plumb lines looks like a fence.
Forks are not counted — one cue is one stroke and everything branching off it.

The whole event is one *front*, not a scattering of points: every stroke sits at the same distance
(±16%) inside a 25–50° arc that drifts a little as the event runs. That distance is derived from the
client's effective render distance — `min(renderDistance × 16, config.performance.renderDistance) ×
0.42`, clamped to 55–190 blocks — so the wall lands comfortably inside the fog instead of being
generated behind it. Spread the same strokes over hundreds of blocks of depth and eighty degrees of
sky and the effect falls apart: they read as unrelated flashes rather than one storm.

Total generation is capped, and the debug command says so when the cap truncates a request rather
than silently delivering a lower rate.

`/tempestfx roll [seconds] [channels-per-second]` overrides both, which is how the balance was tuned.

Only one roll runs at a time and a cooldown follows it. Overlapping ten-second events do not sound
bigger, they sound like the storm never stops — and when a roll does start, the ordinary cue is cut
to its sharp opening so the two paths never stack.

`ThunderRumbleCameraEffect` is likewise its own system, driven by pulses that actually started
playing rather than by the strike. It is a bank of independent damped oscillators: each boom pushes
one in with its own amplitude, frequency, decay and axis, and the camera offset is their sum. Heavy
transients thump and settle within about a second; quiet grumbles barely register. Both effects share
the `VoiceBudget` with the ordinary cue scheduler so neither can starve the mix.

Playback is voice-limited on top of that. Minecraft's static channel pool holds a few hundred
handles and is shared with every other sound in the game; a dense storm asking for more clips than
that makes the engine refuse handles for everything, not just for thunder. A sliding one-second
window caps how many clips Thunderhead may start, which is also what a listener would perceive anyway.
The discharge crackle shares the same budget so incidental sounds cannot starve the storm.

## Entity discharge

`EntityDischargeSystem` grants charge by proximity and sustains it by movement, measured from the
replicated `position` and previous-tick position of each target. A moving target keeps arcing and
regains a little charge with speed; a stationary one loses 20% per tick and disappears within about
half a second. Arc polylines are regenerated a few times per second by the simulation and stored
relative to the entity, so rendering only offsets them by the interpolated position.

## Ash imprint

A strike whose resolved target is a player creates an `AshImprint` at that player's feet, sized to
their bounding box and seeded from the strike. It renders as a branching burn scar — a Lichtenberg
figure drawn procedurally by the asset script — in two passes: a translucent charcoal decal for the
whole lifetime and an additive ember pass that cools over the first few seconds. A matching burst of
ash, embers and smoke is emitted through the normal particle pipeline. No block is placed or changed.

## Ball lightning

A real entity, not a client-side effect, so the server owns its position and its contact damage.

`BallLightningMotion` holds the model and is free of Minecraft types: smooth-noise horizontal drift
capped at about 1.6 m/s, a damped spring that holds it roughly 1.15 blocks above whatever surface is
underneath, and an output curve that ignites quickly, holds, then collapses over the final fifth of
its life. Slow smooth noise rather than random jitter is what makes it read as a floating object with
mass instead of a twitching particle.

The entity probes downward for the surface each tick so it follows the ground contour, discharges
into the first living thing it touches, and ends with a report — optionally scorching the grass
block underneath, which additionally requires the `mobGriefing` game rule.

The renderer layers a ground pool, three counter-rotating turbulent shells, a soft corona, a hot
near-white core and six discharges crawling over the sphere surface, all seeded so every player sees
the same sphere.

## Transient lighting

Two purely visual mechanisms, neither of which touches stored light:

- an additive light pool drawn on the struck surface, depth-tested so terrain occludes it correctly;
- `WorldFlashSystem`, which raises `ClientLevel#skyFlashTime` for a few extra distance-scaled ticks.
  Vanilla already brightens the lightmap and sky through that same transient client value for two
  ticks per bolt; Thunderhead only extends it, and honours Minecraft's own "hide lightning flashes"
  option.

No chunk is relit and no block light is written.

## Compatibility and bloom

`ShaderEnvironmentDetector` checks Iris and OptiFine classes without a hard dependency and reports
`VANILLA`, `IRIS`, `OPTIFINE`, or an explicit override. Nothing on the normal render path consults it
any more: an isolated pass is correct without knowing, and asking would be asking the wrong question.
It survives for two narrow jobs — choosing the bloom backend, and choosing the degraded
`ShaderPackProfile` on the fallback path, where the mod really is drawing into a frame somebody else
composites and the old compromises really do apply.

`BloomBackend` is the extension point for a pipeline-specific glow; the shipped safe backend performs
no framebuffer capture at all and simply lets the effect push its additive layers harder, which
produces the perceived bloom with zero GL state assumptions.

Registry lookups are lazy rather than lifecycle-dependent: NeoForge fires `RegisterRenderers` before
deferred setup work completes, so `TempestEntities` resolves from the registry on demand. The entity
registry is a defaulted one, so the lookup uses `getOptional` — asking it for an unknown id answers
`minecraft:pig`.

## Performance decisions

- geometry and bounds are computed once per strike, never per frame;
- render-time randomness and topology rebuilds are forbidden;
- passes are grouped by render type, giving a constant draw-call count;
- particle capacity is hard-bounded and fully pooled; emitters cannot allocate;
- particle, ribbon and arc hot paths use primitive maths only;
- effect lists are bounded by `performance.maxConcurrentEffects` and expire deterministically;
- the thunder queue, the return-stroke queue and the cross-thread event queue are all bounded;
- ball lightning is tracked through the entity-added hook rather than a per-tick world query;
- impact environment probes happen once and foliage scans are bounded;
- LOD reduces generations, forks, stubs, segment budget, particle budget and ring resolution;
- the native vertex buffer and the compositor's framebuffers are released after the storm ends, and
  again on a dimension change;
- the composite is one blended fullscreen quad, and reads the scene only while something refracts it;
- world block light and chunk relighting are never touched.
