# Thunderhead 2.0 verification matrix

Evidence is from local development runs on Windows 11, Java 21.0.12 and Radeon RX 570 (OpenGL 4.6). Exact releases of render loaders/packs matter. No row means universal compatibility with future versions.

2.0.1 adds a [26-scenario mechanism audit](../docs/MECHANISM_AUDIT.md): ARTShade, Complementary
and pack-disabled NeoForge runs each passed 26/26, with final-frame captures. Both artifacts build;
191 shared tests pass. The broader 2.0 evidence below remains historical unless explicitly updated.

| Configuration | Evidence | Scope / limitations |
| --- | --- | --- |
| Fabric 0.16.14 / API 0.116.15, Minecraft 1.21.1 | Build and integrated-server runtime passed | Server packets, ground/cloud events and client render loaded; no Iris in this run |
| NeoForge 21.1.248, Minecraft 1.21.1 | Build and integrated-server runtime passed | Current storm implementation delivered ground and cloud events |
| NeoForge dedicated server + two NeoForge clients | 26 shared v2 events (41 in earlier pre-v2 run), zero mismatches or duplicate IDs | Clients capped at 30/60 FPS; loopback transport, not WAN latency certification |
| Official vanilla 1.21.1 server + Thunderhead NeoForge client | Eight real vanilla bolts observed with protocol=false | Client-only visuals/audio work; no server-side Thunderhead gameplay |
| Iris 1.8.12 + Sodium 0.6.13 + Complementary Unbound r5.8.1 | Repeated successful captures and storm runs | Isolated channel, surface lighting approximation and real-entity fallback inspected |
| Iris 1.8.12 + ARTShade V0.3.0FIX | Successful final-candidate surface capture, run 1789247679418 | Channel visibility inspected; process saved world and exited normally |
| Other packs/loaders, OptiFine, alternate depth renderers | Not individually tested | Best-effort isolated path; do not advertise as certified |

## Render capabilities

| Capability | Supported behavior |
| --- | --- |
| Channel shape and pulse timing | Mod-owned programs and exposure integration |
| Occlusion | Available scene depth, read-only |
| Surface lighting | Up to four channel samples, depth-reconstructed normals, limited visible-depth occlusion |
| Cloud flash | Approximate local glow / configurable legacy sky flash; no access to pack density |
| Pack-native exposure/reflections | Not guaranteed; unsuccessful native-material experiment removed |
| Missing custom programs under a pack | True lightning entities return to the vanilla renderer; API-only channels are limited |
| Missing/incompatible depth | Direct fallback plus retained legacy lighting state |

Depth alone cannot describe transparent layers, off-screen occluders or every pack's cloud/reflection pipeline. Screenshots prove the inspected behavior, not perfect optical transport.

## Remaining verification limits

- The local network test validates repeated event agreement, not every possible latency/loss/reconnect case; pure tests additionally exercise late/future/stale/deduplicated events.
- No calibrated GPU-only timing or multi-vendor driver matrix is claimed. Recorded timings are CPU submission plus driver waits, with FPS caps and capture overhead noted.
- Server target selection and conduction are bounded game approximations. Additional damaging storm strikes are opt-in; ordinary vanilla targeting is unchanged.
- Test saves and screenshots are kept under ignored run directories. No test world, credentials or third-party shader archive is included in release jars.
