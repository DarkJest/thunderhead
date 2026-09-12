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

## Shader evidence

1.2.1 finite captures on Radeon RX 570 with NeoForge 21.1.248:

- Vanilla: run 1789238547404; examined before/strike frames 119/122. Visible channel and scene flash.
- Iris 1.8.12 + Sodium 0.6.13 + Complementary Unbound r5.8.1: run 1789238635301;
  examined strike frame 122. Channel and impact visible; log says isolated compositor.
- Both runs terminated normally and saved their disposable worlds. Screenshots live in the ignored
  development run directory; they are inspection evidence, not bundled assets.

These captures do not certify transparent-water depth, accurate cloud occlusion, reflections,
frame-time budgets, other GPUs, Fabric runtime or other shader packs.
