# Thunderhead 2.0 architecture

`common` owns algorithms, configuration, effects, rendering, audio, server behavior and the optional wire format. `fabric` and `neoforge` adapt loader lifecycle, entity registration, render hooks and transport. Client classes are not required by the dedicated server.

## Flash lifecycle

Vanilla entity ingestion or StormInbox produces an immutable LightningStrikeFxEvent. FlashSimulation advances old effects before accepting new arrivals at age zero (or their elapsed server age). A FlashTimeline is shared by geometry, contact effects and channel audio. Repeat impulses never generate another tree. Late events skip past contact impulses and seek the existing visual timeline instead of replaying a new flash.

Realistic DischargeGeometryStrategy uses a 128-segment canonical backbone and independently seeded attached forks. LOD decimates retained vertices, preserving endpoints. Cinematic retains the earlier midpoint-displacement style. Pulse times are fractional ticks; CPU contact scheduling rounds to ticks. Rendering integrates exposure between samples. Accessibility takes precedence over profile and API settings.

## Rendering

WorldFxRenderer batches channels, surfaces, particles and optional cinematic effects into a private RGBA16F target. EffectRenderTarget borrows available depth read-only for occlusion, optionally copies it for surface lighting, and detaches it before the foreign pipeline continues. FramebufferEffectCompositor applies the accumulated effect after the level image is produced.

FxStateGuard preserves affected framebuffer, viewport, program, vertex/array buffer, texture/sampler, blend, clear-color, depth, mask and cull state. Failed preparation restores the original target before direct fallback. Actual vanilla lightning entities keep their renderer if generic fallback programs under a pack cannot reliably show custom channels.

Surface lighting reconstructs view-space positions/normals and uses up to four light samples with eight visibility steps. It is a post-tone-map approximation, not access to pack-native albedo, cloud density or reflections. The tested native-material experiment was removed; isolated rendering is the supported path.

## Audio

ChannelAcoustics samples up to eight equal-length portions of the actual trunk. ThunderSystem schedules sources per pulse, recomputes arrival against a moving listener, and uses a local shelter probe only after a voice is admitted. Late network arrivals do not replay sound that already passed the listener. Wave and legacy queues share a 192-entry bound and the existing 18-starts-per-second voice budget.

## Server and network

StormServer maintains at most eight cells per dimension near players. Cells have charge, drift and a finite lifecycle. Cloud events are harmless. Additional ground strikes require an explicit server setting and loaded terrain; ContactSelector inspects at most 17x17 columns and favors height/rods. Scheduled extra real bolts use the same replicated seed as their announced visual event.

StormPacket carries model/wire version 2, event ID, seed, world start tick, dimension, kind, endpoints, intensity and repeat cap. The optional sender checks channel support; no client-to-server gameplay payload exists. Hello/replay messages and a 64-event recent window support new observers. Publication is capped at 16 events per dimension/tick, client pending events at 128, and deduplication histories at 512 entries. Client-only operation continues on vanilla servers without this protocol.

The client briefly holds native spawns when the server protocol is present to let authoritative metadata win. Expiring native entity-ID correlation avoids drawing both packet and entity representations; authoritative packet IDs are deduplicated independently of geometry seeds. Wrong-dimension, far-future and stale packets are rejected. All queues reset on world changes.

## Gameplay

Vanilla retains direct damage, fire, copper effects, conversions and normal targeting. Extra near-miss effects exclude any target bounding box intersecting vanilla's true strike volume. Ground current requires connected sampled surfaces and ground/water contact. Side flash is a separate opt-in approximation with loaded-area and visibility checks. Cosmetic bolts skip extra gameplay. Ball lightning is experimental and disabled in new server configs.

## Validation

Pure tests cover geometry, time integration, lifecycle ordering, late packets, bounded queues, audio arrival, config migration, accessibility and damage approximations. Runtime scenarios cover both loaders, a dedicated server with two clients, shader packs and client-only behavior. See release/compatibility.md and docs/IMPLEMENTATION_STATUS.md for evidence and remaining limits; successful compilation alone is not rendering certification.
