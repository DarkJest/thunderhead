# 2.0.1 mechanism audit

The report of invisible `/tempestfx roll` reproduced in Realistic: audio scheduled visual cues,
but the client discarded every cue because of its presentation profile. This was a rendering
dispatch bug, independent of the shader pack. The explicit command now works in both profiles,
points ahead of the player, and explains when distant bolts or reduced flashing suppress visuals.
Automatic decorative rolls remain Cinematic-only.

## Corrections

- Air distortion omitted the global model-view rotation, treating a forward-facing impact as
  behind the camera. Projection now includes both world pose and model-view transforms.
- Distant-channel intensity was multiplied twice; geometry now has unit intensity.
- Decorative ground endpoints now sample loaded terrain instead of using camera-relative height.
  Unloaded columns use a sea-level fallback; this is not a remote terrain query.
- Shader passes could submit the same ball-lightning entity twice. Deferred snapshots are keyed
  by entity ID, retaining the latest transform once per visible scene.
- Imprint smoke and sphere embers bypassed their particle switches. Both emitters now honor
  material-specific configuration.
- Loader client ticks continue during integrated-server pause. FX simulation now pauses too.
- Settings expose surface ripple and explain profile, accessibility and parent-option dependencies.
  Cycle buttons no longer repeat their captions.

## Runtime scenarios

Each suite runs in a disposable integrated world, captures the final framebuffer after the HUD,
and writes counters/assertions to `neoforge/run-audit/mechanism-audit-<run>.json`.

| Group | Scenarios / evidence |
| --- | --- |
| Channels | Realistic land and water, positive ground, intracloud, intercloud |
| Cinematic impacts | Land debris, water spray, shockwaves and active distortion projection |
| Camera / exposure | Separate screen flash, camera impulse in both profiles, native sky flash |
| Lighting | Active depth-based surface lighting; representative frames inspected |
| Sound | Voice scheduling and delayed onset at distance; not a listening-quality evaluation |
| Attached / persistent FX | Entity discharge, ash imprint, ball lightning in both profiles |
| Roll | Both profiles produce active distant channels and thunder voices |
| Accessibility | Reduced flashing limits channels, suppresses storm walls and native sky flash |
| Fallback | Real vanilla lightning entity survives disabled custom programs / disabled mod |
| Particle switches | Imprint and sphere remain visible with their particles disabled |
| Settings / pause | Both profile screens, unavailable widgets, no FX simulation advancement while paused |

26 cases per suite. Most cases last six seconds and capture four moments; settings cases also
capture the actual screen. Assertions verify internal activity and negative conditions; representative
screenshots supplement them, rather than treating counters as proof of every pixel.

Test environment: Minecraft 1.21.1, NeoForge 21.1.248, Iris 1.8.12, Sodium 0.6.13,
Windows 11, Microsoft Java 21.0.12, Radeon RX 570.

| Rendering configuration | Result | Local run ID |
| --- | --- | --- |
| ARTShade V0.3.0FIX | 26/26 passed | 1789287274570 |
| Complementary Unbound r5.8.1 | 26/26 passed, including added pause assertion | 1789287632583 |
| Shader pack disabled (Iris/Sodium installed) | 26/26 passed, including pause assertion | 1789287824685 |

ARTShade's run predates the additional
pause assertion; its settings screens were exercised. Both loader artifacts build and 191 shared
tests pass. This patch's runtime matrix uses NeoForge; prior Fabric and multiplayer evidence is
recorded in [compatibility](../release/compatibility.md).

## Reproduction

Prepare a disposable save named `Mechanism Audit` inside `neoforge/run-audit/saves`, then run
`gradlew :neoforge:runMechanismAudit` with Java 21. The harness changes blocks around
136..168 / 70..90 / 184..224, teleports the test player and removes fixture entities. Never point
this development run at a valued save. Third-party packs and test worlds are not bundled.
Use `:neoforge:runManualPreview` for the same profile without automated scenarios or auto-exit.

These checks do not certify every shader pack, transparent-depth edge case, GPU driver or
physical transport model. Ball lightning remains experimental, and the program-disabled sphere
fallback has a reduced appearance. Server conduction, storm simulation and protocol regressions
remain covered by shared tests and the previous 2.0 integration runs, not repeated visual cases here.
