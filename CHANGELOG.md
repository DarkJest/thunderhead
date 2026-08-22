# Changelog

## 1.2.0

### Rendering

- **The effect looks the same with a shader pack as without one.** The mod now compiles its own
  programs, fills its own vertex buffers, sets its own GL state and draws into a framebuffer of its own,
  then applies the result to the frame once Minecraft — and any pack — has finished producing the scene
  image. Nothing about that arrangement names Iris, OptiFine or Oculus, and no code path asks which of
  them is installed
- **Why it was invisible under a pack before.** Registering a core shader with the game hands every
  shader loader a decision about a program it does not recognise, and Iris resolves it by disabling
  colour and depth writes around the draw and rebinding its own framebuffer inside `apply()` —
  reasonable from its side, and it silently discarded the mod's geometry no matter which framebuffer the
  mod had bound. There is no shader object to recognise any more, so there is nothing to discard
- Everything the previous release gave up under a pack is back: the analytic channel cross-section, the
  wide glow, atmosphere and flash passes, air distortion, and ball lightning's shell, core and ground
  pool rather than its surface arcs alone
- **Occlusion still comes from the frame, not from the mod.** The effect target borrows whatever depth
  attachment the bound framebuffer has, for the duration of one pass, read-only and with depth writes
  off — re-queried every frame, so a resize, a render-scale change or a pack being switched on is
  followed automatically. Terrain, water, entities and particles occlude the effect exactly as they did
- Vanilla rendering is unchanged. Additive layers accumulate colour and no coverage, translucent layers
  accumulate both, and `scene × (1 − coverage) + colour` is the same "over" operator the passes were
  applying one at a time — with one half-float accumulation instead of a dozen round trips through an
  eight-bit buffer
- Air distortion no longer needs a post chain, a swap target or two fullscreen passes. It is a branch
  inside the composite, driven by six floats, and the scene is copied at all only on the frames where a
  wavefront is actually bending it
- **Every failure degrades rather than breaks.** A program that will not compile drops the whole set and
  falls back to Minecraft's shader objects and render types. No depth to borrow, an incomplete
  framebuffer, a world rendered at another resolution, a pipeline where the composite hook is never
  reached: the mod draws straight into the scene the way it did before and logs the reason once.
  `compatibility.effectCompositor = false` picks the direct path deliberately, and
  `compatibility.customShaders = false` the vanilla programs
- The debug overlay reports both halves — `programs own | compositor isolated` is the intended path
- GPU state is captured from the driver and restored by one class rather than assumed by each renderer:
  framebuffer, viewport, blend, depth test and write, culling, scissor, colour mask, active texture unit
  and its binding. No effect renderer contains a line of GPU state management, and none can tell which
  target it is drawing into

## 1.1.0

### Settings

- **An in-game settings screen.** Quality preset, the effect toggles, and sliders for thickness,
  glow, branching, height, tint, flash, thunder and the performance caps. Reachable from the mod
  list — ModMenu on Fabric, NeoForge's own config button — or with `/tempestfx settings`, which
  needs neither. Built on vanilla's own options widgets, so no config library is added as a
  dependency and nothing extra ships in the jar
- **`/tempestfx reload`** re-reads both config files without restarting the game. In singleplayer it
  reloads the gameplay config on the server thread too
- Fractions are edited as percentages, because "Thickness: 140%" needs no explanation and "1.4" does

### API

- `TempestFxApi.onStrike(...)` — other mods can now react to every strike Thunderhead draws, not
  only raise their own. Returns a handle that unsubscribes; registering before the client starts is
  supported and is the normal case; a listener that throws is contained and logged
- `LightningStyle` — per-strike look, passed through `LightningEffect.builder().style(...)`.
  Thickness, branchiness, reach and cold tint, all clamped. A styled strike is drawn to its style
  independent of the player's lightning settings, which govern the strikes the mod raises itself;
  leaving the style out follows the player's configuration, as before. Accessibility — brightness,
  flicker, reduced flashing — is not stylable and cannot be reached from the API at all
- `TempestFxApi.install` and `uninstall` moved to the nested `TempestFxApi.Internal`. They are the
  mod's own wiring and never were API; leaving them next to `triggerLightning` invited a third-party
  mod to replace the dispatcher and silently break every effect
- Intensity supplied through the API is clamped to 4. A flash is something people look at, and the
  one entry point for untrusted numbers should not be able to produce a white screen
- `LightningEffect.builder().origin(...)` — the caller can state where the channel starts, which
  fixes the bolt's angle and its length. Without it the bolt hangs from the cloud base with a seeded
  lean, as before. The geometry runs between the two points whatever their relation, so a channel
  can lean hard, run flat or rise
- `ThunderOptions` — which clip, how loud, how long after the flash. `ThunderVoice.SILENT` for a
  strike that is seen and not heard. The player's master volume and their realistic-delay setting
  are still applied afterwards and cannot be overridden
- `ParticleFamily` — restrict which debris a strike throws up. A filter: it narrows what the ground
  and the player's settings already allow, never widens it
