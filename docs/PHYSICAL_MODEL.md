# Physical model and explicit approximations

## 1.3 — shared flash timeline

A flash owns one generated channel tree and one immutable pulse plan. Contact effects consume that
same plan, retaining the original position, seed, surface and per-event overrides. Pulses never invoke
the geometry generator again. Explicit multi-contact flashes are not implemented yet.

The renderer evaluates a weak downward leader followed by a return front traveling from the ground
toward the cloud. The model uses one block = one metre for propagation, and an approximate return-front
speed of 100 million metres/second, consistent in order of magnitude with the
[NWS explanation](https://www.weather.gov/safety/lightning-science-negative-charged-flash).
This is normally much faster than one frame; it is intentionally not presented as a slow upward beam.

| Parameter | Current value | Interpretation |
| --- | --- | --- |
| Timeline unit | 1 tick = 50 ms | Minecraft simulation time, with fractional visual evaluation |
| Visible leader duration | 20 ms Realistic / 60 ms Cinematic | Game-scale presentation approximation, not derived from storm field |
| Relative leader brightness | 0.04 | Artistic luminance ratio |
| Pulse decay time | 11 ms Realistic / 35 ms Cinematic | Optical presentation envelope, not an electrical current waveform |
| Inter-pulse interval | 30–100 ms | Bounded illustrative interval, not a fitted climatological distribution |
| Maximum repeats | 0–4 | User/budget cap |
| Multi-stroke mixture | 75% of deterministic seed samples when enabled | Game variety parameter, not a universal measured rate |
| Return-front speed | 100,000,000 m/s | Approximate order of magnitude; different strokes can vary |

Brightness integrates the exponential pulse envelope analytically over a frame interval, preserving
integrated energy across 20/30/60/144 FPS in tests. Actual display RGB can saturate; this test does not
claim calibrated photometry or identical screenshots at different frame rates.

Contact particles, legacy sky-light extension and audio scheduling still execute on tick boundaries;
their timing is rounded upward by less than 50 ms. Sub-tick world illumination and geometry-derived
audio are subsequent work. Full server storm timing requires the planned network protocol.

Reduced flashing replaces the channel pulse train with a monotone decay and suppresses repeat contact
events. It is an effective override and does not erase saved preferences. Existing configs retain the
Cinematic profile; new configs select Realistic. Profiles are independent of GPU quality settings.

Realistic currently excludes impact rings, movement-triggered entity arcs, player-hit branch imprints
and audio-triggered distant walls. Server ball-lightning settings remain separate and must be disabled
by the operator if experimental gameplay is unwanted; client presentation cannot remove a real entity.

## 1.4 — channel geometry and explicit categories

Realistic rendering generates a fixed 128-segment canonical backbone. Lower detail retains a subset
of its vertices; forks use independent seeds and can only attach to retained vertices. Hard budgets
always preserve the first and last backbone points. This is a stochastic geometric model, not a
simulation of air breakdown or a solution of Maxwell's equations.

The visible core half-width is 0.055 blocks before user scaling and a screen-visibility floor. It is
not a measurement of plasma radius. Geometry carries relative branch brightness; event intensity is
applied once at rendering. The fallback cloud base is an absolute world Y=192, configurable as
lightning.cloudBaseY; terrain above it raises the fallback source to retain at least 32 blocks of height.
Shader-pack cloud meshes can differ: this fallback does not claim to locate their actual density field.

Kinds are explicit API inputs. Vanilla bolt entities remain NEGATIVE_GROUND. POSITIVE_GROUND uses a
longer illustrative decay and mostly single-pulse mixture; INTRACLOUD/INTERCLOUD use cloud endpoints,
a longer envelope, and no ground aftermath. Their timing distributions are still game tuning. Natural
server selection and storm-wide category rates belong to the later server simulation stage.

Use `/tempestfx type intracloud 12345` (or intercloud, positive_ground, negative_ground) for visual tests.
The old four-argument StrikeOptions constructor defaults to NEGATIVE_GROUND, preserving existing callers.

## Shader evidence (continued)

### 1.5.0-dev surface lighting limits

The surface-lighting option reconstructs view-space position and screen-derived normals from a private
snapshot of the available depth buffer. Up to four samples are selected along active channels. Their
power follows the same integrated pulse envelope; lighting.illuminationRadius controls the influence
radius and zero disables it. The post pass works on already tone-mapped color, so its material response
is approximate. Eight visibility samples can only find obstacles represented in the visible depth.

It does not inject light into shader-pack reflections, cloud density or exposure. Transparent-water
and offscreen occlusion require additional integration. These missing capabilities keep full 1.5
acceptance open. Failed isolation retains the old pool/sky-flash state rather than silently deleting it.

An independent opt-in compatibility.packNativeChannels mode submits the core through Iris's lightning
material wrapper before the pack finishes the frame. The wrapper sets/restores the pack's lightning
entity material ID; plain RenderType.lightning did not supply that context. It is reflectively resolved
and format-checked, with isolated fallback when the internal Iris interface is absent. This adapter is
version-sensitive and requires an explicit test per supported Iris version. The pack controls its material,
bloom and exposure; the late pass omits the duplicate core. This can make the channel available to
effects a pack implements, but does not guarantee any particular reflection or cloud-lighting algorithm.
The option defaults off and the isolated core remains available without a shader pack or after failure.
Packs may replace input colors and alpha rather than simply grade them. In that case pack-native mode
cannot preserve the calibrated pulse-brightness envelope; use isolated channels for timing/brightness control.

Development captures: add -PtempestfxCapture to the NeoForge run with a disposable quick-play save.
Add -PtempestfxCaptureScene=surface for a stone test plane/wall and low camera; this deliberately
modifies only the selected disposable world. The finite harness saves screenshots and exits normally.
Capture overhead makes reported FPS unsuitable as a benchmark. -PtempestfxCaptureFps sets the cap.

1.2.1 finite captures on Radeon RX 570 with NeoForge 21.1.248:

- Vanilla: run 1789238547404; examined before/strike frames 119/122. Visible channel and scene flash.
- Iris 1.8.12 + Sodium 0.6.13 + Complementary Unbound r5.8.1: run 1789238635301;
  examined strike frame 122. Channel and impact visible; log says isolated compositor.
- Both runs terminated normally and saved their disposable worlds. Screenshots live in the ignored
  development run directory; they are inspection evidence, not bundled assets.

These captures do not certify transparent-water depth, accurate cloud occlusion, reflections,
frame-time budgets, other GPUs, Fabric runtime or other shader packs.
