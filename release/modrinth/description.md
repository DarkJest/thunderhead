# Thunderhead

> **Turn every thunderstorm into a spectacle.**

<!-- Upload release/media/gif/tempestfx-main-loop-800.gif and replace the URL. Use the 640 optimized version if required. -->
![A Thunderhead lightning strike]({{MAIN_GIF_URL}})

Thunderhead turns every lightning strike into a short cinematic event: a towering branching channel, a violent flash, a pressure wave, sparks, smoke or water spray — and then thunder arriving from the distance.

## ⚡ Lightning Reimagined

Vanilla lightning is over in a blink. Thunderhead gives every strike scale, shape and weight. Channels are generated from a repeatable seed, so every bolt is unique — and the same beautiful strike can be replayed for capture or testing.

![Procedural branching across the sky]({{BRANCHING_IMAGE_URL}})

## ✨ Features

- **Procedural lightning** — unique sky-spanning channels with thick trunks, tapering forks and fine branches.
- **Five kinds of discharge** — negative and positive cloud-to-ground, cloud-to-cloud, intracloud and the rare megaflash, each with its own shape, timing, colour and thunder.
- **Positive superbolts** — rare, wider, violet and almost unbranched; a different event rather than a brighter one.
- **A storm alive between strikes** — horizontal channels travelling through the cloud deck, intracloud pulses that light the cloud from within, and clouds that glow around a discharge.
- **Red sprites and blue jets** — the real phenomena high above severe storms, built from their actual morphology and rare enough to be worth recording.
- **Multi-stroke flashes** — the channel can fire again along a related path instead of disappearing after one frame.
- **Cinematic impacts** — pressure rings, impact bursts, sparks, debris, smoke, ash and atmospheric haze.
- **Water-specific effects** — water spray, steam and a surface ripple replace dry impact debris.
- **Transient illumination** — the impact and sky flash light the scene without rewriting chunk lighting.
- **Thunder with distance** — custom layered thunder is delayed by `distance / 343 m/s`.
- **Storm-scale audio** — close cracks, heavy booms, distant rumbles and rare rolling thunder events.
- **Environmental reactions** — moving nearby entities can carry short-lived electrical arcs.
- **No external shaders required** — the baseline effect renders in the vanilla pipeline.
- **Performance controls** — Low, Medium, High and Ultra presets, particle caps, LOD and effect limits.
- **Accessibility controls** — reduced flashing, independent screen-flash and camera-impulse toggles.
- **Fabric + NeoForge** — one shared feature set for Minecraft 1.21.1.

## 💥 Lightning Has Weight

![Close impact with sparks, smoke and shockwave]({{IMPACT_GIF_URL}})

The strike does not just draw a bright line. It punches through the scene: the ground wave expands, the impact throws material into the air, and the camera receives a short pressure impulse.

## 🌊 Water Reacts Differently

![Lightning striking water]({{WATER_GIF_URL}})

Water impacts emit spray and steam with a surface ripple. Dry terrain uses dust, debris and ash instead.

## 🔊 Light First. Thunder Follows.

![A distant strike followed by delayed thunder]({{THUNDER_DELAY_GIF_URL}})

At range, you see the strike before you hear it. Thunderhead schedules its thunder from real distance, then builds the sound from custom cracks, booms, rolls and tails.

## 🎮 Client and Server

| Installation | What you get |
| --- | --- |
| **Client on any server** | Procedural bolts, flashes, shockwaves, particles, transient illumination, custom thunder and camera effects. |
| **Client + server** | Everything above, plus configurable near-miss damage and rare server-driven ball lightning. |

Thunderhead does not replace vanilla lightning gameplay inside the original strike box. The optional server features extend the event outside it and can be disabled in `config/tempestfx-server.json`.

## ⚙️ Requirements

- Minecraft **1.21.1**
- Java **21**
- **Fabric Loader 0.16.14+ and Fabric API**, or **NeoForge 21.1+**

## 🌈 Rendering and shader packs

External shader packs are **not required**, and they are also **not a problem**. Thunderhead compiles its own GLSL programs, draws the effect into a framebuffer of its own and applies it once the frame is finished, so a bolt looks the same with a shader pack as without one. Occlusion by terrain, water and entities comes from the frame's own depth buffer, so it stays correct in every pipeline. If a program will not compile or the composite point cannot be reached, the mod falls back to drawing straight into the scene and says so in the log.

**Release validation status:** Fabric and NeoForge builds are automated and verified. Manually verified with Iris 1.8.12 under Complementary Unbound r5.8.1 and ARTShade V0.3.0FIX. OptiFine is untested — list it as tested only after a manual run.

## ♿ Flash safety

`general.reducedFlashing` removes channel flicker and return strokes, caps exposure and camera impulse, and disables the extended sky flash. Minecraft's **Hide Lightning Flashes** option is respected.

## 📦 Configuration

Client visuals, sound and performance live in `config/tempestfx.json`. Optional server gameplay lives separately in `config/tempestfx-server.json`.

Choose a preset, tune individual effects, or turn screen flashes and camera movement off completely.

---

**Thunderstorms finally feel powerful.**

## For mod developers

Thunderhead has a small client-side API: raise your own lightning, decide where it lands and at what
angle, style it, choose its thunder and its debris, or listen for every strike the mod draws. It
degrades to a no-op when the mod is absent.

**[Full API reference](https://github.com/DarkJest/thunderhead/blob/main/API.md)** · **[Issues](https://github.com/DarkJest/thunderhead/issues)**
