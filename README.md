# Thunderhead 2.0 — Lightning & Thunder

Minecraft **1.21.1**, Java **21**, **Fabric** or **NeoForge**.

Thunderhead replaces lightning with procedural channels, shared return-stroke timing, distributed thunder and optional server-driven storm activity. It uses bounded physical approximations designed for Minecraft, rather than claiming a complete plasma or atmospheric simulation.

## What changes

- **One channel per flash.** A weak leader and a fast return front use the same topology as subsequent impulses. Brightness is integrated over frame intervals instead of relying on a single sample.
- **Four discharge categories:** negative and positive cloud-to-ground, intracloud and intercloud. Cloud events do not create ground-impact particles or damage.
- **Stable realistic geometry.** Lower detail retains vertices from a canonical backbone; endpoints remain fixed and forks attach to retained vertices.
- **Thunder along the channel.** Up to eight spatial sources follow the flash's pulse plan. Arrival uses distance / 343 m/s and the listener's current position. Optional local shelter attenuation selects a muffled profile behind obstacles.
- **Surface illumination.** A private depth snapshot reconstructs visible surfaces and approximate normals. Up to four channel samples illuminate them with bounded screen-space occlusion checks.
- **Server storm cells.** Small charge reservoirs drift, build activity and decay during Minecraft thunderstorms. An optional versioned protocol shares event IDs, seeds, kinds, endpoints and start times, with bounded deduplication and late-arrival handling.
- **Ground conduction.** Additional near-miss damage can depend on connected surface materials and whether the target contacts the ground. A short side-flash approximation is separately opt-in. Vanilla's own damage region is excluded by bounding-box intersection.
- **Accessibility and budgets.** Reduced flashing preserves saved preferences, removes the pulse train, suppresses vanilla sky flashes and limits new visual flashes across the storm. Sound, particles, geometry and network queues have explicit caps.

## Installation

Install exactly one jar for your loader:

- Fabric Loader 0.16.14+ and Fabric API for Minecraft 1.21.1.
- NeoForge 21.1.x; development verification uses 21.1.248.

**Client only:** vanilla server lightning receives local visuals and audio. The mod does not change server gameplay or invent a synchronized server storm.

**Client and server:** optional storm synchronization and server gameplay settings are available. Extra storm-generated ground strikes are **off by default**. Cloud activity does not modify blocks. Operators can enable additional real strikes explicitly.

Ball lightning remains an experimental gameplay feature and is **off for new server configurations**. Existing explicit settings are preserved.

## Shader compatibility

The normal channel renderer uses its own programs and framebuffer, then composites into the finished scene. This preserves the mod's pulse appearance without requiring edits to a shader pack. True vanilla bolts retain their vanilla entity renderer if the mod's custom programs are unavailable under a pack.

Surface lighting is an approximation from the available depth. It cannot recover off-screen occluders, a pack's cloud density, or its reflection and exposure buffers. **Full volumetric cloud lighting and pack-native reflections are not promised.** Transparent water, alternate depth pipelines and other GPUs need combination-specific testing.

An experimental native-material route was tested and removed: Complementary replaced supplied channel brightness/color and introduced depth artifacts. It is not part of the supported 2.0 path.

See [the compatibility matrix](release/compatibility.md) for exact tested combinations and limitations. No finite test matrix proves compatibility with every existing or future shader pack.

## Settings

Use `/tempestfx settings`, ModMenu on Fabric, or NeoForge's config button.

- **Realistic** is selected for new client configs: restrained impacts, a coherent flash and channel-based thunder.
- **Cinematic** preserves enhanced impact rings, stronger decorative effects and audio-driven distant walls. Existing configs keep this presentation unless changed.
- Explicit `/tempestfx roll` previews work in both profiles and appear ahead of the player. Distant bolts must be enabled; reduced flashing suppresses these visual cues.
- Settings mark unavailable effects and explain their profile or dependency requirements in tooltips. See the [mechanism audit](docs/MECHANISM_AUDIT.md).
- Quality presets adjust geometry, particle/effect budgets and surface-lighting cost. They are independent of presentation and accessibility.
- Disable shelter attenuation if an acoustic mod already handles obstruction.

Files are `config/tempestfx.json` and `config/tempestfx-server.json`. `/tempestfx reload` reloads the client config and the integrated server's config. Dedicated-server config is read on server start.

Important server settings:

| Setting | New default | Purpose |
| --- | --- | --- |
| `storm.enabled` | true | Harmless additional cloud activity during thunderstorms |
| `storm.groundStrikes` | false | Opt-in extra real strikes |
| `storm.maxCells` | 8 | Maximum active cells per dimension |
| `storm.flashesPerSecond` | 0.12 | Mature-cell activity before lifecycle modulation |
| `storm.broadcastDistance` | 1024 | Event subscription radius in blocks |
| `nearMiss.physicalConduction` | true | Connected-surface and grounded-target approximation |
| `nearMiss.sideFlash` | false | Optional short-range conductor discharge |
| `nearMiss.igniteSeconds` | 0 | Extra ignition; vanilla fire behavior remains separate |
| `ballLightning.enabled` | false | Experimental plasma-sphere gameplay |

The extra-strike target search examines at most 289 already-loaded columns near its candidate, favoring exposed height and lightning rods. This local search is an approximation; ordinary vanilla targeting and its lightning-rod behavior are unchanged. Damage and ignition remain server decisions. Cosmetic vanilla bolts do not receive additional gameplay damage.

## Commands

```text
/tempestfx settings
/tempestfx reload
/tempestfx strike 40 --seed 12345
/tempestfx type negative_ground 12345
/tempestfx type positive_ground 12345
/tempestfx type intracloud 12345
/tempestfx type intercloud 12345
/tempestfx stress 20
/tempestfx summon
/tempestfx ball
/thunderstorm status
/thunderstorm strike 152 71 197
```

`strike`, `type` and `stress` under `/tempestfx` are visual tests. `summon`, `ball` and `/thunderstorm strike` create real entities; server commands require operator permission. Server strike targets must already be loaded.

## Development

[Build instructions](BUILDING.md), [architecture](ARCHITECTURE.md), [API](API.md), [physical approximations](docs/PHYSICAL_MODEL.md), [implementation/verification journal](docs/IMPLEMENTATION_STATUS.md).

`buildAll` tests the shared implementation and builds both loader jars. Test captures use disposable saves; source media in the neighboring project is not modified. No external shader assets are bundled.

Made by **GestSe**. Code and original bundled assets: MIT. See [asset provenance](common/src/main/resources/assets/tempestfx/ASSET_LICENSE.md).
