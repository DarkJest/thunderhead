# Thunderhead — Lightning & Thunder Overhaul

> Minecraft 1.21.1 · Fabric & NeoForge · cinematic lightning, storm fronts and rolling thunder

**Thunderhead** is a visual, audio and gameplay overhaul of lightning for Minecraft 1.21.1. A strike
stops being a white line and a delayed boom, and becomes a short cinematic event: a leader racing
down from the cloud, a branching cold-white channel that flashes two or three more times down the
same path, a blinding exposure flash, a pressure ring tearing across the ground, sparks, embers,
debris, smoke and ash — and only afterwards, at the speed of sound, the thunder.

The visual half is client-side and works on any server. The gameplay half — being hurt by a strike
that lands near you, and the ball lightning it can leave behind — needs the mod on the server too.

## Features

- ⚡ **Procedural channels** — seeded midpoint displacement with direction-aware forking, micro
  stubs and a leader that visibly travels from cloud to ground. Width steps down a fixed ladder with
  every level of branching — thick trunk, distinctly thinner limbs, thinner twigs again — instead of
  a tangle of identical strands
- 🔁 **Multi-stroke flashes** — real lightning discharges several times down the same channel; so
  does this, with seeded timing, weakening strokes and a couple of metres of wander between them
- 🔦 **Three-layer emissive rendering** — cold halo, inner sheath and near-white core, drawn through
  bundled core shaders that give every ribbon a soft analytic cross-section
- 🌌 **Sky-spanning flashes** — channels reach 130–190 blocks up with near-horizontal intracloud
  canopy branches crawling out from the cloud base, so a strike occupies real sky
- 🔊 **Rolling thunder as its own effect** — a five-to-ten second event assembled at runtime from
  a dozen component layers, each with its own bearing, distance, delay, pitch and envelope: CRACK,
  BOOM, a low-frequency wall, irregular overlapping rolls sweeping across the sky, distant grumble
  and a long decay. It is triggered by a strike and then runs independently of it
- ⛅ **A wall of distant lightning** — a roll puts 15 to 100 heavily forked cloud-to-ground channels
  across the horizon *every second* it runs: vertical, leaning as they descend, each with its own
  fork tree that does not count toward the rate. They form one tight storm front — a single distance
  scaled to your render distance, inside a narrow arc that drifts as the event moves — rather than
  scattered flashes all over the sky. Dense storms are deliberately rare
- 🌊 **Transient-driven camera** — the view reacts to the individual booms as they arrive, not to a
  single sine running for the whole event
- 💥 **Ground shockwave** — a noisy, seam-free pressure ring, a procedural shockwave shader with
  curl-noise warping, an overexposed impact burst and wide atmospheric haze
- 🌊 **Screen-space air distortion** — a post-processing pass that bends the scene along the
  wavefront (vanilla pipeline only, degrades silently everywhere else)
- ✨ **Custom particle engine** — pooled sparks, micro arcs, embers, dust, debris, smoke, steam, ash
  and water spray, all velocity-aware, interpolated, lit by the scene and shaped by torn masks
  rather than soft discs; no vanilla particle is used
- 🔌 **Entity discharge** — residual charge crawls over anything that was near the strike **and is
  moving**; stand still and it bleeds away in half a second
- 🕯️ **Ash imprint** — a direct hit on a player burns a branching Lichtenberg scar into the ground
  that glows, cools and slowly fades
- ⚡ **Near-miss damage** — a strike that lands close hurts, on a smooth falloff, instead of vanilla's
  all-or-nothing 3-block box
- 🔮 **Ball lightning** — a rare, slow, floating plasma sphere left behind by a strike: it hovers at
  head height, drifts along the ground contour, sheds sparks, and discharges into whatever it touches
- 💡 **Transient illumination** — an additive light pool plus a short extension of the client-side
  sky flash; no chunk is ever relit
- 🌩️ **Layered thunder** — five original profiles plus a sub-bass impact thump and an arc crackle,
  scheduled at `distance / 343 s`, mixed so distant strikes stay audible, and voice-limited so a
  dense storm never exhausts the game's sound channels
- 🎮 **Fabric + NeoForge** from one shared codebase
- 🌈 **Works under any shader pack** — the effect is drawn with the mod's own programs into a
  framebuffer of its own and applied once the frame is finished, so it looks the same with a pack as
  without one. Nothing on that path asks which pipeline is installed. Verified with Iris 1.8.12 under
  Complementary Unbound r5.8.1 and ARTShade V0.3.0FIX; vanilla rendering is unchanged
- ⚙️ **Fully configurable**, with a reduced-flashing accessibility mode

