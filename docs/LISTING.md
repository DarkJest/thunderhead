# Listing and discoverability

Everything needed to publish Thunderhead and be findable. This is the source of truth for the public
copy: if a store page and this file disagree, the store page is wrong.

## Identity

| | |
| --- | --- |
| Public name | **Thunderhead** |
| Author | **GestSe** |
| Slug | `thunderhead` |
| Search subtitle | Lightning & Thunder Overhaul |
| Hero tagline | Turn every thunderstorm into a spectacle. |
| Mod id | `tempestfx` (unchanged, and deliberately so) |

The two lines do different jobs and both are needed. The **subtitle** rides in the store title and
exists for search. The **tagline** is the first line of the page and exists for the human who just
arrived; it never has to contain a keyword.

The name is the anvil-topped storm cloud that actually makes lightning — one word, easy to say, easy
to spell from hearing it, and it carries the word *thunder*, which is what people type. It was free
on both Modrinth and CurseForge when this was written; **re-check both before you publish**, along
with the `thunderhead` slug.

The mod id, the Java package `dev.tempestfx`, the resource namespace, the shader ids and the config
filenames all stay `tempestfx`. Store identity and technical identity are separate things, and
renaming the id would rewrite every asset path and shader id for zero user-visible gain.

### Names that were rejected

