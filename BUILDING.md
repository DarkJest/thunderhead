# Building Thunderhead

## Prerequisites

- A Java 21 JDK
- Internet access for the first Gradle dependency resolution

The checked-in Gradle Wrapper uses Gradle 8.14.3. Set `JAVA_HOME` to a Java 21 JDK if your system
default is older.

## Build and test

Windows:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\gradlew.bat clean buildAll
```

Linux/macOS:

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew clean buildAll
```

Artifacts:

- `fabric/build/libs/thunderhead-fabric-1.0.0.jar`
- `neoforge/build/libs/thunderhead-neoforge-1.0.0.jar`

`buildAll` runs the shared unit tests and builds and remaps both loader targets.

## Development clients

```powershell
.\gradlew.bat :fabric:runClient
.\gradlew.bat :neoforge:runClient
```

Use separate invocations because each loader owns its run directory and launch configuration.

The NeoForge development run sets `tempestfx.smokeStrike`, which fires one synthetic terrain strike
and one synthetic direct hit shortly after you enter a world. That exercises the whole render path —
channel, shockwave, ripple, particles, decals, distortion — so buffer, shader and render-state
regressions show up during smoke testing instead of during a storm.

Pass `-PtempestfxWorld="New World"` to boot straight into a save, so the smoke strike fires without
any manual interaction:

```powershell
.\gradlew.bat :neoforge:runClient -PtempestfxWorld="New World"
```

Two checks are worth running by hand afterwards, because they exercise the gameplay half rather than
the render half:

```text
/tempestfx summon    a real bolt: vanilla damage and fire, plus near-miss damage from the mod
/tempestfx ball      a real ball lightning entity, which is server-driven and can hurt you
```

`/tempestfx strike` and `/tempestfx directhit` deliberately do neither: they draw and nothing else.

For repeatable showcase capture, a fixed seed preserves the generated channel between takes:

```text
/tempestfx strike-camera 20 --seed 12345
/tempestfx strike 120 72 -35 --seed 12345
/tempestfx camera cinematic
/tempestfx camera speed 0.06
/tempestfx camera off
```

The camera preset uses ordinary spectator mode and therefore needs command permission. It hides the
HUD, disables view bobbing and enables smooth camera, but deliberately does not change tick speed.

## Bundled assets

Every texture, OGG and GLSL program under `common/src/main/resources/assets` is checked in and
original. The masks are RGBA images whose shape lives in the alpha channel, so the renderer supplies
all colour at draw time; the curl map is a three-channel noise field the shaders read directly. The
audio is thirteen Vorbis clips, including the six components a rolling thunder event is assembled
from. Nothing is downloaded or generated at build time — editing an asset means editing the file and
re-running `buildAll` so both jars pick it up.

Masks are written alongside a `.png.mcmeta` requesting bilinear filtering and clamped edges, which is
what keeps a 128×128 mask smooth when stretched over a small quad.

## Verifying the mixin targets

Loom writes `thunderhead-fabric-refmap.json` into the Fabric jar. Every entry there is a resolved
obfuscated target, so an empty or missing mapping is the fastest signal that a mixin no longer
matches the game:

```bash
unzip -p fabric/build/libs/thunderhead-fabric-1.0.0.jar tempestfx-fabric-refmap.json
```

Four hooks are expected: `ClientLevel#addEntity`, `ClientLevel#getSkyFlashTime`,
`GameRenderer#bobHurt` and `GameRenderer#render` (targeting the internal `renderLevel` call).

The server half uses no mixins at all — it hangs off `EntityJoinLevelEvent` on NeoForge and
`ServerEntityEvents.ENTITY_LOAD` on Fabric.

## Test scope

JUnit covers midpoint resolution and determinism, channel continuity and endpoints, divergent seeds,
fork creation and taper, micro stubs, propagation parameters, the segment budget, LOD thresholds, the
brightness envelope and re-strikes, multi-stroke flash planning and release, replicated seed
derivation, seam-free ring noise, physical sound delay, engine-accurate spatial loudness round-trips,
thunder layering, queue bounds and the shared voice budget, rolling thunder
planning, spatial spread and irregular gaps, the transient-driven camera bank, sky-spanning canopy
generation, particle caps and pool reuse, water-versus-land
emission, entity discharge movement rules, ash imprint lifetime and bounds, ball lightning motion and
output curves, near-miss damage falloff and its non-overlap with vanilla, bloom backend lifecycle,
intensity falloff and config validation for both config files.
