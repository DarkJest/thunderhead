# Reddit launch copy

Check each community's current self-promotion and media rules immediately before posting. Use native video/GIF where allowed and answer technical questions in comments instead of stuffing them into the title.

## r/feedthebeast titles

1. **I wanted Minecraft thunderstorms to feel powerful, so I rebuilt the lightning, impact and thunder**
2. **Thunderhead turns each lightning strike into a procedural cinematic event**
3. **I spent way too much time making Minecraft lightning feel terrifying**
4. **Light first, thunder later — my lightning overhaul now delays sound by distance**

### Suggested post

I wanted storms to be something you stop and watch, not a white line you miss between frames. Thunderhead generates each channel from a seed, adds multi-stroke branching, shockwaves, sparks, smoke or water spray, transient illumination and custom thunder delayed by distance.

The visual/audio side works client-side; installing it on the server also enables configurable near-miss damage and rare ball lightning. It targets Minecraft 1.21.1 on Fabric and NeoForge, and it does not require an external shader pack.

The clip is a real in-game capture. Shader-pack compatibility is only being claimed for combinations listed as tested on the project page.

## r/Minecraft titles

1. **I wanted thunderstorms to actually feel powerful, so I made this lightning overhaul**
2. **I made Minecraft lightning fill the sky — then delayed the thunder by distance**
3. **A before-and-after of vanilla lightning and the storm effects I've been building**

### Suggested post

This is Thunderhead, a Minecraft 1.21.1 mod built around huge procedural lightning and the few seconds around each impact. The bolt lights the scene, throws a pressure wave and material-specific particles, then the custom thunder arrives based on distance.

No external shader pack is required for the effect shown in the no-shader clip. Fabric and NeoForge builds are included.

## Modding-community titles

1. **Showcase: seeded procedural lightning with repeatable capture commands on Fabric and NeoForge**
2. **Thunderhead 1.0 — procedural channels, impact VFX and distance-delayed thunder**
3. **Rebuilding lightning as a deterministic visual/audio event for Minecraft 1.21.1**

### Technical follow-up comment

The release uses the bolt position and replicated entity id for deterministic visuals. The showcase command can override that with a fixed seed, which makes the same channel repeatable from multiple camera angles. The baseline renderer is additive and does not rewrite chunk light; framebuffer-dependent distortion is limited to the vanilla pipeline.

## Posting order

1. Post the 10–15 second native clip first.
2. Put the project link in the body or first comment according to community rules.
3. Use the before/after as a second post only where repeat promotion is allowed.
4. Never claim “OptiFine compatible” or “works with all shaders.”

