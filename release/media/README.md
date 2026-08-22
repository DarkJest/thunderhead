# Thunderhead media manifest

Selected and prepared from the material supplied on 2026-08-16. Original files outside the repository were left unchanged. Media filenames retain the technical `tempestfx` id used by the mod and build artifacts.

## Ready assets

| File | Role | Status | Notes |
| --- | --- | --- | --- |
| `gallery/01-hero-branching.png` | Provisional hero / procedural branching | **Use now** | Strongest available bolt and clearest branching. The dusk sky and foreground fire make it less suitable than the planned night-storm hero, so replace it when that shot exists. |
| `gallery/02-close-impact-smoke.png` | Close impact | **Use now** | Best available frame for illuminated clouds, impact smoke and scale. |
| `gallery/03-night-illumination-before.png` | Illumination “before” | **Use with 04** | Clean dark frame, same composition as the flash frame. |
| `gallery/04-night-illumination-after.png` | Illumination “after” | **Use with 03** | Best available whole-scene flash frame. |
| `gallery/05-night-bolt.png` | Dark night strike | **Secondary gallery** | Atmospheric, but the channel is too thin/faint for the first gallery position. |
| `gallery/06-night-illumination-comparison.png` | Before/after comparison | **Use now** | Derived 50/50 split from 03 and 04 without independent exposure changes. |
| `video/tempestfx-main-loop-1080p60.mp4` | Autoplay/video loop | **Use where MP4 is supported** | 3.78 s, 1920×1080, approximately 60 FPS, no HUD/chat, deliberately silent. |
| `gif/tempestfx-main-loop-800.gif` | High-quality storefront GIF | **Use when size allows** | 3.83 s, 800×450, 15 FPS, 128 colors, 10.6 MB. |
| `gif/tempestfx-main-loop-640-optimized.gif` | Lightweight storefront GIF | **Preferred upload fallback** | 3.83 s, 640×360, 12.5 FPS, 96 colors, 5.28 MB. |
| `gif/thunderhead-main-loop-curseforge-480-under-2mb.gif` | CurseForge storefront GIF | **Recommended for CurseForge** | 2.8 s, 480×270, 10 FPS, 64 colors, 1.70 MB; safely below the reported 2 MB limit. |

Both loops use a clean 0.4-second dark prelude from the supplied PNG, followed by the source MP4 from `00:04.70`. This removes the command chat visible immediately before the strike while preserving darkness → flash → impact → smoke.

## Branding assets

| File | Use |
| --- | --- |
| `branding/thunderhead-project-icon-512.png` | **Recommended CurseForge project logo.** Square PNG, crisp at thumbnail size and visually consistent with Minecraft. |
| `branding/thunderhead-project-icon-source.png` | Original 1254×1254 source for future exports. |
| `branding/thunderhead-project-icon-64-preview.png` | Small-size readability check; do not upload instead of the 512 file. |
| `branding/thunderhead-lightning-brand-art.png` | Alternative cinematic brand art for social posts or a future cover composition. |

The embedded JAR icon was not replaced automatically; these files are release assets only.

## Source material not copied

| Original | Reason |
| --- | --- |
| `C:\Users\Max\Videos\0816\0816.mp4` | 56.77 s, 1920×1080 at 60 FPS, 103 MB. Keep as the local editing master; copying it would unnecessarily bloat normal Git history. |
| `C:\Users\Max\Videos\0816\0816.gif` | 56.8 s, 1138×640 at 10 FPS, 328 MB. Too long and too large for a storefront hook; replaced by the optimized loops above. |
| Supplied image 1 | Near-duplicate dark strike; weaker than `05-night-bolt.png`. |
| Supplied image 3 | Near-duplicate illumination frame; weaker composition than image 5. |

If large source masters must be versioned, use Git LFS or attach them to a private release/archive instead of committing them to ordinary Git history.

## Upload mapping

- `{{MAIN_GIF_URL}}` on CurseForge → upload `gif/thunderhead-main-loop-curseforge-480-under-2mb.gif`.
- `{{MAIN_GIF_URL}}` elsewhere → use `gif/tempestfx-main-loop-800.gif`, or the 640 version if required.
- `{{BRANCHING_IMAGE_URL}}` → `gallery/01-hero-branching.png`
- `{{IMPACT_IMAGE_URL}}` → `gallery/02-close-impact-smoke.png`
- `{{ILLUMINATION_IMAGE_URL}}` → `gallery/06-night-illumination-comparison.png`
- `{{NIGHT_BOLT_IMAGE_URL}}` → `gallery/05-night-bolt.png`

Still missing from the planned release gallery:

- clean water-impact capture without chat;
- controlled shader/no-shader matched pair;
- plains shot showing the entire channel against an uncluttered horizon;
- night hero without foreground fire;
- delayed-thunder clip with clean audio.

Do not label any supplied image “Iris”, “shader” or “no shader” until the renderer state used during capture is confirmed.