- `LightningStyle.coreColor` / `glowColor` — arbitrary `0xRRGGBB` for the core and the halo
- `TempestFxApi.triggerThunderRoll(...)` — raise a rolling thunder event on its own, with no
  lightning in front of it
- `TempestFxServerApi.spawnBallLightning(...)` — the one server-side entry point, separate because a
  sphere is a replicated entity rather than a client-side visual
- Per-strike overrides are grouped in `StrikeOptions` rather than widening the event one parameter
  at a time
- Documented in [`API.md`](API.md)

### Multiplayer

- Ball lightning replicates its visual seed and its age. Two players standing together saw two
  differently shaped spheres, and anyone arriving mid-life saw one that had just been born
- Ball lightning glides between position updates instead of snapping to them
- The burst now plays the mod's own arc clip. It used `entity.lightning_bolt.impact`, which is one
  of the two sounds Thunderhead suppresses, so modded clients watched it burst in silence
- Near-miss damage excludes vanilla's real damage box (`x±3, y-3..y+9, z±3`) instead of a sphere
  approximating it. A target four to nine blocks above a strike was taking both lots of damage
- Bolts vanilla marks `visualOnly` — the skeleton horse trap, and anything spawned for show — no
  longer deal near-miss damage. They still flash
- The struck entity breaks exact ties on entity id, and the discharge effect sorts by distance
  before applying its cap, so two clients cannot disagree about who was hit

### Audio

- **Thunder no longer cuts off.** Every clip's envelope is an exponential decay, and an exponential
  never reaches zero — the file simply ended while the sound was still going. Ten of the thirteen
  clips ended mid-sound, the worst (`distant_thunder`) at 15% of its peak, which is a click rather
  than an ending. Every clip now gets a raised-cosine fade scaled to its length, and all thirteen
  end at effectively silence

### Compatibility

- **Works under Iris shader packs.** Detection existed but was never consulted: the mod kept drawing
  with its own core programs while a pack composited the world through its own G-buffers, and the
  geometry was discarded - lightning was simply invisible. The pipeline is now asked, per frame,
  through Iris's own `v0` API, so it also follows a player toggling shaders without restarting
- Under an active pack the mod switches to a conservative profile: vanilla core programs, no wide
  glow or atmosphere passes (they showed as flat discs without the bundled shader that shapes them),
  channels widened and brightened to survive a pack's tonemapper, per-segment intensity compressed so
  the thin end of the branch ladder does not vanish, and ball lightning reduced to its surface arcs
  because every other part of it is an additive quad that fights a pack's deferred water
- Verified with Iris 1.8.12 and Complementary Unbound r5.8.1 on NeoForge 1.21.1. Vanilla rendering is
  byte-for-byte unchanged: the profile's multipliers are all 1 and no pass is skipped
- **Turning the mod off no longer hides lightning.** The renderer registered for vanilla bolts drew
  nothing unconditionally, so with `general.enabled` off there was no lightning at all. It now defers
  to vanilla's own renderer whenever Thunderhead is not drawing
- The three decorative mixin injections — sky flash, camera impulse, vanilla sound suppression — are
  `require = 0`, so another mod rewriting one of those methods costs the feature instead of stopping
  the game from starting. The one hook that has to work, the bolt-spawn catch, stays required
- `audio.suppressVanillaThunder` lets a player keep Minecraft's own lightning sounds, for anyone
  running a weather or sound mod that relies on them

### Fixes

- **No more black squares.** `MapColor.NONE` packs to zero, and air, glass and a few other blocks
  report it. Dust, debris and ash take their colour straight from the sampled ground and debris is
  drawn untextured, so a strike over a gap, a cliff or a glass roof threw up plain black squares —
  most visible under `/tempestfx stress`. A colourless surface now falls back to a neutral grey,
  substituted in `LightningEnvironment` itself so no producer can reintroduce it
- **Effects no longer draw through terrain.** None of the mod's render types enabled the depth test;
  they only disabled depth writes and inherited the test from whatever drew before them, so a change
  of pass order put the whole storm in front of the world. Every pass now sets its own depth state
  and restores it
- The gameplay config is re-read when a server starts on NeoForge, matching Fabric

## 1.0.0

Initial release.

### Lightning

- Fully procedural, seeded lightning generation
- Dynamic branching and tapered channel geometry
- Multi-stroke flashes and sky-spanning cloud branches
- Multi-layer cold-white/blue glow rendering

### Impact effects

- Ground shockwaves and impact bursts
- Sparks, micro-arcs, smoke, debris, dust, ash and embers
- Water-specific spray, steam and surface ripples
- Entity discharge and direct-hit ash effects

### Atmosphere

- Transient scene illumination and screen flashes
- Custom layered thunder with realistic distance delay
- Camera pressure impulses and rolling thunder events
- Rare server-driven ball lightning

### Performance and accessibility

- Four quality presets, LOD, particle caps and effect limits
- Reduced-flashing mode and independent camera/flash controls

### Compatibility

- Fabric for Minecraft 1.21.1
- NeoForge for Minecraft 1.21.1
- No external shader pack required

