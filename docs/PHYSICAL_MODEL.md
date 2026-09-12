# Thunderhead 2.0: physical model and limits

Thunderhead is a bounded game simulation. The sources below inform its structure; its numerical distributions, damage values and appearance controls are not calibrated measurements of every real storm.

## Discharges

One flash has an immutable channel tree and pulse plan. A weak leader precedes a return front; subsequent pulses reuse the same channel. Ground flashes include a short upward connecting segment. The return front uses approximately 100 million m/s with one block treated as one metre for propagation. This is normally faster than a frame, so exposure is integrated analytically rather than slowed into an upward beam.

| Parameter | Implementation / interpretation |
| --- | --- |
| Time unit | 1 tick = 50 ms; visual evaluation retains fractional ticks |
| Negative ground leader | 20 ms in Realistic, a game-scale presentation approximation |
| Negative ground optical decay | 11 ms time constant in Realistic |
| Return intervals | Illustrative bounded 30–100 ms range, not a fitted climatology |
| Positive ground | Longer decay and predominantly single-pulse mixture |
| Intracloud / intercloud | Distinct horizontal geometry, branching/wander and longer timing profiles |
| Core radius | 0.055 blocks before scaling; a pixel footprint floor prevents dashed subpixel lines |
| Radiance | Presentation exposure independent of pulse timing; reduced flashing caps it |
| Cloud base | Configurable absolute fallback height, not shader-pack density geometry |

The Realistic backbone has 128 canonical segments. LOD retains a subset of its vertices and attached forks; geometry budgets preserve both endpoints. This stochastic geometry is not an electrostatic field solver. Explicit seeds control shape, not event identity.

## Sound

Up to eight equal-length trunk portions emit sound per pulse. Arrival uses distance / 343 m/s and the current listener position. Source weights limit aggregate loudness; all audio shares queue and voice budgets. A short local visibility test can reduce gain and select a muffled distant profile. This is not full acoustic ray tracing, frequency-dependent atmospheric propagation or physically exact reverberation. It can be disabled when another sound mod supplies obstruction.

Network arrivals seek existing time. Past contact visuals are not replayed; both channel and legacy audio preserve future acoustic arrivals while dropping already-past cues. Tick scheduling introduces up to roughly one tick of timing quantization.

## Storms and gameplay

Server cells have finite charge, drift, growth and decay, driven by Minecraft's thunder state. Default mature activity is 0.12 flashes/s per cell, with at most eight cells per dimension. Category mixture and lifecycle are game tuning. Only already-loaded terrain is considered for optional extra ground strikes.

Ordinary vanilla targeting, direct damage, ignition, copper and conversions remain vanilla responsibilities. Additional ground strikes are separately opt-in. Their local 17x17 target search favors height and rods; it is not a replacement for vanilla's broader rod targeting rule.

Additional ground-current damage uses a connected sampled surface, material conductance and ground/water contact. Gaps break the path. An optional short side flash requires a conductor and a clear loaded path. Damage is a game balance value, not a medical/electrical injury model. Bounding-box overlap excludes vanilla's own strike volume. Cosmetic events cannot apply additional gameplay damage. Ball lightning is an experimental separate feature, off in new server configs.

## Lighting and shaders

The supported channel path owns its programs and framebuffer and composites after the scene. Surface lighting reconstructs visible position/normals from a private depth snapshot and samples up to four portions of active channels. Radius zero disables the field. The visibility approximation uses eight depth samples; offscreen occluders and transparent/cloud volumes cannot be recovered from that data.

Scene color has already been tone mapped. This is not access to physical albedo, native HDR exposure, cloud density or reflection buffers. The tested native-material experiment was removed because a pack replaced channel RGBA and wrote objectionable depth. Stock shader packs do not have a universal interface for physically consistent light injection.

Capability and combination-specific results are in release/compatibility.md. Full volumetric cloud lighting, pack-native reflections and all future shader packs are not guaranteed.

## Accessibility

Reduced flashing preserves saved preferences, substitutes monotone channel decay, suppresses native sky-flash returns and admits at most one new visual flash per second across the storm. Incoming primary API notifications and appropriate audio still work. The setting does not constitute a medical safety certification.

## Sources

- [NOAA NSSL: Lightning Types](https://www.nssl.noaa.gov/education/svrwx101/lightning/types/) — discharge categories and leader/return-stroke direction.
- [NWS: Negative Flash](https://www.weather.gov/safety/lightning-science-negative-charged-flash) — leader attachment and approximate propagation speeds.
- [NWS: Return Stroke](https://www.weather.gov/safety/lightning-science-return-stroke) — channel reuse and upward optical propagation.
- [Iris uniform reference](https://shaders.properties/current/reference/uniforms/overview/) — interfaces available to packs; a declared uniform is not proof that a pack uses it.
