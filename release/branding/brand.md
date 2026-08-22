# Thunderhead — brand direction

## Selected name

**Thunderhead**

**Search subtitle:** Lightning & Thunder Overhaul

**Tagline:** Turn every thunderstorm into a spectacle.

**One-sentence description:** Thunderhead transforms Minecraft thunderstorms into cinematic events with towering procedural lightning, explosive impacts, transient illumination and thunder that travels at the speed of sound.

**Slug:** `thunderhead`

**GitHub repository:** `thunderhead`

Thunderhead is the anvil-topped storm cloud that actually produces lightning: one word, easy to spell from hearing it, and it carries *thunder*, which is the word people type into a mod search. It was free on Modrinth and CurseForge when this was written — re-check before publishing.

The earlier working name, Tempest FX, was dropped because [Tempestra](https://modrinth.com/mod/tempestra) already exists and is pitched in almost the same words, and because "FX" matches no search anyone performs. The technical id stays `tempestfx`: store identity and mod id are separate, and renaming the id would rewrite every asset path and shader id for no user-visible gain.

Full keyword, category and store-copy guidance lives in [`docs/LISTING.md`](../../docs/LISTING.md).

## Alternative names

| Name | Tagline | One-line positioning | Suggested slug/repository |
| --- | --- | --- | --- |
| **Skyshatter** | Make the sky hit back. | Violent, memorable and ideal for a lightning-first showcase. | `skyshatter` |
| **Stormwake** | Feel the storm after it strikes. | Atmospheric and distinctive; emphasizes shockwaves and delayed thunder. | `stormwake` |
| **Voltfall** | Every strike becomes an event. | Compact and energetic, with a strong lightning association. | `voltfall` |
| **Stormscar** | Thunder leaves a mark. | Darker and more physical; fits impact debris, ash and ground effects. | `stormscar` |
| **Fulminant** | Thunderstorms, fully unleashed. | Premium and unusual, but slightly harder to pronounce internationally. | `fulminant` |
| **Thunderwake** | Light first. Thunder follows. | Strong audio identity and a clear reference to sound delay. | `thunderwake` |
| **Thunderclap** | The sky, out loud. | Runner-up: free on both stores and carries the same *thunder* keyword. | `thunderclap` |
| **Fulgurite** | Lightning leaves glass behind. | Perfect fit for the ash scars, but nobody searches for the word. | `fulgurite` |

## Extended description

Thunderhead rebuilds the feeling of a Minecraft thunderstorm around scale, impact and timing. Lightning grows into huge, unique branching channels; the strike floods the scene with cold light, drives a pressure wave across the ground and throws sparks, smoke, debris or water spray into the air. Then the sound arrives: layered custom thunder delayed by real distance. It works without an external shader pack, offers performance presets and reduced-flashing controls, and ships for Fabric and NeoForge on Minecraft 1.21.1.

## Voice

- Lead with the feeling: **Thunderstorms finally feel powerful.**
- Use short, physical verbs: *tears, punches, floods, rolls, crawls*.
- Put algorithms and renderer details below compatibility/configuration.
- Never call transient illumination “real block lighting”; no chunk light is rewritten.
- Never claim a shader pack or optimization mod as compatible until that exact combination is tested.