No vanilla lightning renderer, thunder sound, particle type or borrowed asset is used. Every bundled
texture, GLSL program, OGG and even the mod icon is original work made for this project: the masks,
curl maps, burst and haze textures are synthesised from noise, filters and drawn primitives, and the
audio is built from filtered noise and transients. Nothing is sampled from Minecraft, from another
mod, from a shader pack or from the web.

## Requirements

- Minecraft 1.21.1
- Java 21
- Fabric Loader 0.16.14+ with Fabric API, **or** NeoForge 21.1+

Install exactly one loader jar from the matching `build/libs` folder.

**On a vanilla server** the client half still works: channels, multi-stroke flashes, shockwaves,
particles, discharges, ash imprints, thunder and screen effects all run from replicated state alone.
Near-miss damage and ball lightning are simply absent, because both are server decisions.

**With the mod on the server**, everything is available and every client sees the same storm.

## What is and is not touched

Vanilla lightning behaviour is left exactly as it is: 5 points and ignition inside its own damage
box, fires, lightning rods, copper weathering, mob conversions. Thunderhead only adds effects
*outside* that box — the real one, `x±3, y-3..y+9, z±3` — so nothing is ever damaged twice for one
bolt, and every addition can be turned off in `config/tempestfx-server.json`.

## Multiplayer

Thunderhead sends no custom packets at all. Consistency comes from deriving every random decision —
channel shape, fork placement, flicker, stroke timing, thunder profile, particle scatter, arc
pattern, imprint rotation — from data the server replicates verbatim: the bolt's spawn position and
its entity id.

Entity ids matter here. An earlier version salted the seed with the world time, but a client's tick
counter can drift a tick or two between the periodic time packets, which would give two players
slightly different bolts. Entity ids arrive in the spawn packet and are known to the server as well,
so the server-side gameplay and the client-side visuals agree on one flash.

`LightningBolt.seed`, the field vanilla uses for its own bolt shape, is deliberately *not* used: it
is rolled from each client's local random, so it differs per player and per rejoin.

Ball lightning is a real entity rather than a client-side effect, so vanilla's entity replication
keeps its position, its lifetime and its damage in agreement for free. What vanilla has no opinion
about is replicated explicitly: the sphere's visual seed, so two players standing together watch the
same sphere rather than two different ones, and its age, so someone who walks up to a sphere halfway
through its life sees it halfway through rather than newly born. It also glides between position
updates instead of snapping to them.

Where the client still has a choice to make, it is made the same way everywhere: the entity a bolt
struck breaks exact ties on entity id, and the discharge effect sorts by distance before capping its
target list, because the order a level lists its entities is not the same on every client.

Gameplay stays exactly additive to vanilla's. Near-miss damage excludes vanilla's real damage box
(`x±3, y-3..y+9, z±3`, not a sphere), so nothing is ever hurt twice for one bolt, and a bolt vanilla
marked as cosmetic — the skeleton horse trap, or anything a datapack spawns for show — does no
damage here either, though it still flashes.

## Configuration

Two files, written on first run.

`config/tempestfx.json` — looks, sounds, performance. Values are clamped on load, and a malformed or
hand-edited file falls back to defaults instead of preventing startup.

| Section | Notable keys |
| --- | --- |
| `general` | `enabled`, `debug`, `reducedFlashing` |
| `lightning` | `geometryQuality`, `branchCount`, `thickness`, `glowStrength`, `flicker`, `coldTint`, `returnStrokes`, `scale`, `skySpread` |
| `impact` | `shockwave`, `shockwaveStrength`, `sparks`, `smoke`, `debris`, `ash`, `airDistortion`, `airDistortionStrength`, `surfaceRipple`, `entityDischarge`, `entityDischargeRadius`, `entityDischargeMinSpeed`, `ashImprint`, `ashImprintSeconds`, `ballLightningEffects` |
| `lighting` | `dynamicLighting`, `illuminationRadius`, `illuminationStrength`, `worldFlash`, `worldFlashTicks`, `distantBolts` |
| `camera` | `screenFlash`, `flashStrength`, `cameraImpulse`, `impulseStrength` |
| `audio` | `customThunder`, `thunderVolume`, `realisticSoundDelay`, `maxThunderDistance`, `giantRoll`, `giantRollChance`, `giantRollDistance` |
| `performance` | `qualityPreset`, `maxParticles`, `renderDistance`, `lod`, `maxConcurrentEffects` |
| `compatibility` | `shaderCompatibilityMode`, `bloomMode`, `customShaders` |

`config/tempestfx-server.json` — gameplay. Separate on purpose, so an operator can hand it out or
lock it down without touching anybody's visual settings.

