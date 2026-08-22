# Showcase video — 55-second production script

## Edit timeline

### 0:00–0:03 — Cold open

Dark forest, rain, no logo. At 0:00.4 a huge strike lands 18–22 blocks away. Keep the flash, shockwave and first crack at full speed.

On-screen text: none.

### 0:03–0:07 — Replay the hit

Second angle of seed `12345`, slowed to 45–55%. Show branching, tree illumination, sparks and the expanding ring.

Text: **LIGHTNING. REIMAGINED.**

### 0:07–0:12 — Scale

Wide plains shot. One full channel, then two distant storm-front flashes over the horizon.

Text: **PROCEDURALLY GENERATED**

### 0:12–0:17 — Close impact

Low camera, 10–16 blocks out. Cut on the flash; hold long enough to read the sparks, debris and smoke.

Text: **FEEL THE IMPACT**

### 0:17–0:22 — Water

Strike open water at a shallow angle. Let spray, steam and ripple expand.

Text: **WATER REACTS DIFFERENTLY**

### 0:22–0:28 — Forest pressure wave

Side-on forest view. Track gently as the pressure ring crosses the clearing and particles lift.

Text: **SHOCKWAVES • SPARKS • SMOKE**

### 0:28–0:35 — Sound delay

Distant mountain strike. After the light, hold the same frame in rain and silence until thunder arrives. Do not fill the wait with music.

Text: **LIGHT FIRST. THUNDER FOLLOWS.**

### 0:35–0:41 — Shader beauty pass

Two 3-second shots with the exact tested Iris + shader-pack combination. Keep the pack/version in the description, not over the image.

Text: **SHADER-AWARE** only after successful validation.

### 0:41–0:47 — No-shader proof

Match-cut to the same seed and angle without an external shader pack. Give it at least three seconds.

Text: **NO EXTERNAL SHADERS REQUIRED**

### 0:47–0:52 — Fast proof stack

Four 1.25-second cuts:

1. **FABRIC**
2. **NEOFORGE**
3. **4 PERFORMANCE PRESETS**
4. **REDUCED FLASHING**

Do not show “Iris compatible” or “OptiFine compatible” unless the compatibility matrix has been updated from a real test.

### 0:52–0:55 — Final strike and brand

Mountain or forest hero, largest clean bolt of the set. Cut to dark after the thunder transient.

Title: **TEMPEST FX**

Tagline: **Turn every thunderstorm into a spectacle.**

## Capture order

Record by location, not timeline: Forest angles first, then Plains, Water, Village, Mountain, shader/no-shader match pair. This minimizes setup changes. Capture every seeded strike from at least two angles before changing scene.

## OBS Studio settings

### Video

- Base canvas and output: **2560×1440**; use **3840×2160** only if a 60-second stress capture stays at 60 FPS.
- FPS: **60**.
- Downscale filter: Lanczos only when base and output differ.
- Game Capture source, not Display Capture, whenever the renderer hooks correctly.
- Disable the OBS preview while recording if GPU headroom is tight.

### Recording

- Format: **MKV**, then use OBS **Remux Recordings** to make MP4.
- Encoder preference: hardware **AV1**, then HEVC, then H.264.
- Rate control: **CQP/CQ 14–18** for a high-quality master. If constant bitrate is required, target **80–120 Mbps at 1440p60** or **120–180 Mbps at 4K60**.
- Keyframe interval: 2 seconds.
- Preset: Quality/Slow enough to preserve GPU headroom; avoid the heaviest preset if Minecraft frame time rises.
- B-frames: 2 where supported.

### Color and audio

- Color format: NV12; color space Rec.709; range Limited unless the entire edit pipeline is configured for Full.
- Sample rate: 48 kHz.
- Record game audio and microphone/music on separate tracks; leave microphone off for clean showcase takes.
- Use 320 kbps AAC for the delivery track and keep a lossless/high-quality master if available.
- Make a test strike and keep the loudest thunder peak below 0 dBFS; aim around -6 to -3 dBFS peak.

## Minecraft capture setup

1. Enter the dedicated showcase world and apply the locked night/thunder gamerules.
2. Set 45–60° FOV; avoid Quake Pro or heavy lens distortion.
3. Use maximum render distance that holds 60 FPS, High particles and High Thunderhead preset.
4. Run `/tempestfx camera cinematic` and `/tempestfx camera speed 0.06`.
5. Move in spectator with long, slow inputs. Use 2–4 second handles before and after every action.
6. Trigger seeded strikes only after the camera has settled.
7. Run `/tempestfx camera off` when finished; the previous bobbing, HUD and game mode are restored.

The capture preset does not change tick speed. Record replays at 60 FPS and slow selected clips to 40–60% in the editor. For the cleanest slow motion, optical flow may be used only when it does not warp branches or particles; otherwise use frame blending or a shorter slowdown.

## Editing instructions

1. Build the cold open first. If the first strike is not exceptional, replace it before editing anything else.
2. Cut on flashes and thunder transients; use mostly hard cuts. Avoid generic glitch transitions.
3. Keep text to 2–5 words, large enough for a phone screen, and on screen for at least 1.2 seconds.
4. Keep real delayed-thunder silence at 0:28–0:35. Music should duck 8–12 dB around major cracks/booms.
5. Apply one restrained grade across all clips: deep navy blacks, neutral whites, slight cold-blue shadows. Never recolor the bolt core purple.
6. Protect highlights with scopes; the halo may bloom, but the core and major branches must remain separable.
7. Add a subtle 2.39:1 letterbox only if every platform version uses it; do not letterbox vertical shorts.
8. Export a clean master in 1440p60/4K60, then platform encodes from that master.
9. Watch once muted: visuals and text must still explain the mod. Watch once eyes closed: thunder timing and impact must still feel intentional.

## Main GIF (3–6 seconds)

Use a 4-second loop: 0.0–0.45 darkness/rain, 0.45 strike, 0.55 shockwave, 0.8–2.8 smoke/decay, 2.8–4.0 dark recovery. Start and end on nearly identical dark frames.

Export a master MP4 first. For GIF, target 960–1280 px wide, 24–30 FPS, adaptive palette and only enough colors to preserve the blue-white gradient. If the file is too large, reduce dimensions before reducing colors; never destroy the bolt edge. Prefer an autoplaying WebM/MP4 where the platform supports it.