| Name | Why not |
| --- | --- |
| Tempest FX | [Tempestra](https://modrinth.com/mod/tempestra) already exists and is pitched almost word for word the same — "visual overhaul of thunderstorms and lightning… arcing and branching". Competing for the same query as a near-identical name loses. |
| Stormfront | The word is owned in public perception by a neo-Nazi site. Never use it. |
| Arclight | Taken by well-known Forge/Bukkit hybrid server software. |
| Stormlight | Brandon Sanderson's *Stormlight Archive*. |
| Fulgurite | Perfect fit — the glass lightning melts in sand — but nobody searches for it. Keep it as a feature name, not the product name. |

## Titles per surface

Search on both stores matches the title first, so the title carries a keyword. Everywhere else the
name stands alone.

| Surface | Text |
| --- | --- |
| Modrinth / CurseForge title | `Thunderhead — Lightning & Thunder Overhaul` |
| GitHub repo description | `Cinematic lightning and thunder overhaul for Minecraft 1.21.1 (Fabric & NeoForge)` |
| In-game / mod menu | `Thunderhead` |
| Video title | `Minecraft Lightning Looks Insane Now — Thunderhead 1.21.1` |
| Thumbnail | `THUNDERHEAD` over one bolt, nothing else |

## Copy

**Summary** (Modrinth's field is capped at 256 characters; this is 186):

> Cinematic lightning and thunder overhaul: procedural branching bolts, distant storm fronts,
> rolling thunder that lasts up to 15 seconds, shockwaves, ash scars and ball lightning.

The same string is the `description` in `fabric.mod.json` and `neoforge.mods.toml`. Keep the three
in sync.

**First 150 characters of the long description** are what search engines show. They must contain
"lightning", "thunder" and "Minecraft 1.21.1", and they must read as a sentence, not a keyword list:

> Thunderhead rebuilds lightning and thunder in Minecraft 1.21.1. Every strike is a procedurally
> generated branching channel with its own glow, shockwave, sparks and ash — and thunder that rolls
> across the sky for up to fifteen seconds afterwards.

## Keywords

Ranked by intent. A keyword that appears nowhere on the page ranks for nothing, so each tier says
where it has to physically appear.

**Primary — must appear in the title or the summary**

`lightning mod` · `thunder mod` · `lightning overhaul` · `realistic lightning` · `Minecraft 1.21.1`

**Secondary — must appear in the first two paragraphs of the description**

`thunderstorm` · `cinematic` · `storm` · `weather` · `ball lightning` · `rolling thunder` ·
`shockwave` · `procedural` · `visual overhaul` · `Fabric` · `NeoForge`

**Body — anywhere in the description, gallery captions or feature list**

`branching lightning` · `lightning branches` · `screen shake` · `camera shake` · `custom shaders` ·
`GLSL` · `particles` · `sparks` · `ash` · `Lichtenberg` · `scorch` · `sky flash` · `thunder sounds` ·
`speed of sound` · `distant thunder` · `storm front` · `immersive` · `atmosphere` · `Iris` ·
`OptiFine` · `client-side` · `multiplayer`

**Long-tail — the queries that actually convert; work them into headings and captions**

- `minecraft realistic lightning mod 1.21.1`
- `minecraft better lightning mod fabric`
- `minecraft thunder sound mod`
- `minecraft lightning shader effect`
- `minecraft ball lightning mod`
- `minecraft storm atmosphere mod`
- `neoforge lightning overhaul 1.21.1`

**Russian — for VK, YouTube and Russian-language mod sites**

`мод на молнии` · `реалистичные молнии майнкрафт` · `гроза` · `гром` · `шаровая молния` ·
`кинематографичные молнии` · `улучшение молний` · `мод на погоду` · `эффекты молний`

### Where keywords have to live

1. **Title** — one primary keyword, and only one. `Thunderhead — Lightning & Thunder Overhaul`.
2. **Summary** — two or three primary, written as a sentence.
3. **First paragraph** — all primary plus the secondary tier.
4. **Headings** in the description — long-tail phrasing (`Realistic thunder, at the speed of sound`).
5. **Gallery captions** — one long-tail phrase each; captions are indexed and almost everyone leaves
   them empty.
6. **Screenshot filenames** — `thunderhead-branching-lightning-1-21-1.png`, not `2026-08-16.png`.
7. **Jar filename** — `thunderhead-fabric-1.0.0.jar`, not the mod id. This is the line of text every
   user reads in their mods folder.

Do not stuff. Both stores rank engagement over repetition, and a description that reads like a
keyword list costs downloads.

## Categories and tags

**Modrinth** (verified against the live category list; a mod may pick several):

- `Decoration` — it is a visual mod, and this is the closest thing Modrinth has to "cosmetic"
- `Game Mechanics` — near-miss damage and ball lightning contact damage
- `Mobs` — ball lightning is a real entity

Environment: **client and server** — the visual half is client-side and runs on a vanilla server;
the gameplay half needs the mod server-side. Say this explicitly on the page, it is the single most
common question for a mod shaped like this.

Loaders: `Fabric`, `NeoForge`. Versions: `1.21.1`.

**CurseForge**: `Cosmetic`, `Mobs`, `Miscellaneous`. Confirm against the category picker at upload
time — CurseForge changes its list without notice.

## Differentiation

Every one of these is true and none of the neighbours can say it. Lead with them; a page that only
says "better lightning" is indistinguishable from the six mods that already said it.

- **No vanilla particle is used.** Own pooled particle engine and own GLSL core shaders.
- **Thunder is not one clip.** A roll is assembled at runtime from a dozen independent layers with
  their own bearing, delay, pitch and envelope, and lasts 4 to 15 seconds.
- **A storm front, not a flash.** 15 to 100 forked cloud-to-ground channels a second across the
  horizon, placed relative to your render distance.
- **Every bundled asset is original.** Own textures, own audio, own GLSL, own icon — no borrowed
  media anywhere.
- **Multiplayer-correct by construction.** No custom packets; every client derives the same storm
  from replicated data.
- **Vanilla behaviour untouched.** Damage, fire, lightning rods, copper weathering and mob
  conversions are exactly as they were.

## Neighbours

Know who you appear next to, and say what you are that they are not.

| Mod | What it does | Overlap |
| --- | --- | --- |
| [Tempestra](https://modrinth.com/mod/tempestra) | Visual thunderstorm and lightning overhaul, arcing and branching bolts | Direct. Closest competitor by pitch. |
| [ImmersiveThunder](https://modrinth.com/mod/immersivethunder) | Distance-based thunder timing | Audio only; Thunderhead includes this and more |
| [Eternal Thunder](https://modrinth.com/mod/eternal-thunder) | Permanent thunderstorm weather | Weather state, not visuals — pairs well |
| [Thunder's Wrath](https://modrinth.com/mod/thunders-wrath) | Lightning wand item | Item mod, not visual |
| [Thunderstorm's Armor](https://modrinth.com/mod/thunderstorms-armor) | Lightning-attracting armour | Gameplay item mod |

"Pairs well" is worth saying on the page. Compatibility notes bring in the other mod's audience.

## Pre-publish checklist

- [ ] `thunderhead` slug still free on Modrinth and CurseForge
- [ ] Title, summary and `fabric.mod.json` / `neoforge.mods.toml` descriptions all match this file
- [ ] Gallery: at least four screenshots, every one captioned and named with a keyword
- [ ] A short video or GIF above the fold — the effect does not survive a still image
- [ ] Environment (client / server) stated in the first screen of the description
- [ ] Both loader jars attached, correct game version tags
- [ ] License and source link filled in
- [ ] Author credited as **GestSe** on both stores, in the jar metadata and in `LICENSE`