| Section | Keys |
| --- | --- |
| `nearMiss` | `enabled`, `radius` (9), `maxDamage` (5), `igniteSeconds` (2), `igniteFraction` (0.45), `affectMobs` |
| `ballLightning` | `enabled`, `chancePerStrike` (0.05), `minimumSpawnDistance`, `minRadius`/`maxRadius`, `minSeconds`/`maxSeconds`, `damage` (6), `contactDamage`, `contactRadius`, `contactCooldownTicks`, `igniteSeconds`, `scorchGround` |

Near-miss damage starts where vanilla's box ends (3 blocks) and falls to zero at `radius` on a
squared curve. Ball lightning scorching a grass block also requires the `mobGriefing` game rule.

### Accessibility

`general.reducedFlashing` removes channel flicker, re-strikes and the whole multi-stroke sequence,
caps the exposure flash and the camera impulse, and disables the sky-flash extension.
`camera.screenFlash` and `camera.cameraImpulse` can also be turned off independently.

Minecraft's own **Hide Lightning Flashes** option is respected: with it enabled, Thunderhead draws no
exposure flash and requests no sky flash at all.

## Debugging

Enable `general.debug` for an on-screen counter of bolts, segments, particles, thunder queue, lights,
discharges and imprints, plus the detected pipeline and whether the custom shaders loaded. Then:

```text
/tempestfx strike           visual only, on the ground you are facing
/tempestfx strike 40        visual only, further away
/tempestfx strike 40 72 -15 visual only, at exact world coordinates
/tempestfx strike --seed 12345
/tempestfx strike 40 72 -15 --seed 12345
/tempestfx strike water     visual only, forced surface type
/tempestfx strike water --seed 12345
/tempestfx strike-camera 20 --seed 12345
/tempestfx directhit        visual only, simulates a bolt landing on you
/tempestfx summon           a REAL bolt at your feet: vanilla damage, fire, and the visuals
/tempestfx ball             a REAL ball lightning entity in front of you
/tempestfx roll             a rolling thunder event on its own, with no lightning at all
/tempestfx roll 8           the same, forced to eight seconds
/tempestfx roll 8 50        eight seconds at fifty channels per second
/tempestfx stress 20        20 visual strikes at once
/tempestfx camera cinematic spectator capture preset: HUD off, no bobbing, smooth camera
/tempestfx camera speed .06 set cinematic flight speed (0.01 to 0.5)
/tempestfx camera off       restore saved options and game mode
```

The distinction matters: `strike` and `directhit` draw and nothing else — no entity, no damage. Use
`summon` and `ball` to test gameplay. Both are shortcuts for the vanilla `/summon` command and need
the usual permission.

## Compatibility philosophy

The mod owns the whole path a lightning bolt takes to the screen: it compiles its own GLSL programs,
fills its own vertex buffers, draws into a framebuffer of its own, and applies the result to the frame
after the game — and any shader pack — has finished producing the scene image. Nothing along the way
asks which rendering pipeline is installed, because nothing along the way needs to know.

Occlusion still comes from the frame rather than from the mod: the effect target borrows whatever
depth buffer the bound framebuffer has, read-only, for the duration of one pass. So terrain, water,
entities and particles hide the effect exactly as they always did, in every pipeline, with no
pack-specific API involved.

Every failure degrades instead of breaking. A program that will not compile drops the whole set back
to Minecraft's shader objects; no depth to borrow, an incomplete framebuffer, a world rendered at
another resolution or a pipeline where the composite point is never reached drops to drawing straight
into the scene. Each case logs its reason once, and `compatibility.effectCompositor` and
`compatibility.customShaders` select those paths by hand.

Verified with Iris 1.8.12 under Complementary Unbound r5.8.1 and ARTShade V0.3.0FIX; OptiFine is
untested rather than unsupported. See [`release/compatibility.md`](release/compatibility.md).

## For mod developers

Thunderhead has a small client-side API: raise your own lightning, style it, or react to every
strike the mod draws. No packets, no registration, and it degrades to a no-op when the mod is
absent so an optional integration cannot crash its host.

```java
TempestFxApi.triggerLightning(LightningEffect.builder()
    .position(new Vec3d(x, y, z))
    .seed(seed)
    .style(LightningStyle.builder().thickness(1.8f).branchiness(1.4f).build())
    .build());
```

Full reference, Gradle coordinates and the soft-dependency pattern: [API.md](API.md).

See [ARCHITECTURE.md](ARCHITECTURE.md) for internals, [BUILDING.md](BUILDING.md) for reproducible
builds, and [docs/LISTING.md](docs/LISTING.md) for the public name, store copy and keywords.

## Credits and license

Thunderhead is made by **GestSe**.

Code and generated assets are available under the MIT License. Asset provenance is documented in
`common/src/main/resources/assets/tempestfx/ASSET_LICENSE.md`.
